package com.chinajey.dwork.modules.salesReturn.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.common.utils.CodeGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExternalSalesReturnApplicationService {

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    @Resource
    private CodeGenerator codeGenerator;

    @Resource
    private SalesReturnApplicationService salesReturnApplicationService;

    private static final String SALES_RETURN_APPLICANT = "salesReturnApplicant";


    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(JSONObject jsonObject) {
        //校验基础数据
        checkSaveOrUpdate(jsonObject);
        //查询单据
        Map<String, Object> params = new HashMap<>(2);
        params.put("externalDocumentCode", jsonObject.getString("externalDocumentCode"));
        params.put("sourceSystem", jsonObject.getString("sourceSystem"));
        BmfObject purchaseReturnApplication = this.businessUtils.findOrder(params, SALES_RETURN_APPLICANT);
        if (purchaseReturnApplication == null) {
            save(jsonObject);
        } else {
            update(jsonObject, purchaseReturnApplication);
        }
    }

    private void save(JSONObject jsonObject) {
        //移除主表id
        jsonObject.remove("id");
        handleSaveDetail(jsonObject);
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(jsonObject, SALES_RETURN_APPLICANT);
        bmfObject = this.codeGenerator.setCode(bmfObject);
        if (StringUtils.isBlank(bmfObject.getString("code"))) {
            throw new BusinessException("编码生成失败");
        }
        if (StringUtils.isEmpty(bmfObject.getString("sourceDocumentType"))) {
            bmfObject.put("sourceDocumentType", SALES_RETURN_APPLICANT);
            bmfObject.put("sourceDocumentCode", bmfObject.getString("code"));
        }
        this.bmfService.saveOrUpdate(bmfObject);
        //下达 先取消再下达
        this.businessUtils.closeCurrentTask("GN10010", "sourceDocumentCode", bmfObject.getString("code"));
        salesReturnApplicationService.issuedSalesReturnApplication(bmfObject);
    }

    private void update(JSONObject jsonObject, BmfObject salesReturnApplication) {
        if (!DocumentStatusEnum.whetherUpdate(salesReturnApplication.getString("documentStatus"))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(salesReturnApplication.getString("documentStatus")).getName() + "],不能更新");
        }
        jsonObject.put("id", salesReturnApplication.getPrimaryKeyValue());
        jsonObject.put("code", salesReturnApplication.getString("code"));
        List<BmfObject> oldDetails = salesReturnApplication.getAndRefreshList("salesReturnApplicantDetailIdAutoMapping");
        List<JSONObject> newDetails = jsonObject.getJSONArray("salesReturnApplicantDetailIdAutoMapping").toJavaList(JSONObject.class);
        //更新行操作
        updateDetails(oldDetails, newDetails);

        jsonObject.put("salesReturnApplicantDetailIdAutoMapping", newDetails);
        BmfObject newBmfObject = BmfUtils.genericFromJson(jsonObject, SALES_RETURN_APPLICANT);
        if (StringUtils.isEmpty(newBmfObject.getString("sourceDocumentType"))) {
            newBmfObject.put("sourceDocumentType", SALES_RETURN_APPLICANT);
            newBmfObject.put("sourceDocumentCode", newBmfObject.getString("code"));
        }
        bmfService.saveOrUpdate(newBmfObject);
        //下达 先取消再下达
        this.businessUtils.closeCurrentTask("GN10010", "sourceDocumentCode", newBmfObject.getString("code"));
        salesReturnApplicationService.issuedSalesReturnApplication(newBmfObject);
    }


    private void checkSaveOrUpdate(JSONObject jsonObject) {
        if (jsonObject == null) {
            throw new BusinessException("参数不能为空");
        }
        String code = jsonObject.getString("code");
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("编码[code]不能为空");
        }
        jsonObject.remove("code");

        //外部单据编码
        jsonObject.put("externalDocumentCode", code);
        String externalDocumentType = jsonObject.getString("externalDocumentType"); //来源单据编码
        if (StringUtils.isBlank(externalDocumentType)) {
            throw new BusinessException("单据类型[externalDocumentType]不能为空");
        }
        jsonObject.remove("code");
        //来源系统
        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            throw new BusinessException("来源系统字段[sourceSystem]不能为空");
        }
        jsonObject.computeIfAbsent("returnDate", k -> new Date());
        jsonObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());

        String providerCode = jsonObject.getString("customerCode");
        if (StringUtils.isNotBlank(providerCode)) {
            Map<String, Object> params = new HashMap<>(2);
            params.put("code", providerCode);
            params.put("type", "customer");
            BmfObject businessPartner = bmfService.findOne("businessPartner", params);
            if (businessPartner == null) {
                throw new BusinessException("客户主数据[" + providerCode + "]不存在");
            }
            jsonObject.put("customerName", businessPartner.getString("name"));
        }

        //校验子表
        JSONArray jsonArray = jsonObject.getJSONArray("salesReturnApplicantDetailIdAutoMapping");
        if (CollectionUtil.isEmpty(jsonArray)) {
            throw new BusinessException("销售退货申请单明细[salesReturnApplicantDetailIdAutoMapping]不能为空");
        }
        List<JSONObject> details = jsonArray.toJavaList(JSONObject.class);
        for (JSONObject detail : details) {
            //物料主数据
            String materialCode = detail.getString("materialCode");
            if (StringUtils.isBlank(materialCode)) {
                throw new BusinessException("明细物料编码[materialCode]不能为空");
            }
            BmfObject material = bmfService.findByUnique(BmfClassNameConst.MATERIAL, "code", materialCode);
            if (material == null) {
                throw new BusinessException("明细物料主数据[" + materialCode + "]不存在");
            }
            detail.put("materialName", material.getString("name"));
            detail.put("specifications", material.getString("specifications"));
            detail.put("unit", material.getAndRefreshBmfObject("flowUnit"));

            BigDecimal returnQuantity = ValueUtil.toBigDecimal(detail.getString("returnQuantity"));
            if (returnQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("明细退货数量[planReturnQuantity]不能为空或小于等于0");
            }

            String warehouseCode = detail.getString("warehouseCode");
            if (StringUtils.isBlank(warehouseCode)) {
                throw new BusinessException("明细仓库编码[warehouseCode]不能为空");
            }
            BmfObject warehouse = bmfService.findByUnique(BmfClassNameConst.WAREHOUSE, "code", warehouseCode);
            if (warehouse == null) {
                throw new BusinessException("明细仓库主数据[" + warehouseCode + "]不存在");
            }
            detail.put("warehouseName", warehouse.getString("name"));

            detail.put("noReceivedQuantity", returnQuantity);
            detail.put("receivedQuantity", BigDecimal.ZERO);
        }
    }

    //保存时，对同物料和同仓库的进行合并
    private void handleSaveDetail(JSONObject jsonObject) {
        List<JSONObject> salesAutoMapping = jsonObject.getJSONArray("salesReturnApplicantDetailIdAutoMapping").toJavaList(JSONObject.class);
        Map<String, List<JSONObject>> salesGroup = salesAutoMapping.stream().collect(Collectors.groupingBy(object -> object.getString("materialCode") + "-" + object.getString("warehouseCode")));
        List<JSONObject> newMapping = new ArrayList<>();
        salesGroup.forEach((key,list) -> {
            if (CollectionUtil.isNotEmpty(list)) {
                JSONObject detail = list.get(0);
                detail.remove("id");
                BigDecimal sumReturnQuantity = list.stream().map(item -> item.getBigDecimal("returnQuantity")).reduce(BigDecimal.ZERO, BigDecimal::add);
                detail.put("returnQuantity", sumReturnQuantity);
                detail.put("noReceivedQuantity", sumReturnQuantity);
                newMapping.add(detail);
            }
        });
        jsonObject.put("salesReturnApplicantDetailIdAutoMapping", newMapping);
    }

    private void updateDetails(List<BmfObject> oldDetails, List<JSONObject> newDetails) {
        //记录旧数据行号+物料编号
        Map<String, BmfObject> oldValueMap = oldDetails.stream().collect(Collectors.toMap(item -> item.getString("warehouseCode") + item.getString("materialCode"), bmfObject -> bmfObject));
        for (JSONObject item : newDetails) {
            String unValue = item.getString("warehouseCode") + item.getString("materialCode");
            if (oldValueMap.containsKey(unValue)) {
                JSONObject oldItem = oldValueMap.get(unValue);
                item.put("id", oldItem.getLong("id"));
                //老的已退货数量
                BigDecimal receivedQuantity = oldItem.getBigDecimal("receivedQuantity");
                if (item.getBigDecimal("returnQuantity").compareTo(receivedQuantity) < 0) {
                    throw new BusinessException("更新失败-物料名称:"
                            + item.getString("materialName")
                            + ",的销售退货任务修改后退货数量小于已接收数量,不允许更新");
                }
                //更新数量 已退货数量
                item.put("receivedQuantity", receivedQuantity);
                //待退货数量 = 计划退货数量 - 已退货数量
                item.put("noReceivedQuantity", BigDecimalUtils.subtractResultMoreThanZero(item.getBigDecimal("returnQuantity"), receivedQuantity));
                //更新后移除
                oldValueMap.remove(unValue);
            }
        }
        //删除行操作
        for (JSONObject remove : oldValueMap.values()) {
            BigDecimal receivedQuantity = ValueUtil.toBigDecimal(remove.getBigDecimal("receivedQuantity"));
            if (receivedQuantity.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("更新失败-物料名称:"
                        + remove.getString("materialName")
                        + ",的销售退货任务存在已接收数量,不允许删除");
            }
            //删除数据
            bmfService.delete(BmfUtils.genericFromJson(remove, "salesReturnApplicantDetail"));
        }
    }


}
