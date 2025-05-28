package groovy.node_detail

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

/**
 * 从迈金搬过来的
 *
 * 不合格评审详情脚本
 */
class NodeGN3503Detail extends NodeGroovyClass {
    def basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    BmfObject runScript(BmfObject nodeData) {
        String inspectionType = nodeData.getString("ext_inspection_type")
        String sourceType = nodeData.getString("ext_source_type")
        if ("来料检" == inspectionType && ("purchaseOrderTask" == sourceType || "outsourceReceiptPlan" == sourceType)) {
            //责任分类默认为 外部责任
            nodeData.put("ext_responsibility_classification", "external")
            nodeData.put("ext_responsible_dept_name", nodeData.getString("ext_business_partner_name"))
            nodeData.put("ext_responsible_dept_code", nodeData.getString("ext_business_partner_code"))
            basicGroovyService.updateByPrimaryKeySelective(nodeData);
        }
        return nodeData
    }
}
