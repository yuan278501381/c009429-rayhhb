package groovy.node_scan

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils

/**
 * 库存转储-扫描脚本
 */

class NodeGN10011Scan extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //返回实体
        DomainScanResult result = new DomainScanResult()
        //周转箱校验
        if (StringUtils.isNotBlank(nodeData.getString("codeBmfClass"))&& "passBox" == nodeData.getString("codeBmfClass")) {
            BmfObject passBoxReal = basicGroovyService.findOne(BmfClassNameConst.PASS_BOX_REAL, "passBoxCode", nodeData.getString("code"))
            if (passBoxReal != null) {
                //校验仓库
                String warehouseCode = passBoxReal.getString("warehouseCode")
                if (warehouseCode != nodeData.getString("ext_source_warehouse_code")) {
                    throw new BusinessException("请扫描对应仓库的周转箱,仓库编码"+nodeData.getString("ext_source_warehouse_code") )
                }
                //校验物料
                String materialCode = passBoxReal.getString("materialCode")
                if (materialCode != nodeData.getString("ext_material_code")) {
                    throw new BusinessException("请扫描相同物料的周转箱,物料编码"+nodeData.getString("ext_material_code") )
                }
            }
        }
        //校验位置
        if (StringUtils.isNotBlank(nodeData.getString("codeBmfClass"))&& "location" == nodeData.getString("codeBmfClass")) {
            def warehouseInfo = sceneGroovyService.getWarehouseByLocation(nodeData.getString("code"))
            if (ObjectUtils.isEmpty(warehouseInfo) || ObjectUtils.notEqual(warehouseInfo.get("warehouseCode"), nodeData.getString("ext_target_warehouse_code"))) {
                return result.fail("扫描的" + nodeData.getString("code") + "不属于目标仓库")
            }
        }
        return result.success()
    }
}
