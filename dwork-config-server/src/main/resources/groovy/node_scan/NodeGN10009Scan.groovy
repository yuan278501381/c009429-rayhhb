package groovy.node_scan

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.enums.OperateSourceEnum
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils

import java.util.stream.Collectors

/**
 * 销售发货任务 - 扫描控制
 */
class NodeGN10009Scan extends NodeGroovyClass {

    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        DomainScanResult result = new DomainScanResult()
        if (nodeData.getString("model") != "materialRelevancePassBox") {
            return result.success()
        }
        String passBoxRealCodes = nodeData.getString("ext_pass_box_real_codes")
        Set<String> passBoxRealCodeSet = Arrays.stream(StringUtils.split(passBoxRealCodes, ","))
                .map(String::trim)
                .collect(Collectors.toSet())
        List<BmfObject> passBoxReals = this.sceneGroovyService.getPassBoxRealTargetPassBox(new ArrayList<>(passBoxRealCodeSet), OperateSourceEnum.UNPACKING_PASS_BOX)
        Set<String> passBoxCodeSet = passBoxReals
                .stream()
                .map { it -> it.getString("passBoxCode") }
                .collect(Collectors.toSet())
        if (!passBoxCodeSet.contains(nodeData.getString("code"))) {
            throw new BusinessException("请扫描对应出库的周转箱")
        }
        return result.success()
    }
}
