package com.chinajey.dwork.modules.purchaseReturn.service;

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


/**
 * @author erton.bi
 */
@Service
public class ExternalPurchaseReturnApplicationService {

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    @Resource
    private CodeGenerator codeGenerator;


    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(JSONObject jsonObject) {
        //校验基础数据
        checkSaveOrUpdate(jsonObject);
        //查询单据
        Map<String, Object> params = new HashMap<>(2);
        params.put("externalDocumentCode", jsonObject.getString("externalDocumentCode"));
        params.put("sourceSystem", jsonObject.getString("sourceSystem"));
        BmfObject purchaseReturnApplication = this.businessUtils.findOrder(params, "purchaseReturnApplication");
        if (purchaseReturnApplication == null) {
            save(jsonObject);
        }else {
            update(jsonObject, purchaseReturnApplication);
        }
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
        //外部单据类型
        jsonObject.putIfAbsent("externalDocumentType", "purchaseReturnApplication");
        //来源系统
        jsonObject.putIfAbsent("sourceSystem", jsonObject.getString("sourceSystem"));
        jsonObject.computeIfAbsent("returnDate", k -> new Date());
        jsonObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());

        String providerCode = jsonObject.getString("providerCode");
        if (StringUtils.isNotBlank(providerCode)) {
            Map<String, Object> params = new HashMap<>(2);
            params.put("code", providerCode);
            params.put("type", "supplier");
            BmfObject businessPartner = bmfService.findOne("businessPartner", params);
            if (businessPartner == null) {
                throw new BusinessException("供应商主数据[" + providerCode + "]不存在");
            }
            jsonObject.put("providerName", businessPartner.getString("name"));
        }

