package groovy.common

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.springframework.util.CollectionUtils

import java.util.stream.Collectors

/**
 * 创建物料库存信息
 */
class NodeCreateUtensilInventory extends NodeGroovyClass{

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    BmfObject runScript(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        if (CollectionUtils.isEmpty(passBoxes)){
            throw new BusinessException("创建物料库存信息失败，周转箱不能为空")
        }
        //根据物料的维度创建库存信息
        Map<String,List<BmfObject>> groupByMaterial = passBoxes.stream().collect(Collectors.groupingBy(p -> p.getString("materialCode")))
        for (String materialCode:groupByMaterial.keySet()){
            //生成库存信息
            BigDecimal reduce = groupByMaterial.get(materialCode).stream().map(passBox -> passBox.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
            createUtensilInventory(materialCode,reduce)
        }
        return nodeData
    }

    //库存信息生成
    void createUtensilInventory(String materialCode,BigDecimal quantity){
        //物料
        BmfObject material = basicGroovyService.getByCode(BmfClassNameConst.MATERIAL, materialCode)
        if (material==null){
            throw new BusinessException("物料主数据不存在,编码:"+materialCode)
        }
        if (quantity == null) {
            quantity = BigDecimal.ZERO
        }
        BmfObject utensilInventory = material.getAndRefreshBmfObject("utensilInventory")
        if (utensilInventory == null) {
            BmfObject save = new BmfObject("utensilInventory")
            save.put("quantity",quantity)
            save.put("receivedQuantity",BigDecimal.ZERO)
            save.put("inventoryQuantity",quantity)
            save.put("lockQuantity",BigDecimal.ZERO)
            save.put("material",material)
            basicGroovyService.saveOrUpdate(save)
            def update = new BmfObject(material.getBmfClassName())
            update.put("id", material.getPrimaryKeyValue())
            update.put("utensilInventory",save)
            basicGroovyService.updateByPrimaryKeySelective(update)
        }else {
            def update = new BmfObject(utensilInventory.getBmfClassName())
            update.put("id", utensilInventory.getPrimaryKeyValue())
            update.put("quantity",utensilInventory.getBigDecimal("quantity") == null?quantity:utensilInventory.getBigDecimal("quantity").add(quantity))
            update.put("inventoryQuantity",utensilInventory.getBigDecimal("inventoryQuantity") == null?quantity:utensilInventory.getBigDecimal("inventoryQuantity").add(quantity))
            basicGroovyService.updateByPrimaryKeySelective(update)
        }

    }
}
