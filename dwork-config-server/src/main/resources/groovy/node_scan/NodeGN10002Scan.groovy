package groovy.node_scan

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.StringUtils

/**
 * 出库任务-扫描脚本
 */

class NodeGN10002Scan extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //返回实体
        DomainScanResult result = new DomainScanResult()

        if (StringUtils.isNotBlank(nodeData.getString("codeBmfClass"))&& "passBox" == nodeData.getString("codeBmfClass")) {
            BmfObject passBoxReal = basicGroovyService.findOne(BmfClassNameConst.PASS_BOX_REAL, "passBoxCode", nodeData.getString("code"))
            if (passBoxReal != null) {
                String warehouseCode = passBoxReal.getString("warehouseCode")
                if (warehouseCode != nodeData.getString("ext_source_warehouse_code")) {
                    throw new BusinessException("请扫描对应仓库的周转箱,仓库编码"+nodeData.getString("ext_source_warehouse_code") )
                }
            }
        }
        return result.success()
    }
}
