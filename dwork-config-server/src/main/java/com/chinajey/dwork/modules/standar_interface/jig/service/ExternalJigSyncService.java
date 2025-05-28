package com.chinajey.dwork.modules.standar_interface.jig.service;

import com.chinajay.virgo.bmf.enums.BmfEnum;
import com.chinajay.virgo.bmf.enums.BmfEnumCache;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.annotation.NoRepeatSubmit;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ToolUtils;
import com.chinajey.dwork.modules.standar_interface.jig.from.ExternalJigSyncFrom;
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
public class ExternalJigSyncService {
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
    public void saveOrUpdate(ExternalJigSyncFrom jigSyncFrom) {
        //判断新增还是更新
        BmfObject jig = bmfService.findByUnique("jig", "externalDocumentCode", jigSyncFrom.getCode());
        BmfObject location = businessUtils.getSyncBmfObject("location", jigSyncFrom.getLocationCode());
        if (location == null) {
            throw new BusinessException("没找到对应存放位置" + jigSyncFrom.getLocationCode());
        }
        if (jig == null) {
            //新增
            BmfObject bmfObject = new BmfObject("jig");
            fill(bmfObject, jigSyncFrom, true);
            bmfObject.put("life", BigDecimal.valueOf(100));
            bmfObject.put("externalDocumentCode", jigSyncFrom.getCode());
            bmfObject.put("sourceType", "oaGenerate");
            //创建台账周转箱
            toolUtils.generatePassBox(bmfObject, LoadMaterialTypeEnum.JIG.getCode(), OperateSourceEnum.JIG.getCode(), location);

            if (StringUtils.isNotBlank(bmfObject.getString("status"))) {
                bmfObject.put("status", "warehouse");
            }
            //创建周转箱
            bmfService.saveOrUpdate(bmfObject);
            //处理库存
            toolUtils.inventoryUpdate(bmfObject);
        } else {
            //更新
            fill(jig, jigSyncFrom, false);
            BmfObject passBox = bmfService.findByUnique(BmfClassNameConst.PASS_BOX, BmfAttributeConst.CODE, jig.getString(BmfAttributeConst.CODE));
            BmfObject passBoxReal = bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL, BmfAttributeConst.PASS_BOX_CODE, jig.getString(BmfAttributeConst.CODE));
            passBox.put("locationCode", location.getString("code"));
            passBox.put("locationName", location.getString("name"));
            passBox.put("location", location);
            passBoxReal.put("locationCode", location.getString("code"));
            passBoxReal.put("locationName", location.getString("name"));
            passBoxReal.put("location", location);
            passBoxRealService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PC.getCode(), "JIG", "治具更新");
            bmfService.updateByPrimaryKeySelective(passBox);
            bmfService.updateByPrimaryKeySelective(jig);
        }
    }

    //填充入参
    private void fill(BmfObject bmfObject, ExternalJigSyncFrom jigSyncFrom, Boolean newflag) {
        //物料带出反写
        BmfObject material = businessUtils.getSyncBmfObject("material", jigSyncFrom.getMaterialCode());
        if (material == null) {
            throw new BusinessException("未找到对应物料主数据" + jigSyncFrom.getMaterialCode());
        }
        bmfObject.put("material", material);
        //类别带出反写
        BmfObject jigClassification = businessUtils.getSyncBmfObject("jigClassification", jigSyncFrom.getJigClassificationCode());
        if (jigClassification == null) {
            throw new BusinessException("未找到对应类别主数据" + jigSyncFrom.getJigClassificationCode());
        }
        bmfObject.put("jigClassification", jigClassification);
        bmfObject.put("oaCreateTime", jigSyncFrom.getOaCreateTime());
        if (StringUtils.isNotBlank(jigSyncFrom.getWarehouseCode())) {
            BmfObject warehouse = businessUtils.getSyncBmfObject("warehouse", jigSyncFrom.getWarehouseCode());
            if (warehouse == null) {
                throw new BusinessException("未找到对应仓库主数据" + jigSyncFrom.getWarehouseCode());
            }
            bmfObject.put("warehouse", warehouse);
        }
        bmfObject.put("checkStatus", jigSyncFrom.getCheckStatus());
        if (newflag) {
            bmfObject.put("name", material.get("name"));
            bmfObject.put("buGroup", material.get("ext_buGroup"));
            bmfObject.put("responsibleDept", material.getAndRefreshBmfObject("responsibleDept"));
            bmfObject.put("responsibleUser", material.getAndRefreshBmfObject("responsibleUser"));
        }

        if (StringUtils.isNotBlank(jigSyncFrom.getName())) {
            bmfObject.put("name", jigSyncFrom.getName());
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getBuGroup())) {
            bmfObject.put("buGroup", jigSyncFrom.getBuGroup());
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getFixedAssetCode())) {
            bmfObject.put("fixedAssetCode", jigSyncFrom.getFixedAssetCode());
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getBrand())) {
            bmfObject.put("brand", jigSyncFrom.getBrand());
        }
        if (!ObjectUtils.isEmpty(jigSyncFrom.getResponsibleDeptId())) {
            BmfObject department = businessUtils.getSyncBmfObject("department", jigSyncFrom.getResponsibleDeptId().toString());
            if (department == null) {
                throw new BusinessException("未找到对应责任部门" + jigSyncFrom.getResponsibleDeptId());
            }
            bmfObject.put("responsibleDept", department);
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getResponsibleUserCode())) {
            BmfObject user = businessUtils.getSyncBmfObject("user", jigSyncFrom.getResponsibleUserCode());
            if (user == null) {
                throw new BusinessException("未找到对应责任人" + jigSyncFrom.getResponsibleUserCode());
            }
            bmfObject.put("responsibleUser", user);
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getCustomerUserCode())) {
            BmfObject businessPartner = businessUtils.getSyncBmfObject("businessPartner", jigSyncFrom.getCustomerUserCode());
            if (businessPartner == null) {
                throw new BusinessException("未找到对应关联客户" + jigSyncFrom.getCustomerUserCode());
            }
            bmfObject.put("customer", businessPartner);
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getLocationClassification())) {
            bmfObject.put("locationClassification", jigSyncFrom.getLocationClassification());
        }

        if (StringUtils.isNotBlank(jigSyncFrom.getLocationCode())) {
            BmfObject location = businessUtils.getSyncBmfObject("location", jigSyncFrom.getLocationCode());
            if (location == null) {
                throw new BusinessException("未找到对应存放位置" + jigSyncFrom.getLocationCode());
            }
            bmfObject.put("location", location);
        }
        if (!ObjectUtils.isEmpty(jigSyncFrom.getStorageTime())) {
            bmfObject.put("storageTime", jigSyncFrom.getStorageTime());
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getStatus())) {
            bmfObject.put("status", jigSyncFrom.getStatus());
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getRemark())) {
            bmfObject.put("remark", jigSyncFrom.getRemark());
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getErpCode())) {
            bmfObject.put("erpCode", jigSyncFrom.getErpCode());
        }
        if (!ObjectUtils.isEmpty(jigSyncFrom.getCheckCycle())) {
            bmfObject.put("checkCycle", jigSyncFrom.getCheckCycle());
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getCycleUnit())) {
            bmfObject.put("cycleUnit", jigSyncFrom.getCycleUnit());
        }
        if (!ObjectUtils.isEmpty(jigSyncFrom.getProductionDate())) {
            bmfObject.put("productionDate", jigSyncFrom.getProductionDate());
        }
        //寿命可以修改测试根据当时原型来的
        if (!ObjectUtils.isEmpty(jigSyncFrom.getLife())) {
            bmfObject.put("life", jigSyncFrom.getLife());
        }
        if (StringUtils.isNotBlank(jigSyncFrom.getManufacturerCode())) {
            BmfObject manufacturer = businessUtils.getSyncBmfObject("businessPartner", jigSyncFrom.getManufacturerCode());
            if (manufacturer == null) {
                throw new BusinessException("未找到对应制造商" + jigSyncFrom.getManufacturerCode());
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
        if (StringUtils.isNotBlank(jigSyncFrom.getBuGroup())) {
            BmfEnum bmfEnum = BmfEnumCache.getBmfEnum("buGroup");
            if (ObjectUtils.isEmpty(bmfEnum.getBmfEnumItemMap())) {
                throw new BusinessException("BU组枚举未找到");
            }
            if (StringUtils.isBlank(bmfEnum.getBmfEnumItemMap().get(jigSyncFrom.getBuGroup()))) {
                throw new BusinessException("非枚举内bu组");
            }
        }
        JsonUtils.jsonMergeExtFiled(jigSyncFrom.getExtFields(), bmfObject);
    }
}
