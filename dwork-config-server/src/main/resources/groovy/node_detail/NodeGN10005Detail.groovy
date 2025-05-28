package groovy.node_detail

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

/**
 * 采购收货任务-详情
 * 带出包装方案
 * @author angel.su
 * createTime 2025/5/6 09:35
 */
class NodeGN10005Detail extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        nodeData.put("ext_current_received_quantity", BigDecimal.ZERO)

        String materialCode = nodeData.getString("ext_material_code")
        BmfObject material = basicGroovyService.getByCode("material", materialCode)
        if (material == null) return nodeData

        Map<String, Object> params = new HashMap<>()
        params.put("material", material.getPrimaryKeyValue())
        params.put("status", true)
        params.put("defaultStatus", true)
        BmfObject packScheme = basicGroovyService.findOne("packScheme",params)
        if (packScheme != null) {
            nodeData.put("ext_scheme_name", packScheme.getString("name"))
            nodeData.put("ext_scheme_code", packScheme.getString("code"))
            nodeData.put("ext_single_box_quantity", packScheme.getBigDecimal("packageQuantity"))
        }
        return nodeData
    }
}
