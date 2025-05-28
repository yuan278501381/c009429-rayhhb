package groovy.node_scan

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.enums.WlStyleModel
import com.tengnat.dwork.common.utils.PassBoxUtils
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeScanGroovyClass

/**
 * 通用扫描脚本 - 物料关联周转箱套件只能扫描空箱
 */
class NodeScanMustEmptyPassBox extends NodeScanGroovyClass {

    BmfService bmfService = SpringUtils.getBean(BmfService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {
        DomainScanResult result = new DomainScanResult()
        if (nodeData.getString("model") != WlStyleModel.MaterialRelevancePassBox.getCode()) {
            return result.success()
        }
        String passBoxCode = nodeData.getString("code")
        BmfObject passBoxReal = this.bmfService.findByUnique("passBoxReal", "passBoxCode", passBoxCode)
        if (PassBoxUtils.isEmptyPassBox(passBoxReal)) {
            return result.success()
        }
        throw new BusinessException("请扫描空箱")
    }
}
