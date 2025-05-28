package com.chinajey.dwork.modules.standar_interface.equipment.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.modules.standar_interface.equipment.form.ExternalEquipmentForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

/**
 * @author erton.bi
 */
@Service
public class ExternalEquipmentService {

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    public static final String BMF_CLASS = "equipment";


    public BmfObject saveOrUpdate(ExternalEquipmentForm form) {
        JSONObject jsonObject = getJsonObject(form);
        BmfObject bmfEquipment = ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject);
        //更新设备组操作
        updateEquipmentGroup(bmfEquipment, jsonObject.getString("oldEquipmentGroupCode"));
        return bmfEquipment;
    }


    private JSONObject getJsonObject(ExternalEquipmentForm externalEquipmentForm) {
        JSONObject jsonObject = (JSONObject) JSONObject.toJSON(externalEquipmentForm);
        jsonObject.put("externalDocumentCode", externalEquipmentForm.getCode());

        //设备类别
        BmfObject equipmentClassification = this.businessUtils.getSyncBmfObject("equipmentClassification", externalEquipmentForm.getEquipmentClassificationCode());
        if (equipmentClassification == null) {
            throw new BusinessException("设备类别不存在,编码:" + externalEquipmentForm.getEquipmentClassificationCode());
        }
        jsonObject.put("equipmentClassification", equipmentClassification);

        //设备组
        if (StringUtils.isNotBlank(externalEquipmentForm.getEquipmentGroupCode())) {
            BmfObject equipmentGroup = this.businessUtils.getSyncBmfObject("equipmentGroup", externalEquipmentForm.getEquipmentGroupCode());
            if (equipmentGroup == null) {
                throw new BusinessException("设备组不存在,编码:" + externalEquipmentForm.getEquipmentGroupCode());
            }
            jsonObject.put("equipmentGroupCode", equipmentGroup.getString("code"));
            jsonObject.put("equipmentGroupName", equipmentGroup.getString("name"));
        }

        //查询当前的设备信息  用于更新设备组信息
        BmfObject equipment = this.businessUtils.getSyncBmfObject(BMF_CLASS, externalEquipmentForm.getCode());
        if (equipment != null) {
            jsonObject.put("oldEquipmentGroupCode",equipment.getString("equipmentGroupCode"));
        }

        //单位信息
        if (org.apache.commons.lang3.StringUtils.isNotBlank(externalEquipmentForm.getUnitName())) {
            BmfObject unit = bmfService.findByUnique("measurementUnit", "name", externalEquipmentForm.getUnitName());
            if (unit == null) {
                throw new BusinessException("未找到单位主数据" + externalEquipmentForm.getUnitName());
            }
            jsonObject.put("unit", unit);
        }

        //位置信息
        if (StringUtils.isNotBlank(externalEquipmentForm.getLocationCode())) {
            BmfObject location = this.businessUtils.getSyncBmfObject("location", externalEquipmentForm.getLocationCode());
            if (location == null) {
                throw new BusinessException("存放位置不存在,编码:" + externalEquipmentForm.getLocationCode());
            }
            jsonObject.put("location", location);
        }

        //部门数据
        if (StringUtils.isNotBlank(externalEquipmentForm.getDepartmentCode())) {
            BmfObject department = this.businessUtils.getSyncBmfObject("department", externalEquipmentForm.getDepartmentCode());
            if (department == null) {
                throw new BusinessException("部门不存在,编码:" + externalEquipmentForm.getDepartmentCode());
            }
            jsonObject.put("department", department);
        }

        //责任人
        if (StringUtils.isNotBlank(externalEquipmentForm.getResponsibleUserCode())) {
            BmfObject responsibleUser = this.businessUtils.getSyncBmfObject("user", externalEquipmentForm.getResponsibleUserCode());
            if (responsibleUser == null) {
                throw new BusinessException("责任人不存在,编码:" + externalEquipmentForm.getResponsibleUserCode());
            }
            jsonObject.put("responsibleUser", responsibleUser);
        }

        //监督人
        if (StringUtils.isNotBlank(externalEquipmentForm.getSuperviseUserCode())) {
            BmfObject superviseUser = this.businessUtils.getSyncBmfObject("user", externalEquipmentForm.getSuperviseUserCode());
            if (superviseUser == null) {
                throw new BusinessException("监督人不存在,编码:" + externalEquipmentForm.getSuperviseUserCode());
            }
            jsonObject.put("superviseUser", superviseUser);
        }

        JsonUtils.jsonMergeExtFiled(externalEquipmentForm.getExtFields(), jsonObject);
        return jsonObject;
    }

    private void updateEquipmentGroup(BmfObject bmfObject, String oldEquipmentGroupCode) {
        String equipmentGroupCode = bmfObject.getString("equipmentGroupCode");
        String equipmentId = bmfObject.getString("id");
        if (oldEquipmentGroupCode == null && equipmentGroupCode == null) {
            //新旧关联设备组都是空的，return
            return;
        } else if ((oldEquipmentGroupCode == null ? "" : oldEquipmentGroupCode).equals(equipmentGroupCode == null ? "" : equipmentGroupCode)) {
            return;
        }

        //获取设备组关联设备,删除旧关联
        BmfObject oldGroupEquipment = bmfService.findByUnique("groupEquipment", "equipment", equipmentId);
        if (oldGroupEquipment != null) {
            oldGroupEquipment.put("isDelete", "true");
            bmfService.saveOrUpdate(oldGroupEquipment);
        }

        if (equipmentGroupCode != null) {
            BmfObject equipmentGroup = bmfService.findByUnique("equipmentGroup", "code", equipmentGroupCode);
            List<BmfObject> groupEquipments = equipmentGroup.getList("groupEquipments");
            BmfObject groupEquipment = new BmfObject("groupEquipment");
            groupEquipment.put("equipment", equipmentId);
            groupEquipment.put("equipmentGroup", equipmentGroup.getString("id"));
            groupEquipments.add(groupEquipment);
            equipmentGroup.put("groupEquipments", groupEquipments);
            bmfService.saveOrUpdate(equipmentGroup);
        }
    }
}
