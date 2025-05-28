package com.chinajey.dwork.modules.salesDelivery.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BmfEnumUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.modules.salesDelivery.form.DeliveryPlanForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
public class ExternalSalesDeliveryPlanService {

    private static final String BMF_CLASS = "salesDeliveryPlan";

    @Resource
    private BmfService bmfService;

    @Resource
    private SalesDeliveryPlanService salesDeliveryPlanService;

    @Transactional
    public BmfObject saveOrUpdate(DeliveryPlanForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        String externalDocumentCode = jsonObject.getString("externalDocumentCode");
        if (StringUtils.isBlank(jsonObject.getString("externalDocumentCode"))) {
            throw new BusinessException("来源外部单据编码不能为空");
        }
        BmfObject bmfObject = this.bmfService.findByUnique(BMF_CLASS, "externalDocumentCode", externalDocumentCode);
        if (bmfObject == null) {
            bmfObject = this.salesDeliveryPlanService.create(jsonObject);
            // 自动下达
            this.salesDeliveryPlanService.issued(Collections.singletonList(bmfObject.getPrimaryKeyValue()));
            return bmfObject;
        }
        return ExtractUtils.commonOrderUpdate(bmfObject, jsonObject,
                SalesDeliveryPlanService.DETAIL_ATTR, details -> this.salesDeliveryPlanService.formatDetailQuantity(details));
    }

    private JSONObject getJsonObject(DeliveryPlanForm form) {
        if (!BmfEnumUtils.validateBmfEnumValue("sourceSystem", form.getSourceSystem())) {
            throw new BusinessException("来源系统值[" + form.getSourceSystem() + "]错误");
        }
        BmfObject customer = this.bmfService.findByUnique("businessPartner", "code", form.getCustomerCode());
        if (customer == null) {
            throw new BusinessException("客户[" + form.getCustomerCode() + "]不存在");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("sourceSystem", form.getSourceSystem());
        jsonObject.put("externalDocumentType", "salesDelivery");
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("orderDate", form.getOrderDate());
        jsonObject.put("deliveryDate", form.getDeliveryDate());
        jsonObject.put("remark", form.getRemark());
        jsonObject.put("customerCode", customer.getString("code"));
        jsonObject.put("customerName", customer.getString("name"));
        // 赋值扩展字段
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        JSONArray details = new JSONArray();
        Set<String> lineNumSet = new HashSet<>();
        for (DeliveryPlanForm.Detail d : form.getDetails()) {
            if (!lineNumSet.add(d.getLineNum())) {
                throw new BusinessException("外部行号[" + d.getLineNum() + "]重复");
            }
            BmfObject material = this.bmfService.findByUnique("material", "code", d.getMaterialCode());
            if (material == null) {
                throw new BusinessException("物料[" + d.getMaterialCode() + "]不存在");
            }
            BmfObject warehouse = this.bmfService.findByUnique("warehouse", "code", d.getWarehouseCode());
            if (warehouse == null) {
                throw new BusinessException("仓库[" + d.getWarehouseCode() + "]不存在");
            }
            JSONObject detail = new JSONObject();
            detail.put("lineNum", d.getLineNum());
            detail.put("materialCode", material.getString("code"));
            detail.put("materialName", material.getString("name"));
            detail.put("specifications", material.getString("specifications"));
            detail.put("quantity", d.getQuantity());
            detail.put("unit", material.getBmfObject("flowUnit"));
            detail.put("outboundQuantity", BigDecimal.ZERO);
            detail.put("waitOutboundQuantity", d.getQuantity());
            detail.put("shippedQuantity", BigDecimal.ZERO);
            detail.put("waitShippedQuantity", d.getQuantity());
            detail.put("sourceWarehouseCode", warehouse.getString("code"));
            detail.put("sourceWarehouseName", warehouse.getString("name"));
            JsonUtils.jsonMergeExtFiled(d.getExtFields(), detail);
            details.add(detail);
        }
        jsonObject.put("salesDeliveryPlanDetailIdAutoMapping", details);
        return jsonObject;
    }
}
