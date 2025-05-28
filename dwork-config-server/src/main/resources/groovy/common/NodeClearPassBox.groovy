package groovy.common;

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.springframework.util.CollectionUtils

/**
 * 清空周转箱
 */
class NodeClearPassBox extends NodeGroovyClass{

    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    @Override
    BmfObject runScript(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        if (CollectionUtils.isEmpty(nodeData.getList("passBoxes"))){
            throw new BusinessException("清空周转箱失败，周转箱不能为空")
        }
        //清空周转箱信息
        sceneGroovyService.clearPassBox(passBoxes)
        return nodeData
    }
}
