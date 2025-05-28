package com.chinajey.dwork.modules.purchaseOrder.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.enums.DocumentConfirmStatusEnum;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.utils.BmfEnumUtils;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.modules.purchaseOrder.form.PurchaseOrderForm;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExternalPurchaseOrderService {
    @Resource
    private BmfService bmfService;
    @Resource
    private BusinessUtils businessUtils;
    @Resource
    private CodeGenerator codeGenerator;
    @Resource
    private PurchaseOrderService purchaseOrderService;

    private static final String BMF_CLASS = "purchaseOrder";

    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(PurchaseOrderForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        //查询单据
        Map<String, Object> params = new HashMap<>(4);
        params.put("externalDocumentCode", jsonObject.getString("externalDocumentCode"));
        params.put("sourceSystem", jsonObject.getString("sourceSystem"));
        BmfObject purchaseOrder = businessUtils.findOrder(params, BMF_CLASS);
        if (purchaseOrder == null) {
            purchaseOrderService.save(jsonObject);
        } else {
            this.update(jsonObject, purchaseOrder);
        }

    }

    private JSONObject getJsonObject(PurchaseOrderForm form) {
        if (!BmfEnumUtils.validateBmfEnumValue("sourceSystem", form.getSourceSystem())) {
            throw new BusinessException("来源系统值[" + form.getSourceSystem() + "]错误");
        }
        JSONObject jsonObject = new JSONObject();
        BmfObject provider = bmfService.findByUnique("businessPartner", "code", form.getProviderCode());
        if (provider == null) {
            throw new BusinessException("供应商[" + form.getProviderCode() + "]不存在");
        }
        BmfObject buyer = bmfService.findByUnique("user", "code", form.getBuyerCode());
        if (buyer == null) {
            throw new BusinessException("采购员[" +  form.getBuyerCode() + "]不存在");
        }
        jsonObject.put("providerName", provider.getString("name"));
        jsonObject.put("providerCode", provider.getString("code"));
        jsonObject.put("buyerName", buyer.getString("name"));
        jsonObject.put("buyerCode", buyer.getString("code"));
        jsonObject.put("sourceSystem", form.getSourceSystem());
        jsonObject.put("externalDocumentType", BMF_CLASS);
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("remark", form.getRemark());
        // 赋值扩展字段
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        JSONArray details = new JSONArray();
        for (PurchaseOrderForm.Detail d : form.getDetails()) {
            BmfObject material = bmfService.findByUnique("material", "code", d.getMaterialCode());
            if (material == null) {
                throw new BusinessException("物料主数据[" + d.getMaterialCode() + "]不存在");
            }
            BmfObject warehouse = bmfService.findByUnique("warehouse", "code", d.getWarehouseCode());
            if (warehouse == null) {
                throw new BusinessException("仓库主数据[" + d.getWarehouseCode() + "]不存在");
            }
            JSONObject detail = new JSONObject();
            detail.put("lineNum", d.getLineNum());
            detail.put("materialCode", material.getString("code"));
            detail.put("materialName", material.getString("name"));
            detail.put("specifications", material.getString("specifications"));
            detail.put("unit", material.getBmfObject("flowUnit"));
            detail.put("planQuantity", d.getPlanQuantity());
            detail.put("warehouseCode", warehouse.getString("code"));
            detail.put("warehouseName", warehouse.getString("name"));
            JsonUtils.jsonMergeExtFiled(d.getExtFields(), detail);
            details.add(detail);
        }
        jsonObject.put("purchaseOrderDetailIdAutoMapping", details);
        return jsonObject;
    }

//    private void save(JSONObject jsonObject) {
//        //移除主表id
//        jsonObject.remove("id");
//        //移除子表id
//        jsonObject.getJSONArray("purchaseOrderDetailIdAutoMapping").toJavaList(JSONObject.class).forEach(item -> item.remove("id"));
//        BmfObject bmfObject = BmfUtils.genericFromJsonExt(jsonObject, "purchaseOrder");
//        bmfObject = codeGenerator.setCode(bmfObject);
//        if (StringUtils.isBlank(bmfObject.getString("code"))) {
//            throw new BusinessException("编码生成失败");
//        }
//        bmfService.saveOrUpdate(bmfObject);
//    }

    private void update(JSONObject jsonObject, BmfObject purchaseOrder) {
        jsonObject.put("id", purchaseOrder.getPrimaryKeyValue());
        jsonObject.put("code", purchaseOrder.getString("code"));
        List<BmfObject> oldDetails = purchaseOrder.getAndRefreshList("purchaseOrderDetailIdAutoMapping");
        List<JSONObject> newDetails = jsonObject.getJSONArray("purchaseOrderDetailIdAutoMapping").toJavaList(JSONObject.class);
        //更新行操作
        updateDetails(oldDetails, newDetails);
        jsonObject.put("purchaseOrderDetailIdAutoMapping", newDetails);
        BmfObject newBmfObject = BmfUtils.genericFromJson(jsonObject, "purchaseOrder");
        purchaseOrderService.update(newBmfObject,false);
    }

    private void updateDetails(List<BmfObject> oldDetails, List<JSONObject> newDetails) {
        //记录旧数据行号+物料编号
        Map<String, BmfObject> oldValueMap = oldDetails.stream().collect(Collectors.toMap(item -> item.getString("lineNum") + item.getString("materialCode"), bmfObject -> bmfObject));
        for (JSONObject item : newDetails) {
            String unValue = item.getString("lineNum") + item.getString("materialCode");
            if (oldValueMap.containsKey(unValue)) {
                JSONObject oldValue = oldValueMap.get(unValue);
                item.put("id", oldValue.getLong("id"));
                //老的已收货数量
                BigDecimal receivedQuantity = oldValue.getBigDecimal("receivedQuantity");
                if (item.getBigDecimal("planQuantity").compareTo(receivedQuantity) < 0) {
                    throw new BusinessException("更新失败-物料名称:"
                            + item.getString("materialName")
                            + ",行号:" + item.getString("lineNum")
                            + ",采购订单修改后计划数量不能小于已收货数量");
                }
                //老的已入库数量
                BigDecimal warehousedQuantity = oldValue.getBigDecimal("warehousedQuantity");
                if (item.getBigDecimal("planQuantity").compareTo(warehousedQuantity) < 0) {
                    throw new BusinessException("更新失败-物料名称:"
                            + item.getString("materialName")
                            + ",行号:" + item.getString("lineNum")
                            + ",采购订单修改后计划数量不能小于已入库数量");
                }
                //更新newDetails中的数量
                //已收货数量
                item.put("receivedQuantity", receivedQuantity);
                //待收货数量 = 计划数量 - 已收货数量
                item.put("waitReceivedQuantity", BigDecimalUtils.subtractResultMoreThanZero(item.getBigDecimal("planQuantity"), receivedQuantity));
                //已入库数量
                item.put("warehousedQuantity", warehousedQuantity);
                //待入库数量 = 已收货数量 - 已入库数量
                item.put("waitWarehousedQuantity", BigDecimalUtils.subtractResultMoreThanZero(receivedQuantity, warehousedQuantity));
                //退货数量
                item.put("returnQuantity", item.getBigDecimal("returnQuantity"));
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
                        + ",行号:" + remove.getString("lineNum")
                        + ",采购订单存在已收货数量,不允许删除");
            }
            //删除数据
            bmfService.delete(BmfUtils.genericFromJson(remove, "purchaseOrderDetail"));
        }
    }

