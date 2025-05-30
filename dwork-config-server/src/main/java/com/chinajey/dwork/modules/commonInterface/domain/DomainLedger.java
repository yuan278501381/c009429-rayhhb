package com.chinajey.dwork.modules.commonInterface.domain;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.SpringUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class DomainLedger {

    BmfService bmfService = SpringUtils.getBean(BmfService.class);

    /**
     * 根据周转箱信息获取对应的台账信息
     */
    public BmfObject findAndValidateLedgerByPassBoxCode(String passBoxCode){
        BmfObject passBox = this.bmfService.findByUnique("passBox", "code", passBoxCode);
        if (passBox == null){
            throw new BusinessException("周转箱[" + passBoxCode + "]信息不存在");
        }
        BmfObject passBoxReal = this.bmfService.findByUnique("passBoxReal", "passBoxCode", passBoxCode);
        if (passBoxReal == null){
            throw new BusinessException("周转箱实时[" + passBoxCode + "]信息不存在");
        }
        if (!Boolean.TRUE.equals(passBoxReal.getBoolean("isLedger"))){
            throw new BusinessException("周转箱实时[" + passBoxReal.getString("code") + "]台账信息不存在");
        }
        List<String> values = Arrays.asList("3", "4", "5", "6", "7", "8");
        String loadMaterialType = passBoxReal.getString("loadMaterialType");
        if (StringUtils.isBlank(loadMaterialType) || !values.contains(loadMaterialType)){
            throw new BusinessException("台账[" + passBoxReal.getString("passBoxCode") + "]信息不存在");
        }
        String bmfClassName = null;
        if ("3".equals(loadMaterialType)){
            bmfClassName = "knife";
        }else if ("4".equals(loadMaterialType)){
            bmfClassName = "mold";
        }else if ("5".equals(loadMaterialType)){
            bmfClassName = "fixture";
        }else if ("6".equals(loadMaterialType)){
            bmfClassName = "measuringTool";
        }else if ("7".equals(loadMaterialType)){
            bmfClassName = "jig";
        }else if ("8".equals(loadMaterialType)){
            bmfClassName = "rack";
        }
        BmfObject bmfObject = this.bmfService.findByUnique(bmfClassName, "code", passBoxReal.getString("passBoxCode"));
        if (bmfObject == null){
            throw new BusinessException("台账[" + passBoxReal.getString("passBoxCode") + "]信息不存在");
        }
        bmfObject.putUncheck("tool_type", bmfClassName);
        return bmfObject;
    }

    /**
     * 通过台账对象修改某个数量
     * bmfObject：台账主数据
     * key：数量字段  receivedQuantity-领用数量  inventoryQuantity-库存数量  lockQuantity-锁定数量
     * quantity：数量加1 = 1，减1 = -1
     */
    public void updateUtensilInventoryByReal(BmfObject bmfObject, String key, BigDecimal quantity){
        BmfObject material = bmfObject.getBmfObject("material");
        if (ObjectUtils.isEmpty(material) || material.getPrimaryKeyValue() == null){
            throw new BusinessException("物料信息为空");
        }
        BmfObject utensilInventory = this.bmfService.findByUnique("utensilInventory", "material", material.getPrimaryKeyValue());
        if (utensilInventory == null){
            throw new BusinessException("工器具库存信息不存在");
        }
        BigDecimal lastQuantity = ValueUtil.toBigDecimal(utensilInventory.getBigDecimal(key), BigDecimal.ZERO).add(quantity);
        if (lastQuantity.compareTo(BigDecimal.ZERO) < 0){
            throw new BusinessException("数量不能小于0");
        }
        utensilInventory.put(key, lastQuantity);
        this.bmfService.updateByPrimaryKeySelective(utensilInventory);
    }
}
