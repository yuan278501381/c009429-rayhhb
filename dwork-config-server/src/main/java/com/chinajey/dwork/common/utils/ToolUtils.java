package com.chinajey.dwork.common.utils;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.utils.ValueUtil;
import com.tengnat.dwork.common.enums.EquipSourceEnum;
import com.tengnat.dwork.common.enums.OperateSourceEnum;
import com.tengnat.dwork.modules.basic_data.service.PassBoxService;
import com.tengnat.dwork.modules.manufacture.domain.form.PackingPassBoxForm;
import com.tengnat.dwork.modules.manufacture.service.PassBoxRealService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

@Component
public class ToolUtils {

    @Resource
    private PassBoxService passBoxService;
    @Resource
    private PassBoxRealService passBoxRealService;
    @Resource
    private BmfService bmfService;


    public void generatePassBox(BmfObject bmfObject, String loadMaterialType, String operateSource, JSONObject location) {
        JSONObject passBoxJson = new JSONObject();
        passBoxJson.put("name", this.passBoxService.getName(1L));
        passBoxJson.put("code", bmfObject.getString("code"));
        passBoxJson.put("status", true);
        passBoxJson.put("passBoxClassification", 1);
        passBoxJson.put("passBoxClassificationAttribute", 3);
        if (location != null) {
            passBoxJson.put("location", location);
            passBoxJson.put("locationCode", location.getString("code"));
            passBoxJson.put("locationName", location.getString("name"));
        }

        BmfObject newPassBox = this.passBoxService.create(passBoxJson);
        bmfObject.put("passBox", newPassBox);
        PackingPassBoxForm form = new PackingPassBoxForm();
        form.setPassBoxCode(newPassBox.getString("code"));
        BmfObject material = this.bmfService.findByUnique("material", "code", bmfObject.getJSONObject("material").getString("code"));
        if (material != null) {
            form.setMaterialId(material.getPrimaryKeyValue());
            form.setMaterialCode(material.getString("code"));
            form.setMaterialName(material.getString("name"));
            if (material.getAndRefreshBmfObject("flowUnit") != null) {
                form.setQuantityUnit(material.getAndRefreshBmfObject("flowUnit").getPrimaryKeyValue());
            }
        }

        form.setLoadMaterialType(loadMaterialType);
        form.setQuantity(new BigDecimal(1));
        form.setResourceCode(bmfObject.getBmfObject("responsibleUser").getString("code"));
        form.setResourceName(bmfObject.getBmfObject("responsibleUser").getString("name"));
        form.setResourceTypeCode("user");
        form.setIsOther(true);
        BmfObject passBoxReal = this.passBoxRealService.packing(form);
        passBoxReal.put("loadMaterialType", loadMaterialType);
        passBoxReal.put("operateSourceType", operateSource);
        passBoxReal.put("isLedger", true);
        if (location != null) {
            passBoxReal.put("location", location);
            passBoxReal.put("locationCode", location.getString("code"));
            passBoxReal.put("locationName", location.getString("name"));
        }

        OperateSourceEnum operateSourceEnum = OperateSourceEnum.valueOf(operateSource);
        this.passBoxRealService.synchronizePassBoxInfo(passBoxReal, EquipSourceEnum.PC, operateSourceEnum);
        bmfObject.put("code", newPassBox.getString("code"));
    }

    /**
     * 处理库存
     *
     * @param bmfObject
     */
    public void inventoryUpdate(BmfObject bmfObject) {
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

}
