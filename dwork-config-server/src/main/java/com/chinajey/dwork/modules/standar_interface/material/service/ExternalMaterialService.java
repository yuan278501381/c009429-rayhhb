package com.chinajey.dwork.modules.standar_interface.material.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.common.utils.ObjectRelationUtils;
import com.chinajey.dwork.modules.standar_interface.material.form.ExternalMaterialForm;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.utils.BeanConvertUtils;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;


@Service
public class ExternalMaterialService {
    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    public static final String BMF_CLASS = "material";

    @Transactional
    public BmfObject saveOrUpdate(ExternalMaterialForm materialForm) {
        checkUnique(materialForm);
        JSONObject jsonObject = getJsonObject(materialForm);
        BmfObject bmfMaterial = ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject);
        // 资源绑定
        ObjectRelationUtils.bind(bmfMaterial, ExtractUtils.commonBindRelations(materialForm.getRelations()));
        return bmfMaterial;
    }

    private JSONObject getJsonObject(ExternalMaterialForm materialForm) {
        JSONObject jsonObject = (JSONObject) JSONObject.toJSON(materialForm);
        jsonObject.put("externalDocumentCode", materialForm.getCode());

        BmfObject materialByClass = this.businessUtils.getSyncBmfObject(BmfClassNameConst.MATERIAL_CLASSIFICATION, materialForm.getMaterialClassificationCode());
        if (materialByClass == null) {
            throw new BusinessException("物料类别不存在,编码:" + materialForm.getMaterialClassificationCode());
        }
        jsonObject.put("materialClassification", materialByClass);
        BmfObject flowUnit = this.bmfService.findByUnique(BmfClassNameConst.MEASUREMENT_UNIT, "name", materialForm.getFlowUnitName());
        if (flowUnit == null) {
            throw new BusinessException("流转单位不存在,名称:" + materialForm.getFlowUnitName());
        }
        jsonObject.put("flowUnit", flowUnit);
        if (StringUtils.isNotBlank(materialForm.getPieceWeightUnitName())) {
            BmfObject weightUnit = this.bmfService.findByUnique(BmfClassNameConst.MEASUREMENT_UNIT, "name", materialForm.getPieceWeightUnitName());
            if (weightUnit == null) {
                throw new BusinessException("单重单位不存在,名称:" + materialForm.getPieceWeightUnitName());
            }
            if (!StringUtils.equals(weightUnit.getString("unitType"), "2")) {
                throw new BusinessException("单重单位类型只能为重量,名称:" + materialForm.getPieceWeightUnitName());
            }
            jsonObject.put("pieceWeightUnit", weightUnit);
        }
        jsonObject.put("stationSpareParts", materialForm.getStationSpareParts() != null && ValueUtil.toBool(materialForm.getStatus(), false));
        String defaultWarehouseCode = materialForm.getDefaultWarehouseCode();
        BmfObject warehouse = this.businessUtils.getSyncBmfObject(BmfClassNameConst.WAREHOUSE, defaultWarehouseCode);
        jsonObject.put("defaultWarehouseCode", defaultWarehouseCode);
        jsonObject.put("defaultWarehouseName", warehouse.getString("name"));
        String type = jsonObject.getString("type");
        if (StringUtils.equals(type, "material")) {
            jsonObject.put("stationSpareParts", false);
        }
        JsonUtils.jsonMergeExtFiled(materialForm.getExtFields(), jsonObject);
        return jsonObject;
    }

    private void checkUnique(ExternalMaterialForm materialForm) {
        BmfObject materialByName = this.bmfService.findByUnique("material", "name", materialForm.getName());
        if (materialByName != null && !StringUtils.equals(materialByName.getString("externalDocumentCode"), materialForm.getCode()) && StringUtils.equals(materialByName.getString("specifications"), materialForm.getSpecifications())) {
            throw new BusinessException("物料名称+规格型号重复，物料编码:" + materialByName.getString("code"));
        }
        if (!StringUtils.equals(materialForm.getType(), "material")) {
            String ledgerClassificationCode = materialForm.getLedgerClassificationCode();
            if (StringUtils.isBlank(ledgerClassificationCode)) {
                throw new BusinessException("物料类型为工器具时,台账类别编码不能为空!");
            }
            BmfObject ledgerClassification = this.businessUtils.getSyncBmfObject(materialForm.getType(), ledgerClassificationCode);
            if (ledgerClassification == null) {
                throw new BusinessException("台账类别不存在:" + ledgerClassificationCode);
            }
            materialForm.setLedgerClassificationName(ledgerClassification.getString("name"));
        }
    }
}
