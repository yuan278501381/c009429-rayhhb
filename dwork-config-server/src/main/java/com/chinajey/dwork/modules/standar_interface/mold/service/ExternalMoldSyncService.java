package com.chinajey.dwork.modules.standar_interface.mold.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.enums.BmfEnum;
import com.chinajay.virgo.bmf.enums.BmfEnumCache;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.Conjunction;
import com.chinajay.virgo.bmf.sql.OperationType;
import com.chinajay.virgo.bmf.sql.Restriction;
import com.chinajay.virgo.bmf.sql.Where;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.annotation.NoRepeatSubmit;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ToolUtils;
import com.chinajey.dwork.modules.standar_interface.mold.form.ExternalMoldSyncForm;
import com.tengnat.dwork.common.constant.BmfAttributeConst;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.enums.LoadMaterialTypeEnum;
import com.tengnat.dwork.common.enums.OperateSourceEnum;
import com.tengnat.dwork.common.utils.JsonUtils;
import com.tengnat.dwork.modules.basic_data.service.MoldService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExternalMoldSyncService {
    @Resource
    private BusinessUtils businessUtils;
    @Resource
    private BmfService bmfService;
    @Resource
    private MoldService moldService;
    @Resource
    private ToolUtils toolUtils;


    @Transactional(rollbackFor = Exception.class)
    @NoRepeatSubmit
    public void saveOrUpdate(ExternalMoldSyncForm form) {
        // 判断新增还是更新
        BmfObject mold = bmfService.findByUnique("mold", "externalDocumentCode", form.getCode());
        BmfObject location = bmfService.findByUnique("location", "code", form.getLocationCode());
        if (location == null) {
            throw new BusinessException("没找到对应存放位置" + form.getLocationCode());
        }
        if (mold == null) {
            // 新增
            BmfObject bmfObject = new BmfObject("mold");
            fill(bmfObject, form, true);
            bmfObject.put("life", BigDecimal.valueOf(100));
            bmfObject.put("sourceType", "oaGenerate");
            bmfObject.put("sourceCode", form.getCode());
            bmfObject.put("externalDocumentCode", form.getCode());
            toolUtils.generatePassBox(bmfObject, LoadMaterialTypeEnum.MOLD.getCode(), OperateSourceEnum.MOLD.getCode(), location);
            if (StringUtils.isNotBlank(bmfObject.getString("status"))) {
                bmfObject.put("status", "warehouse");
            }
            bmfService.saveOrUpdate(bmfObject);
            this.addMoldCaveNumberRecord(bmfObject, true);
        } else {
            // 更新
            fill(mold, form, false);
            moldService.update(mold);
        }
    }

    // 添加穴号记录
    private void addMoldCaveNumberRecord(JSONObject bmfObject, Boolean isAdd) {
        BmfObject moldOld = bmfService.find(BmfClassNameConst.MOLD, bmfObject.getLong("id"));
        List<BmfObject> oldMoldCaveNumbers = moldOld.getAndRefreshList("moldCaveNumbers");
        JSONArray moldCaveNumbers = bmfObject.getJSONArray(BmfAttributeConst.MOLD_CAVE_NUMBERS);
        if (CollectionUtils.isEmpty(moldCaveNumbers)) {
            return;
        }
        if (isAdd) {
            List<BmfObject> moldCaveNumberRecords = new ArrayList<>();
            //直接加穴号记录
            for (Object moldCaveNumberObj : moldCaveNumbers) {
                JSONObject moldCaveNumber = (JSONObject) moldCaveNumberObj;
                BmfObject moldCaveNumberRecord = BmfUtils.genericFromJsonExt(moldCaveNumber, "moldCaveNumberRecord");
                moldCaveNumberRecord.remove("id");
                moldCaveNumberRecord.put("moldCode", moldOld.getString("code"));
                moldCaveNumberRecords.add(moldCaveNumberRecord);
            }
            bmfService.saveOrUpdate(moldCaveNumberRecords);
        } else {
            Map<String, List<BmfObject>> collect = oldMoldCaveNumbers.stream().collect(Collectors.groupingBy(item -> item.getString("caveNumber") + "|"
                    + item.getString("type") + "|"
                    + (org.apache.commons.lang3.ObjectUtils.isNotEmpty(item.getAndRefreshBmfObject("spareParts")) ?
                    item.getAndRefreshBmfObject("spareParts").getPrimaryKeyValue() : "")));
            List<BmfObject> moldCaveNumberRecords = new ArrayList<>();
            for (Object moldCaveNumberObj : moldCaveNumbers) {
                JSONObject moldCaveNumber = (JSONObject) moldCaveNumberObj;
                JSONObject spareParts = moldCaveNumber.getJSONObject("spareParts");
                String sparePartsId = "";
                if (org.apache.commons.lang3.ObjectUtils.isNotEmpty(spareParts)) {
                    sparePartsId = spareParts.getString("id");
                }
                String caveNumber = moldCaveNumber.getString("caveNumber") + "|" + moldCaveNumber.getString("type") + "|" + sparePartsId;
                List<BmfObject> oldMoldCaveNumber = collect.get(caveNumber);
                // 有变化加记录
                if (org.apache.commons.lang3.ObjectUtils.isEmpty(oldMoldCaveNumber)) {
                    BmfObject moldCaveNumberRecord = BmfUtils.genericFromJsonExt(moldCaveNumber, "moldCaveNumberRecord");
                    moldCaveNumberRecord.remove("id");
                    moldCaveNumberRecord.put("moldCode", moldOld.getString("code"));
                    moldCaveNumberRecords.add(moldCaveNumberRecord);
                }
            }
            bmfService.saveOrUpdate(moldCaveNumberRecords);
        }

    }

    /**
     * 更新库存
     *
     * @param bmfObject 台账
     */
    private void inventoryUpdate(BmfObject bmfObject) {
        BmfObject utensilInventory = this.bmfService.findByUnique("utensilInventory", "material", bmfObject.getAndRefreshBmfObject("material").getPrimaryKeyValue());
        if (utensilInventory == null) {
            utensilInventory = new BmfObject("utensilInventory");
            utensilInventory.put("material", bmfObject.getAndRefreshBmfObject("material"));
            utensilInventory.put("quantity", BigDecimal.ONE);
            utensilInventory.put("receivedQuantity", BigDecimal.ZERO);
            utensilInventory.put("lockQuantity", BigDecimal.ZERO);
            if ("warehouse".equals(bmfObject.getString("status"))) {
                utensilInventory.put("inventoryQuantity", BigDecimal.ONE);
            } else {
                utensilInventory.put("inventoryQuantity", BigDecimal.ZERO);
            }
            bmfService.saveOrUpdate(utensilInventory);
        } else {
            BigDecimal quantity = ValueUtil.toBigDecimal(utensilInventory.getBigDecimal("quantity"), BigDecimal.ZERO).add(BigDecimal.ONE);
            utensilInventory.put("quantity", quantity);
            if ("warehouse".equals(bmfObject.getString("status"))) {
                BigDecimal inventoryQuantity = ValueUtil.toBigDecimal(utensilInventory.getBigDecimal("inventoryQuantity"), BigDecimal.ZERO).add(BigDecimal.ONE);
                utensilInventory.put("inventoryQuantity", inventoryQuantity);
            }
            this.bmfService.updateByPrimaryKeySelective(utensilInventory);
        }
    }


    // 填充入参
    private void fill(BmfObject bmfObject, ExternalMoldSyncForm form, Boolean newFlag) {
        // 物料带出反写
        BmfObject material = bmfService.findByUnique("material", "code", form.getMaterialCode());
        if (material == null) {
            throw new BusinessException("未找到对应物料主数据" + form.getMaterialCode());
        }

        List<BmfObject> moldList = bmfService.find(BmfClassNameConst.MOLD, Where.builder().restrictions(Collections.singletonList(
                Restriction.builder()
                        .attributeName("material")
                        .conjunction(Conjunction.AND)
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(material.getPrimaryKeyValue()))
                        .build()
        )).build());

        if (newFlag && !moldList.isEmpty() || !newFlag && moldList.size() > 1) {
            throw new BusinessException("同一个物料编码只能存在一个模具台账" + form.getMaterialCode());
        }

        bmfObject.put("material", material);
        // 类别带出反写
        BmfObject moldClassification = bmfService.findByUnique("moldClassification", "code", form.getMoldClassificationCode());
        if (moldClassification == null) {
            throw new BusinessException("未找到对应类别主数据" + form.getMoldClassificationCode());
        }
        // 校验物料与模具类别是否关联
        String ledgerClassificationCode = material.getString("ledgerClassificationCode");
        String moldClassificationCode = moldClassification.getString("code");
        if (!StringUtils.equals(ledgerClassificationCode, moldClassificationCode)) {
            throw new BusinessException("该物料工器具类别[" + ledgerClassificationCode + "]与台账类别[" + moldClassificationCode + "]不匹配");
        }
        bmfObject.put("moldClassification", moldClassification);
        bmfObject.put("oaCreateTime", form.getOaCreateTime());
        if (StringUtils.isNotBlank(form.getWarehouseCode())) {
            BmfObject warehouse = bmfService.findByUnique("warehouse", "code", form.getWarehouseCode());
            if (warehouse == null) {
                throw new BusinessException("未找到对应仓库主数据" + form.getWarehouseCode());
            }
            bmfObject.put("warehouse", warehouse);
        }
        // 模具
        List<Map<String, Object>> moldCaveNumberMaps = form.getMoldCaveNumbers();
        List<JSONObject> moldCaveNumbers = new ArrayList<>();
        if (moldCaveNumberMaps != null) {
            for (Map<String, Object> map : moldCaveNumberMaps) {
                JSONObject moldCaveNumber = new JSONObject();
                moldCaveNumber.put("caveNumber", map.get("caveNumber"));
                moldCaveNumber.put("type", map.get("type"));
                moldCaveNumber.put("status", map.get("status"));
                moldCaveNumber.put("standardLife", map.get("standardLife"));

                if (map.get("sparePartsCode") != null && StringUtils.isNotBlank(map.get("sparePartsCode").toString())) {
                    BmfObject spareParts = bmfService.findByUnique("spareParts", "code", map.get("sparePartsCode").toString());
                    if (spareParts == null) {
                        throw new BusinessException("未找到对应备品备件" + map.get("sparePartsCode").toString());
                    }
                    moldCaveNumber.put("spareParts", spareParts);
                }
                moldCaveNumbers.add(moldCaveNumber);
            }
        }
        bmfObject.put("moldCaveNumbers", moldCaveNumbers);
        if (newFlag) {
            bmfObject.put("name", material.get("name"));
            bmfObject.put("buGroup", material.get("ext_buGroup"));
            bmfObject.put("responsibleDept", material.getAndRefreshBmfObject("responsibleDept"));
            bmfObject.put("responsibleUser", material.getAndRefreshBmfObject("responsibleUser"));
        }
        bmfObject.put("checkStatus", form.getCheckStatus());
        bmfObject.put("drawingNumber", form.getDrawingNumber());
        bmfObject.put("processNo", form.getProcessNo());
        bmfObject.put("procedureNo", form.getProcedureNo());
        if (StringUtils.isNotBlank(form.getName())) {
            bmfObject.put("name", form.getName());
        }
        if (StringUtils.isNotBlank(form.getBuGroup())) {
            bmfObject.put("buGroup", form.getBuGroup());
        }
        if (StringUtils.isNotBlank(form.getFixedAssetCode())) {
            bmfObject.put("fixedAssetCode", form.getFixedAssetCode());
        }
        if (!ObjectUtils.isEmpty(form.getProductionDate())) {
            bmfObject.put("productionDate", form.getProductionDate());
        }
        if (StringUtils.isNotBlank(form.getBrand())) {
            bmfObject.put("brand", form.getBrand());
        }
        if (!ObjectUtils.isEmpty(form.getResponsibleDeptId())) {
            BmfObject department = bmfService.find("department", form.getResponsibleDeptId());
            if (department == null) {
                throw new BusinessException("未找到对应责任部门" + form.getResponsibleDeptId());
            }
            bmfObject.put("responsibleDept", department);
        }
        if (StringUtils.isNotBlank(form.getResponsibleUserCode())) {
            BmfObject user = bmfService.findByUnique("user", "code", form.getResponsibleUserCode());
            if (user == null) {
                throw new BusinessException("未找到对应责任人" + form.getResponsibleUserCode());
            }
            bmfObject.put("responsibleUser", user);
        }
        if (StringUtils.isNotBlank(form.getCustomerUserCode())) {
            BmfObject businessPartner = bmfService.findByUnique("businessPartner", "code", form.getCustomerUserCode());
            if (businessPartner == null) {
                throw new BusinessException("未找到对应关联客户" + form.getCustomerUserCode());
            }
            bmfObject.put("customer", businessPartner);
        }
        if (StringUtils.isNotBlank(form.getLocationClassification())) {
            bmfObject.put("locationClassification", form.getLocationClassification());
        }

        if (StringUtils.isNotBlank(form.getLocationCode())) {
            BmfObject location = bmfService.findByUnique("location", "code", form.getLocationCode());
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
        // 寿命可以修改测试根据当时原型来的
        if (!ObjectUtils.isEmpty(form.getLife())) {
            bmfObject.put("life", form.getLife());
        }
        if (StringUtils.isNotBlank(form.getManufacturerCode())) {
            BmfObject manufacturer = bmfService.findByUnique("businessPartner", "code", form.getManufacturerCode());
            if (manufacturer == null) {
                throw new BusinessException("未找到对应制造商" + form.getManufacturerCode());
            }
            bmfObject.put("manufacturer", manufacturer);
        }
        // 测试要求加当时是和前端页面对齐的****
        if (ObjectUtils.isEmpty(bmfObject.get("responsibleDept"))) {
            throw new BusinessException("责任部门不能为空");
        }
        if (ObjectUtils.isEmpty(bmfObject.get("responsibleUser"))) {
            throw new BusinessException("责任人不能为空");
        }
        // 判断bu组是否在枚举中
        if (StringUtils.isNotBlank(form.getBuGroup())) {
            BmfEnum bmfEnum = BmfEnumCache.getBmfEnum("buGroup");
            if (ObjectUtils.isEmpty(bmfEnum.getBmfEnumItemMap())) {
                throw new BusinessException("BU组枚举未找到");
            }
            if (com.baomidou.mybatisplus.core.toolkit.StringUtils.isBlank(bmfEnum.getBmfEnumItemMap().get(form.getBuGroup()))) {
                throw new BusinessException("非枚举内bu组");
            }
        }
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), bmfObject);
    }
}
