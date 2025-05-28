package com.chinajey.dwork.modules.standar_interface.fixture.service;

import com.chinajay.virgo.bmf.enums.BmfEnum;
import com.chinajay.virgo.bmf.enums.BmfEnumCache;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.annotation.NoRepeatSubmit;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ToolUtils;
import com.chinajey.dwork.modules.standar_interface.fixture.form.ExternalFixtureSyncForm;
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
public class ExternalFixtureSyncService {
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
    public void saveOrUpdate(ExternalFixtureSyncForm fixtureSyncForm) {
        //判断新增还是更新
        BmfObject fixture = bmfService.findByUnique(BmfClassNameConst.FIXTURE, "externalDocumentCode", fixtureSyncForm.getCode());
        BmfObject location = businessUtils.getSyncBmfObject("location", fixtureSyncForm.getLocationCode());
        if (location == null) {
            throw new BusinessException("没找到对应存放位置" + fixtureSyncForm.getLocationCode());
        }
        if (fixture == null) {
            //新增
            BmfObject bmfObject = new BmfObject(BmfClassNameConst.FIXTURE);
            fill(bmfObject, fixtureSyncForm, true);
            bmfObject.put("life", BigDecimal.valueOf(100));
            bmfObject.put("externalDocumentCode", fixtureSyncForm.getCode());
            bmfObject.put("sourceType", "oaGenerate");
            //创建台账周转箱
            toolUtils.generatePassBox(bmfObject, LoadMaterialTypeEnum.FIXTURE.getCode(), OperateSourceEnum.FIXTURE.getCode(), location);

            if (StringUtils.isNotBlank(bmfObject.getString("status"))) {
                bmfObject.put("status", "warehouse");
            }
            //创建
            bmfService.saveOrUpdate(bmfObject);
            //处理库存
            toolUtils.inventoryUpdate(bmfObject);
        } else {
            //更新
            fill(fixture, fixtureSyncForm, false);
            BmfObject passBox = bmfService.findByUnique(BmfClassNameConst.PASS_BOX, BmfAttributeConst.CODE, fixture.getString(BmfAttributeConst.CODE));
            BmfObject passBoxReal = bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL, BmfAttributeConst.PASS_BOX_CODE, fixture.getString(BmfAttributeConst.CODE));
            passBox.put("locationCode", location.getString("code"));
            passBox.put("locationName", location.getString("name"));
            passBox.put("location", location);
            passBoxReal.put("locationCode", location.getString("code"));
            passBoxReal.put("locationName", location.getString("name"));
            passBoxReal.put("location", location);
            passBoxRealService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PC.getCode(), "FIXTURE", "夹具更新");
            bmfService.updateByPrimaryKeySelective(passBox);
            bmfService.updateByPrimaryKeySelective(fixture);
        }
    }

    //填充入参
    private void fill(BmfObject bmfObject, ExternalFixtureSyncForm fixtureSyncForm, Boolean newflag) {
        //物料带出反写
        BmfObject material = businessUtils.getSyncBmfObject(BmfClassNameConst.MATERIAL, fixtureSyncForm.getMaterialCode());
        if (material == null) {
            throw new BusinessException("未找到对应物料主数据" + fixtureSyncForm.getMaterialCode());
        }
        bmfObject.put("material", material);
        //类别带出反写
        BmfObject fixtureClassification = businessUtils.getSyncBmfObject(BmfClassNameConst.FIXTURE_CLASSIFICATION, fixtureSyncForm.getFixtureClassificationCode());
        if (fixtureClassification == null) {
            throw new BusinessException("未找到对应类别主数据" + fixtureSyncForm.getFixtureClassificationCode());
        }
        bmfObject.put("fixtureClassification", fixtureClassification);
        bmfObject.put("oaCreateTime", fixtureSyncForm.getOaCreateTime());
        if (StringUtils.isNotBlank(fixtureSyncForm.getWarehouseCode())) {
            BmfObject warehouse = businessUtils.getSyncBmfObject(BmfClassNameConst.WAREHOUSE, fixtureSyncForm.getWarehouseCode());
            if (warehouse == null) {
                throw new BusinessException("未找到对应仓库主数据" + fixtureSyncForm.getWarehouseCode());
            }
            bmfObject.put("warehouse", warehouse);
        }
        bmfObject.put("checkStatus", fixtureSyncForm.getCheckStatus());
        if (newflag) {
            bmfObject.put("name", material.get("name"));
            bmfObject.put("buGroup", material.get("ext_buGroup"));
            bmfObject.put("responsibleDept", material.getAndRefreshBmfObject("responsibleDept"));
            bmfObject.put("responsibleUser", material.getAndRefreshBmfObject("responsibleUser"));
        }

        if (StringUtils.isNotBlank(fixtureSyncForm.getName())) {
            bmfObject.put("name", fixtureSyncForm.getName());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getBuGroup())) {
            bmfObject.put("buGroup", fixtureSyncForm.getBuGroup());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getFixedAssetCode())) {
            bmfObject.put("fixedAssetCode", fixtureSyncForm.getFixedAssetCode());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getBrand())) {
            bmfObject.put("brand", fixtureSyncForm.getBrand());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getDrawingNumber())) {
            bmfObject.put("drawingNumber", fixtureSyncForm.getDrawingNumber());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getProcedureNo())) {
            bmfObject.put("processNo", fixtureSyncForm.getProcessNo());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getProcedureNo())) {
            bmfObject.put("procedureNo", fixtureSyncForm.getProcedureNo());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getSourceCode())) {
            bmfObject.put("sourceCode", fixtureSyncForm.getSourceCode());
        }
        if (!ObjectUtils.isEmpty(fixtureSyncForm.getResponsibleDeptId())) {
            BmfObject department = businessUtils.getSyncBmfObject(BmfClassNameConst.DEPARTMENT, fixtureSyncForm.getResponsibleDeptId().toString());
            if (department == null) {
                throw new BusinessException("未找到对应责任部门" + fixtureSyncForm.getResponsibleDeptId());
            }
            bmfObject.put("responsibleDept", department);
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getResponsibleUserCode())) {
            BmfObject user = businessUtils.getSyncBmfObject(BmfClassNameConst.USER, fixtureSyncForm.getResponsibleUserCode());
            if (user == null) {
                throw new BusinessException("未找到对应责任人" + fixtureSyncForm.getResponsibleUserCode());
            }
            bmfObject.put("responsibleUser", user);
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getCustomerUserCode())) {
            BmfObject businessPartner = businessUtils.getSyncBmfObject(BmfClassNameConst.BUSINESS_PARTNER, fixtureSyncForm.getCustomerUserCode());
            if (businessPartner == null) {
                throw new BusinessException("未找到对应关联客户" + fixtureSyncForm.getCustomerUserCode());
            }
            bmfObject.put("customer", businessPartner);
        }

        if (StringUtils.isNotBlank(fixtureSyncForm.getLocationClassification())) {
            if (!"internal".equals(fixtureSyncForm.getLocationClassification()) && !"external".equals(fixtureSyncForm.getLocationClassification())) {
                throw new BusinessException("校验方式只能为internal或external");
            } else {
                bmfObject.put("locationClassification", fixtureSyncForm.getLocationClassification());
            }
        }

        if (StringUtils.isNotBlank(fixtureSyncForm.getLocationCode())) {
            BmfObject location = businessUtils.getSyncBmfObject(BmfClassNameConst.LOCATION, fixtureSyncForm.getLocationCode());
            if (location == null) {
                throw new BusinessException("未找到对应存放位置" + fixtureSyncForm.getLocationCode());
            }
            bmfObject.put("location", location);
        }
        if (!ObjectUtils.isEmpty(fixtureSyncForm.getStorageTime())) {
            bmfObject.put("storageTime", fixtureSyncForm.getStorageTime());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getStatus())) {
            bmfObject.put("status", fixtureSyncForm.getStatus());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getRemark())) {
            bmfObject.put("remark", fixtureSyncForm.getRemark());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getErpCode())) {
            bmfObject.put("erpCode", fixtureSyncForm.getErpCode());
        }
        if (!ObjectUtils.isEmpty(fixtureSyncForm.getCheckCycle())) {
            bmfObject.put("checkCycle", fixtureSyncForm.getCheckCycle());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getCycleUnit())) {
            bmfObject.put("cycleUnit", fixtureSyncForm.getCycleUnit());
        }
        if (!ObjectUtils.isEmpty(fixtureSyncForm.getProductionDate())) {
            bmfObject.put("productionDate", fixtureSyncForm.getProductionDate());
        }
        //寿命可以修改测试根据当时原型来的
        if (!ObjectUtils.isEmpty(fixtureSyncForm.getLife())) {
            bmfObject.put("life", fixtureSyncForm.getLife());
        }
        if (StringUtils.isNotBlank(fixtureSyncForm.getManufacturerCode())) {
            BmfObject manufacturer = businessUtils.getSyncBmfObject(BmfClassNameConst.BUSINESS_PARTNER, fixtureSyncForm.getManufacturerCode());
            if (manufacturer == null) {
                throw new BusinessException("未找到对应制造商" + fixtureSyncForm.getManufacturerCode());
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
        if (StringUtils.isNotBlank(fixtureSyncForm.getBuGroup())) {
            BmfEnum bmfEnum = BmfEnumCache.getBmfEnum("buGroup");
            if (ObjectUtils.isEmpty(bmfEnum.getBmfEnumItemMap())) {
                throw new BusinessException("BU组枚举未找到");
            }
            if (StringUtils.isBlank(bmfEnum.getBmfEnumItemMap().get(fixtureSyncForm.getBuGroup()))) {
                throw new BusinessException("非枚举内bu组");
            }
        }
        JsonUtils.jsonMergeExtFiled(fixtureSyncForm.getExtFields(), bmfObject);
    }
}
