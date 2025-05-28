package com.chinajey.dwork.modules.standar_interface.measuringTool.service;

import com.chinajay.virgo.bmf.enums.BmfEnum;
import com.chinajay.virgo.bmf.enums.BmfEnumCache;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.annotation.NoRepeatSubmit;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ToolUtils;
import com.chinajey.dwork.modules.standar_interface.measuringTool.form.ExternalMeasuringToolForm;
import com.tengnat.dwork.common.constant.BmfAttributeConst;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.enums.EquipSourceEnum;
import com.tengnat.dwork.common.enums.LoadMaterialTypeEnum;
import com.tengnat.dwork.common.enums.OperateSourceEnum;
import com.tengnat.dwork.common.utils.JsonUtils;
import com.tengnat.dwork.modules.manufacture.service.PassBoxRealService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;

@Service
public class ExternalMeasuringToolSyncService {

    @Resource
    private BusinessUtils businessUtils;
    @Resource
    private BmfService bmfService;
    @Resource
    private ToolUtils toolUtils;
    @Resource
    private PassBoxRealService passBoxRealService;

    @Transactional(rollbackFor = Exception.class)
    @NoRepeatSubmit
    public void saveOrUpdate(ExternalMeasuringToolForm measuringToolForm) {
        //判断新增还是更新
        BmfObject measuringTool = bmfService.findByUnique(BmfClassNameConst.MEASURING_TOOL, "externalDocumentCode", measuringToolForm.getCode());
        BmfObject location = businessUtils.getSyncBmfObject("location", measuringToolForm.getLocationCode());
        if (location == null) {
            throw new BusinessException("没找到对应存放位置" + measuringToolForm.getLocationCode());
        }
        if (measuringTool == null) {
            //新增
            BmfObject bmfObject = new BmfObject(BmfClassNameConst.MEASURING_TOOL);
            fill(bmfObject, measuringToolForm, true);
            bmfObject.put("life", BigDecimal.valueOf(100));
            bmfObject.put("externalDocumentCode", measuringToolForm.getCode());
            bmfObject.put("sourceType", "oaGenerate");
            //创建台账周转箱
            toolUtils.generatePassBox(bmfObject, LoadMaterialTypeEnum.MEASURING_TOOL.getCode(), OperateSourceEnum.MEASURING_TOOL.getCode(), location);

            if (StringUtils.isNotBlank(bmfObject.getString("status"))) {
                bmfObject.put("status", "warehouse");
            }
            //创建
            bmfService.saveOrUpdate(bmfObject);
            //处理库存
            toolUtils.inventoryUpdate(bmfObject);
        } else {
            //更新
            fill(measuringTool, measuringToolForm, false);
            BmfObject passBox = bmfService.findByUnique(BmfClassNameConst.PASS_BOX, BmfAttributeConst.CODE, measuringTool.getString(BmfAttributeConst.CODE));
            BmfObject passBoxReal = bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL, BmfAttributeConst.PASS_BOX_CODE, measuringTool.getString(BmfAttributeConst.CODE));
            passBox.put("locationCode", location.getString("code"));
            passBox.put("locationName", location.getString("name"));
            passBox.put("location", location);
            passBoxReal.put("locationCode", location.getString("code"));
            passBoxReal.put("locationName", location.getString("name"));
            passBoxReal.put("location", location);
            passBoxRealService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PC.getCode(), "MEASURING_TOOL", "量检具更新");
            bmfService.updateByPrimaryKeySelective(passBox);
            bmfService.updateByPrimaryKeySelective(measuringTool);
        }
    }

    //填充入参
    private void fill(BmfObject bmfObject, ExternalMeasuringToolForm measuringToolForm, Boolean newflag) {
        //物料带出反写
        BmfObject material = businessUtils.getSyncBmfObject(BmfClassNameConst.MATERIAL, measuringToolForm.getMaterialCode());
        if (material == null) {
            throw new BusinessException("未找到对应物料主数据" + measuringToolForm.getMaterialCode());
        }
        bmfObject.put("material", material);
        //类别带出反写
        BmfObject measuringToolClassification = businessUtils.getSyncBmfObject(BmfClassNameConst.MEASURING_TOOL_CLASSIFICATION, measuringToolForm.getMeasuringToolClassificationCode());
        if (measuringToolClassification == null) {
            throw new BusinessException("未找到对应类别主数据" + measuringToolForm.getMeasuringToolClassificationCode());
        }
        bmfObject.put("measuringToolClassification", measuringToolClassification);
        bmfObject.put("oaCreateTime", measuringToolForm.getOaCreateTime());
        if (StringUtils.isNotBlank(measuringToolForm.getWarehouseCode())) {
            BmfObject warehouse = businessUtils.getSyncBmfObject(BmfClassNameConst.WAREHOUSE, measuringToolForm.getWarehouseCode());
            if (warehouse == null) {
                throw new BusinessException("未找到对应仓库主数据" + measuringToolForm.getWarehouseCode());
            }
            bmfObject.put("warehouse", warehouse);
        }
        bmfObject.put("checkStatus", measuringToolForm.getCheckStatus());
        if (newflag) {
            bmfObject.put("name", material.get("name"));
            bmfObject.put("buGroup", material.get("ext_buGroup"));
            bmfObject.put("responsibleDept", material.getAndRefreshBmfObject("responsibleDept"));
            bmfObject.put("responsibleUser", material.getAndRefreshBmfObject("responsibleUser"));
        }

        if (StringUtils.isNotBlank(measuringToolForm.getName())) {
            bmfObject.put("name", measuringToolForm.getName());
        }
        if (StringUtils.isNotBlank(measuringToolForm.getBuGroup())) {
            bmfObject.put("buGroup", measuringToolForm.getBuGroup());
        }
        if (StringUtils.isNotBlank(measuringToolForm.getFixedAssetCode())) {
            bmfObject.put("fixedAssetCode", measuringToolForm.getFixedAssetCode());
        }
        if (StringUtils.isNotBlank(measuringToolForm.getBrand())) {
            bmfObject.put("brand", measuringToolForm.getBrand());
        }
        if (StringUtils.isNotBlank(measuringToolForm.getSourceCode())) {
            bmfObject.put("sourceCode", measuringToolForm.getSourceCode());
        }
        if (!ObjectUtils.isEmpty(measuringToolForm.getResponsibleDeptId())) {
            BmfObject department = businessUtils.getSyncBmfObject(BmfClassNameConst.DEPARTMENT, measuringToolForm.getResponsibleDeptId().toString());
            if (department == null) {
                throw new BusinessException("未找到对应责任部门" + measuringToolForm.getResponsibleDeptId());
            }
            bmfObject.put("responsibleDept", department);
        }
        if (StringUtils.isNotBlank(measuringToolForm.getResponsibleUserCode())) {
            BmfObject user = businessUtils.getSyncBmfObject(BmfClassNameConst.USER, measuringToolForm.getResponsibleUserCode());
            if (user == null) {
                throw new BusinessException("未找到对应责任人" + measuringToolForm.getResponsibleUserCode());
            }
            bmfObject.put("responsibleUser", user);
        }
        if (StringUtils.isNotBlank(measuringToolForm.getCustomerUserCode())) {
            BmfObject businessPartner = businessUtils.getSyncBmfObject(BmfClassNameConst.BUSINESS_PARTNER, measuringToolForm.getCustomerUserCode());
            if (businessPartner == null) {
                throw new BusinessException("未找到对应关联客户" + measuringToolForm.getCustomerUserCode());
            }
            bmfObject.put("customer", businessPartner);
        }
        if (StringUtils.isNotBlank(measuringToolForm.getLocationClassification())) {
            bmfObject.put("locationClassification", measuringToolForm.getLocationClassification());
        }

        if (StringUtils.isNotBlank(measuringToolForm.getLocationCode())) {
            BmfObject location = businessUtils.getSyncBmfObject(BmfClassNameConst.LOCATION, measuringToolForm.getLocationCode());
            if (location == null) {
                throw new BusinessException("未找到对应存放位置" + measuringToolForm.getLocationCode());
            }
            bmfObject.put("location", location);
        }
        if (!ObjectUtils.isEmpty(measuringToolForm.getStorageTime())) {
            bmfObject.put("storageTime", measuringToolForm.getStorageTime());
        }
        if (!ObjectUtils.isEmpty(measuringToolForm.getCheckDate())) {
            bmfObject.put("checkDate", measuringToolForm.getCheckDate());
        }
        if (!ObjectUtils.isEmpty(measuringToolForm.getNextCheckDate())) {
            bmfObject.put("nextCheckDate", measuringToolForm.getNextCheckDate());
        }
        if (!ObjectUtils.isEmpty(measuringToolForm.getAccuracy())) {
            bmfObject.put("accuracy", measuringToolForm.getAccuracy());
        }
        if (StringUtils.isNotBlank(measuringToolForm.getStatus())) {
            bmfObject.put("status", measuringToolForm.getStatus());
        }
        if (StringUtils.isNotBlank(measuringToolForm.getRemark())) {
            bmfObject.put("remark", measuringToolForm.getRemark());
        }
        if (StringUtils.isNotBlank(measuringToolForm.getErpCode())) {
            bmfObject.put("erpCode", measuringToolForm.getErpCode());
        }

        if (!ObjectUtils.isEmpty(measuringToolForm.getCheckCycle())) {
            bmfObject.put("checkCycle", measuringToolForm.getCheckCycle());
        }

        if (StringUtils.isNotBlank(measuringToolForm.getAccuracyUnit())) {
            bmfObject.put("cycleUnit", measuringToolForm.getAccuracyUnit());
        }

        if (StringUtils.isNotBlank(measuringToolForm.getCycleUnit())) {
            bmfObject.put("cycleUnit", measuringToolForm.getCycleUnit());
        }
        if (!ObjectUtils.isEmpty(measuringToolForm.getProductionDate())) {
            bmfObject.put("productionDate", measuringToolForm.getProductionDate());
        }
        //寿命可以修改测试根据当时原型来的
        if (!ObjectUtils.isEmpty(measuringToolForm.getLife())) {
            bmfObject.put("life", measuringToolForm.getLife());
        }
        //校验方式
        if (StringUtils.isNotBlank(measuringToolForm.getCheckMethod())) {
            if (!"internal".equals(measuringToolForm.getCheckMethod()) && !"external".equals(measuringToolForm.getCheckMethod())) {
                throw new BusinessException("校验方式只能为internal或external");
            } else {
                bmfObject.put("checkMethod", measuringToolForm.getCheckMethod());
            }
        }
        if (StringUtils.isNotBlank(measuringToolForm.getManufacturerCode())) {
            BmfObject manufacturer = businessUtils.getSyncBmfObject(BmfClassNameConst.BUSINESS_PARTNER, measuringToolForm.getManufacturerCode());
            if (manufacturer == null) {
                throw new BusinessException("未找到对应制造商" + measuringToolForm.getManufacturerCode());
            }
            bmfObject.put("manufacturer", manufacturer);
        }
        //测试要求加当时是和前端页面对齐的****
        if (ObjectUtils.isEmpty(bmfObject.get("responsibleDept"))) {
            throw new BusinessException("责任部门不能为空");
        }
        if (ObjectUtils.isEmpty(bmfObject.get("responsibleUser"))) {
            throw new BusinessException("责任人不能为空");
        }
        //判断bu组是否在枚举中
        if (StringUtils.isNotBlank(measuringToolForm.getBuGroup())) {
            BmfEnum bmfEnum = BmfEnumCache.getBmfEnum("buGroup");
            if (ObjectUtils.isEmpty(bmfEnum.getBmfEnumItemMap())) {
                throw new BusinessException("BU组枚举未找到");
            }
            if (com.baomidou.mybatisplus.core.toolkit.StringUtils.isBlank(bmfEnum.getBmfEnumItemMap().get(measuringToolForm.getBuGroup()))) {
                throw new BusinessException("非枚举内bu组");
            }
        }
        JsonUtils.jsonMergeExtFiled(measuringToolForm.getExtFields(), bmfObject);
    }

}