        //校验子表
        JSONArray jsonArray = jsonObject.getJSONArray("purchaseReturnApplicationIdAutoMapping");
        if (CollectionUtil.isEmpty(jsonArray)) {
            throw new BusinessException("采购退货申请单明细[purchaseReturnApplicationIdAutoMapping]不能为空");
        }
        List<JSONObject> details = jsonArray.toJavaList(JSONObject.class);
        List<String> lineNums = new ArrayList<>();
        for (JSONObject detail : details) {
            //行号不能为空且不能重复
            String lineNum = detail.getString("lineNum");
            if (StringUtils.isBlank(lineNum)) {
                throw new BusinessException("明细行号[lineNum]不能为空");
            }
            if (lineNums.contains(lineNum)) {
                throw new BusinessException("明细行号重复");
            }
            lineNums.add(lineNum);

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

            //单位名称
            String unitName = detail.getString("unitName");
            if (StringUtils.isBlank(unitName)) {
            throw new BusinessException("明细单位[unitName]不能为空");
            }
            BmfObject unit = bmfService.findByUnique(BmfClassNameConst.MEASUREMENT_UNIT, "name", unitName);
            if (unit == null) {
            throw new BusinessException("明细单位[" + unitName + "]不存在");
            }
            detail.put("unit", unit);

            //校验仓库信息
            String sourceWarehouseCode = detail.getString("sourceWarehouseCode");
            if (StringUtils.isBlank(sourceWarehouseCode)) {
                throw new BusinessException("明细仓库编码[sourceWarehouseCode]不能为空");
            }
            BmfObject sourceWarehouse = bmfService.findByUnique(BmfClassNameConst.WAREHOUSE, "code", sourceWarehouseCode);
            if (sourceWarehouse == null) {
                throw new BusinessException("明细仓库主数据[" + sourceWarehouseCode + "]不存在");
            }
            detail.put("sourceWarehouseName", sourceWarehouse.getString("name"));

            BigDecimal planReturnQuantity = ValueUtil.toBigDecimal(detail.getString("planReturnQuantity"));
            if (planReturnQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("明细计划退货数量[planReturnQuantity]不能为空或小于等于0");
            }
            //待退货数量
            detail.put("waitReturnQuantity",planReturnQuantity);
            //已退货数量
            detail.put("returnedQuantity",BigDecimal.ZERO);
        }
    }

    private void save(JSONObject jsonObject) {
        //移除主表id
        jsonObject.remove("id");
        //移除子表的ID
        jsonObject.getJSONArray("purchaseReturnApplicationIdAutoMapping").toJavaList(JSONObject.class).forEach(item -> item.remove("id"));
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(jsonObject, "purchaseReturnApplication");
        bmfObject = this.codeGenerator.setCode(bmfObject);
        if (StringUtils.isBlank(bmfObject.getString("code"))) {
            throw new BusinessException("编码生成失败");
        }
        this.bmfService.saveOrUpdate(bmfObject);
    }

    private void update(JSONObject jsonObject, BmfObject purchaseReturnApplication) {
        if (!DocumentStatusEnum.whetherUpdate(jsonObject.getString("documentStatus"))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(jsonObject.getString("documentStatus")).getName() + "],不能更新");
        }
        jsonObject.put("id", purchaseReturnApplication.getPrimaryKeyValue());
        jsonObject.put("code", purchaseReturnApplication.getString("code"));
        List<BmfObject> oldDetails = purchaseReturnApplication.getAndRefreshList("purchaseReturnApplicationIdAutoMapping");
        List<JSONObject> newDetails = jsonObject.getJSONArray("purchaseReturnApplicationIdAutoMapping").toJavaList(JSONObject.class);
        //更新行操作
        updateDetails(oldDetails, newDetails);

        jsonObject.put("purchaseReturnApplicationIdAutoMapping", newDetails);
        BmfObject newBmfObject = BmfUtils.genericFromJson(jsonObject, "purchaseReturnApplication");
        bmfService.saveOrUpdate(newBmfObject);
    }

    private void updateDetails(List<BmfObject> oldDetails, List<JSONObject> newDetails) {
        //记录旧数据行号+物料编号
        Map<String, BmfObject> oldValueMap = oldDetails.stream().collect(Collectors.toMap(item -> item.getString("lineNum") + item.getString("materialCode"), bmfObject -> bmfObject));
        for (JSONObject item : newDetails) {
            String unValue = item.getString("lineNum") + item.getString("materialCode");
            if (oldValueMap.containsKey(unValue)) {
                JSONObject oldItem = oldValueMap.get(unValue);
                item.put("id", oldItem.getLong("id"));
                //老的已退货数量
                BigDecimal returnedQuantity = oldItem.getBigDecimal("returnedQuantity");
                if (item.getBigDecimal("planReturnQuantity").compareTo(returnedQuantity) < 0) {
                    throw new BusinessException("更新失败-物料名称:"
                            + item.getString("materialName")
                            + ",行号:" + item.getString("lineNum")
                            + ",的采购退货任务修改后计划退货数量小于已退货数量,不允许更新");
                }
                //更新数量 已退货数量
                item.put("returnedQuantity", returnedQuantity);
                //待退货数量 = 计划退货数量 - 已退货数量
                item.put("waitReturnQuantity", BigDecimalUtils.subtractResultMoreThanZero(item.getBigDecimal("planReturnQuantity"), returnedQuantity));
                //更新后移除
                oldValueMap.remove(unValue);
            }
        }
        //删除行操作
        for (JSONObject remove : oldValueMap.values()) {
            BigDecimal returnedQuantity = ValueUtil.toBigDecimal(remove.getBigDecimal("returnedQuantity"));
            if (returnedQuantity.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("更新失败-物料名称:"
                        + remove.getString("materialName")
                        + ",行号:" + remove.getString("lineNum")
                        + ",的采购退货任务存在已退货数量,不允许删除");
            }
            //删除数据
            bmfService.delete(BmfUtils.genericFromJson(remove, "purchaseReturnApplicationDetail"));
        }
    }
}