//    private void check(JSONObject jsonObject) {
//        //校验必填项
//        validateExist(jsonObject);
//        //校验合法性
//        validateLegal(jsonObject);
//        //默认值
//        setDefault(jsonObject);
//    }
//
//    /**
//     * 校验必填项
//     *
//     * @param jsonObject 表单json
//     */
//    private void validateExist(JSONObject jsonObject) {
//        if (jsonObject == null) {
//            throw new BusinessException("参数不能为空");
//        }
//        if (StringUtils.isBlank(jsonObject.getString("code"))) {
//            throw new BusinessException("外部单据编码[code]不能为空");
//        }
//        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
//            throw new BusinessException("来源系统不能为空");
//        }
//    }
//
//    /**
//     * 校验合法性
//     *
//     * @param jsonObject jsonObject
//     */
//    private void validateLegal(JSONObject jsonObject) {
//        //供应商
//        String providerCode = jsonObject.getString("providerCode");
//        if (StringUtils.isBlank(providerCode)) {
//            throw new BusinessException("供应商编码[providerCode]不能为空");
//        }
//        BmfObject provider = bmfService.findByUnique("businessPartner", "code", providerCode);
//        if (provider == null) {
//            throw new BusinessException("未找到供应商主数据" + providerCode);
//        }
//        jsonObject.put("providerName", provider.getString("name"));
//
//        //采购员
//        String buyerCode = jsonObject.getString("buyerCode");
//        if (StringUtils.isBlank(buyerCode)) {
//            throw new BusinessException("采购员编码[buyerCode]不能为空");
//        }
//        BmfObject buyer = bmfService.findByUnique("user", "code", buyerCode);
//        if (buyer == null) {
//            throw new BusinessException("未找到用户主数据" + buyerCode);
//        }
//        jsonObject.put("buyerName", buyer.getString("name"));
//
//        //校验子表
//        JSONArray jsonArray = jsonObject.getJSONArray("purchaseOrderDetailIdAutoMapping");
//        JSONArray purchaseOrderReleaseIdAutoMapping = new JSONArray();
//        if (CollectionUtil.isEmpty(jsonArray)) {
//            throw new BusinessException("采购订单明细不能为空");
//        }
//        List<JSONObject> details = jsonArray.toJavaList(JSONObject.class);
//        List<String> lineNums = new ArrayList<>();
//        for (JSONObject detail : details) {
//            //行号不能为空且不能重复
//            String lineNum = detail.getString("lineNum");
//            if (StringUtils.isBlank(lineNum)) {
//                throw new BusinessException("明细行号[lineNum]不能为空");
//            }
//            if (lineNums.contains(lineNum)) {
//                throw new BusinessException("明细行号重复");
//            }
//            lineNums.add(lineNum);
//
//            //物料主数据不能为空
//            String materialCode = detail.getString("materialCode");
//            if (StringUtils.isBlank(materialCode)) {
//                throw new BusinessException("采购订单明细物料编码[materialCode]不能为空");
//            }
//            BmfObject material = bmfService.findByUnique("material", "code", materialCode);
//            if (material == null) {
//                throw new BusinessException("采购订单明细未找到物料主数据" + materialCode);
//            }
//            detail.put("materialName", material.getString("name"));
//            detail.put("specifications", material.getString("specifications"));
//            detail.put("unit", material.get("flowUnit"));
//
//            //仓库主数据
//            String warehouseCode = detail.getString("warehouseCode");
//            if (StringUtils.isBlank(warehouseCode)) {
//                throw new BusinessException("采购订单明细目标仓库编码[warehouseCode]不能为空");
//            }
//            BmfObject warehouse = bmfService.findByUnique("warehouse", "code", warehouseCode);
//            if (warehouse == null) {
//                throw new BusinessException("采购订单明细未找到仓库主数据" + warehouseCode);
//            }
//            detail.put("warehouseName", warehouse.getString("name"));
//
//            //计划数量
//            BigDecimal planQuantity = ValueUtil.toBigDecimal(detail.getString("planQuantity"));
//            if (planQuantity.compareTo(BigDecimal.ZERO) <= 0) {
//                throw new BusinessException("采购订单明细计划数量不能为空或小于等于0");
//            }
//            //已收货数量
//            detail.put("receivedQuantity", BigDecimal.ZERO);
//            //待收货数量
//            detail.put("waitReceivedQuantity", planQuantity);
//            //已入库数量
//            detail.put("warehousedQuantity", BigDecimal.ZERO);
//            //待入库数量
//            detail.put("waitWarehousedQuantity", BigDecimal.ZERO);
//            //退货数量
//            detail.put("returnQuantity", BigDecimal.ZERO);
//
//            //创建下达明细行
//            JSONObject item = purchaseOrderService.createReleaseDetail(detail);
//            purchaseOrderReleaseIdAutoMapping.add(item);
//        }
//        jsonObject.put("purchaseOrderReleaseIdAutoMapping", purchaseOrderReleaseIdAutoMapping);
//
//    }
//
//    /**
//     * 设置默认值
//     *
//     * @param jsonObject jsonObject
//     */
//    private void setDefault(JSONObject jsonObject) {
//        //外部单据类型-采购订单
//        jsonObject.putIfAbsent("externalDocumentType", "purchaseOrder");
//        //状态-未处理
//        jsonObject.putIfAbsent("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
//        //确认状态-未确认
//        jsonObject.putIfAbsent("confirmStatus", DocumentConfirmStatusEnum.UNCONFIRMED.getCode());
//        //创建日期
//        jsonObject.computeIfAbsent("createDate", k -> new Date());
//    }
}
