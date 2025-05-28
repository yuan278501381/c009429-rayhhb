package com.chinajey.dwork.modules.warehousingApplicant.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BmfEnumUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.modules.warehousingApplicant.form.StoreForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;

@Service
public class ExternalWarehousingApplicantService {

    private static final String BMF_CLASS = "warehousingApplicant";

    @Resource
    private BmfService bmfService;

    @Resource
    private WarehousingApplicantService warehousingApplicantService;

    @Transactional
    public BmfObject saveOrUpdate(StoreForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        String externalDocumentCode = jsonObject.getString("externalDocumentCode");
        BmfObject bmfObject = this.bmfService.findByUnique(BMF_CLASS, "externalDocumentCode", externalDocumentCode);
        if (bmfObject == null) {
            bmfObject = this.warehousingApplicantService.save(jsonObject);
        } else {
            ExtractUtils.commonOrderUpdate(bmfObject, jsonObject,
                    WarehousingApplicantService.DETAIL_ATTR, details -> this.warehousingApplicantService.formatDetailQuantity(details));
        }
        // 同步过来的入库申请单，自动下达
        this.warehousingApplicantService.issued(Collections.singletonList(bmfObject.getPrimaryKeyValue()));
        return bmfObject;
    }

    private JSONObject getJsonObject(StoreForm form) {
        if (!BmfEnumUtils.validateBmfEnumValue("sourceSystem", form.getSourceSystem())) {
            throw new BusinessException("来源系统值[" + form.getSourceSystem() + "]错误");
        }
        if (!BmfEnumUtils.validateBmfEnumValue("orderBusinessType", form.getOrderBusinessType())) {
            throw new BusinessException("业务类型值[" + form.getOrderBusinessType() + "]错误");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("sourceSystem", form.getSourceSystem());
        jsonObject.put("externalDocumentType", BMF_CLASS);
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("orderBusinessType", form.getOrderBusinessType());
        jsonObject.put("remark", form.getRemark());
        // 赋值扩展字段
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        JSONArray details = new JSONArray();
        for (StoreForm.Detail d : form.getDetails()) {
            BmfObject material = this.bmfService.findByUnique("material", "code", d.getMaterialCode());
            if (material == null) {
                throw new BusinessException("物料[" + d.getMaterialCode() + "]不存在");
            }
            BmfObject warehouse = this.bmfService.findByUnique("warehouse", "code", d.getTargetWarehouseCode());
            if (warehouse == null) {
                throw new BusinessException("仓库[" + d.getTargetWarehouseCode() + "]不存在");
            }
            String externalDocumentType = d.getExternalDocumentType();
            String externalDocumentCode = d.getExternalDocumentCode();
            if (StringUtils.isNotBlank(externalDocumentType)) {
                if (!BmfEnumUtils.validateBmfEnumValue("documentType", externalDocumentType)) {
                    throw new BusinessException("外部单据类型值[" + externalDocumentType + "]错误");
                }
            }
            JSONObject detail = new JSONObject();
            detail.put("lineNum", d.getLineNum());
            detail.put("materialCode", material.getString("code"));
            detail.put("materialName", material.getString("name"));
            detail.put("specifications", material.getString("specifications"));
            detail.put("planQuantity", d.getPlanQuantity());
            detail.put("warehousingQuantity", BigDecimal.ZERO);
            detail.put("waitQuantity", d.getPlanQuantity());
            detail.put("unit", material.getBmfObject("flowUnit"));
            detail.put("targetWarehouseCode", warehouse.getString("code"));
            detail.put("targetWarehouseName", warehouse.getString("name"));
            detail.put("externalDocumentType", externalDocumentType);
            detail.put("externalDocumentCode", externalDocumentCode);
            JsonUtils.jsonMergeExtFiled(d.getExtFields(), detail);
            details.add(detail);
        }
        jsonObject.put("warehousingApplicantIdAutoMapping", details);
        return jsonObject;
    }
}
