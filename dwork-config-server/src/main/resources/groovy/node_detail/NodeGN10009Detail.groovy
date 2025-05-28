package groovy.node_detail

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.enums.OperateSourceEnum
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils

import java.util.stream.Collectors

/**
 * 销售发货任务 - 详情
 * 带出周转箱 - 包含拆箱的
 */
class NodeGN10009Detail extends NodeGroovyClass {

    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        String passBoxRealCodes = nodeData.getString("ext_pass_box_real_codes")
        if (StringUtils.isBlank(passBoxRealCodes)) {
            throw new BusinessException("没有周转箱信息")
        }
        Set<String> passBoxRealCodeSet = Arrays.stream(StringUtils.split(passBoxRealCodes, ","))
                .map(String::trim)
                .collect(Collectors.toSet())
        List<BmfObject> passBoxReals = this.sceneGroovyService.getPassBoxRealTargetPassBox(new ArrayList<>(passBoxRealCodeSet), OperateSourceEnum.UNPACKING_PASS_BOX)
        for (final def passBoxReal in passBoxReals) {
            passBoxReal.remove("id")
            passBoxReal.getAndRefreshBmfObject("quantityUnit")
        }
        nodeData.put("passBoxes", passBoxReals)
        return nodeData
    }
}
