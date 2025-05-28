package groovy.common

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.enums.EquipSourceEnum
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.ObjectUtils
import org.springframework.util.CollectionUtils

/**
 * 更新周转箱实时位置信息
 */
class NodeUpdatePassBoxRealLocation extends NodeGroovyClass {

    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    BmfObject runScript(BmfObject nodeData) {
        //更新周转箱位置信息
        updatePassBoxReal(nodeData)
        return nodeData
    }

    //更新周转箱位置信息
    void updatePassBoxReal(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        if (CollectionUtils.isEmpty(nodeData.getList("passBoxes"))) {
            throw new BusinessException("更新周转箱实时位置失败,周转箱不能为空")
        }
        BmfObject location = basicGroovyService.getByCode("location", nodeData.getString("targetLocationCode"))
        if (location == null) {
            throw new BusinessException("更新周转箱实时位置失败,编码：" + nodeData.getString("targetLocationCode"))
        }
        if (location != null) {
            List<BmfObject> passBoxReals = new ArrayList<>()
            for (final def passBox in passBoxes) {
                BmfObject passBoxReal = basicGroovyService.findOne("passBoxReal", "passBoxCode", passBox.getString("passBoxCode"))
                if (passBoxReal == null) {
                    continue
                }
                passBoxReal.put("locationCode", location.getString("code"))
                passBoxReal.put("locationName", location.getString("name"))
                passBoxReal.put("location", location)
                //置入批次号
                if (ObjectUtils.isNotEmpty(nodeData.getString("ext_batch_code")) && ObjectUtils.isEmpty(nodeData.getString("batchCode"))) {
                    passBoxReal.put("batchCode", nodeData.getString("ext_batch_code"))
                }
                passBoxReals.add(passBoxReal)
            }
            sceneGroovyService.batchSynchronizePassBoxInfo(passBoxReals, EquipSourceEnum.PDA.getCode(), nodeData.getBmfClassName())
        }
    }
}
