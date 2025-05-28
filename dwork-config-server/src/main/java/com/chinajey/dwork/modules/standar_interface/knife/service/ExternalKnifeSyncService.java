package com.chinajey.dwork.modules.standar_interface.knife.service;

import com.chinajay.virgo.bmf.enums.BmfEnum;
import com.chinajay.virgo.bmf.enums.BmfEnumCache;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.annotation.NoRepeatSubmit;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ToolUtils;
import com.chinajey.dwork.modules.standar_interface.knife.form.ExternalKnifeSyncForm;
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
public class ExternalKnifeSyncService {
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
    public void saveOrUpdate(ExternalKnifeSyncForm form) {

        //判断新增还是更新
        BmfObject knife = bmfService.findByUnique("knife", "externalDocumentCode", form.getCode());
        BmfObject location = businessUtils.getSyncBmfObject("location", form.getLocationCode());
        if (location == null) {
            throw new BusinessException("没找到对应存放位置" + form.getLocationCode());
        }
        if (knife == null) {
            //新增
            BmfObject bmfObject = new BmfObject("knife");
            fill(bmfObject, form, true);
            bmfObject.put("life", BigDecimal.valueOf(100));
            bmfObject.put("sourceCode", form.getCode());
            bmfObject.put("sourceType", "oaGenerate");
            bmfObject.put("externalDocumentCode", form.getCode());
            toolUtils.generatePassBox(bmfObject, LoadMaterialTypeEnum.KNIFE.getCode(), OperateSourceEnum.KNIFE.getCode(), location);

            if (StringUtils.isNotBlank(bmfObject.getString("status"))) {
                bmfObject.put("status", "warehouse");
            }
            //创建周转箱
            bmfService.saveOrUpdate(bmfObject);
            //处理库存
            toolUtils.inventoryUpdate(bmfObject);
        } else {
            //更新
            fill(knife, form, false);
            BmfObject passBox = bmfService.findByUnique(BmfClassNameConst.PASS_BOX, BmfAttributeConst.CODE, knife.getString(BmfAttributeConst.CODE));
            BmfObject passBoxReal = bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL, BmfAttributeConst.PASS_BOX_CODE, knife.getString(BmfAttributeConst.CODE));
            passBox.put("locationCode", location.getString("code"));
            passBox.put("locationName", location.getString("name"));
            passBox.put("location", location);
            passBoxReal.put("locationCode", location.getString("code"));
            passBoxReal.put("locationName", location.getString("name"));
            passBoxReal.put("location", location);
            passBoxRealService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PC.getCode(), "KNIFE", "刀具更新");
            bmfService.updateByPrimaryKeySelective(passBox);
            bmfService.updateByPrimaryKeySelective(knife);
        }
    }

    /**
     * 更新库存
     *
     * @param bmfObject 台账
     */


    //填充入参
    private void fill(BmfObject bmfObject, ExternalKnifeSyncForm form, Boolean newFlag) {
        //物料带出反写
        BmfObject material = businessUtils.getSyncBmfObject("material", form.getMaterialCode());
        if (material == null) {
            throw new BusinessException("未找到对应物料主数据" + form.getMaterialCode());
        }
        bmfObject.put("material", material);
        //类别带出反写
        BmfObject knifeClassification = businessUtils.getSyncBmfObject("knifeClassification", form.getKnifeClassificationCode());
        if (knifeClassification == null) {
            throw new BusinessException("未找到对应类别主数据" + form.getKnifeClassificationCode());
        }
        bmfObject.put("knifeClassification", knifeClassification);
        bmfObject.put("oaCreateTime", form.getOaCreateTime());
        if (newFlag) {
            bmfObject.put("name", material.get("name"));
            bmfObject.put("buGroup", material.get("ext_buGroup"));
            bmfObject.put("responsibleDept", material.getAndRefreshBmfObject("responsibleDept"));
            bmfObject.put("responsibleUser", material.getAndRefreshBmfObject("responsibleUser"));
        }
        if (StringUtils.isNotBlank(form.getWarehouseCode())) {
            BmfObject warehouse = businessUtils.getSyncBmfObject("warehouse", form.getWarehouseCode());
            if (warehouse == null) {
                throw new BusinessException("未找到对应仓库主数据" + form.getWarehouseCode());
            }
            bmfObject.put("warehouse", warehouse);
        }
        bmfObject.put("knifeClassification", knifeClassification);
        bmfObject.put("checkStatus", form.getCheckStatus());
        bmfObject.put("type", form.getType());
        if (StringUtils.isNotBlank(form.getName())) {
            bmfObject.put("name", form.getName());
        }
        if (StringUtils.isNotBlank(form.getBuGroup())) {
            bmfObject.put("buGroup", form.getBuGroup());
        }
        if (StringUtils.isNotBlank(form.getFixedAssetCode())) {
            bmfObject.put("fixedAssetCode", form.getFixedAssetCode());
        }
        if (StringUtils.isNotBlank(form.getBrand())) {
            bmfObject.put("brand", form.getBrand());
        }
        if (!ObjectUtils.isEmpty(form.getResponsibleDeptId())) {
            BmfObject department = businessUtils.getSyncBmfObject("department", form.getResponsibleDeptId().toString());
            if (department == null) {
                throw new BusinessException("未找到对应责任部门" + form.getResponsibleDeptId());
            }
            bmfObject.put("responsibleDept", department);
        }
        if (StringUtils.isNotBlank(form.getResponsibleUserCode())) {
            BmfObject user = businessUtils.getSyncBmfObject("user", form.getResponsibleUserCode());
            if (user == null) {
                throw new BusinessException("未找到对应责任人" + form.getResponsibleUserCode());
            }
            bmfObject.put("responsibleUser", user);
        }
        if (StringUtils.isNotBlank(form.getCustomerUserCode())) {
            BmfObject businessPartner = businessUtils.getSyncBmfObject("businessPartner", form.getCustomerUserCode());
            if (businessPartner == null) {
                throw new BusinessException("未找到对应关联客户" + form.getCustomerUserCode());
            }
            bmfObject.put("customer", businessPartner);
        }
        if (StringUtils.isNotBlank(form.getLocationClassification())) {
            bmfObject.put("locationClassification", form.getLocationClassification());
        }

        if (StringUtils.isNotBlank(form.getLocationCode())) {
            BmfObject location = businessUtils.getSyncBmfObject("location", form.getLocationCode());
            if (location == null) {
                throw new BusinessException("未找到对应存放位置" + form.getLocationCode());
            }
            bmfObject.put("location", location);
        }
        if (!ObjectUtils.isEmpty(form.getStorageTime())) {
            bmfObject.put("storageTime", form.getStorageTime());
        }
        if (StringUtils.isNotBlank(form.getStatus())) {
            bmfObject.put("status", form.getStatus());
        }
        if (StringUtils.isNotBlank(form.getRemark())) {
            bmfObject.put("remark", form.getRemark());
        }
        if (StringUtils.isNotBlank(form.getErpCode())) {
            bmfObject.put("erpCode", form.getErpCode());
        }
        if (!ObjectUtils.isEmpty(form.getProductionDate())) {
            bmfObject.put("productionDate", form.getProductionDate());
        }
        //寿命可以修改测试根据当时原型来的
        if (!ObjectUtils.isEmpty(form.getLife())) {
            bmfObject.put("life", form.getLife());
        }
        if (StringUtils.isNotBlank(form.getManufacturerCode())) {
            BmfObject manufacturer = businessUtils.getSyncBmfObject("businessPartner", form.getManufacturerCode());
            if (manufacturer == null) {
                throw new BusinessException("未找到对应制造商" + form.getManufacturerCode());
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
        if (StringUtils.isNotBlank(form.getBuGroup())) {
            BmfEnum bmfEnum = BmfEnumCache.getBmfEnum("buGroup");
            if (ObjectUtils.isEmpty(bmfEnum.getBmfEnumItemMap())) {
                throw new BusinessException("BU组枚举未找到");
            }
            if (StringUtils.isBlank(bmfEnum.getBmfEnumItemMap().get(form.getBuGroup()))) {
                throw new BusinessException("非枚举内bu组");
            }
        }
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), bmfObject);
    }
}
