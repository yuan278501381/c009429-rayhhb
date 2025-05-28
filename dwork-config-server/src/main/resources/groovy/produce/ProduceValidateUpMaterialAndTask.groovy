package groovy.produce

import cn.hutool.core.collection.CollectionUtil
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.manufacturev2.domain.*
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ValidateRuleResult
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkContinuousProcessRecord
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationRealPassBox
import com.tengnat.dwork.modules.script.abstracts.ProduceValidateGroovyClass
import org.apache.commons.collections4.CollectionUtils

/**
 * 开始生产 - 上料校验
 * 上料箱物料是否为任务所需物料
 * 上料校验周转箱的物料和任务与当前任务是否匹配
 * 任务不一致/物料不符合
 */
class ProduceValidateUpMaterialAndTask extends ProduceValidateGroovyClass {

    @Override
    ValidateRuleResult nodeData(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject) {
        List<BmfObject> taskItemBmfObjects = new DomainTask().getTaskItemBmfObjects(new DomainInstanceTask(stationReal).getStationTasks())
        //任务不存在
        if (CollectionUtil.isEmpty(taskItemBmfObjects)) {
            return ValidateRuleResult.success()
        }
        DomainContinuousProcess domainContinuousProcess = new DomainContinuousProcess()

        for (final def taskItemBmfObject in taskItemBmfObjects) {
            boolean reworkProcessType = new DomainBoxOrder().isReworkProcessType(taskItemBmfObject)
            if (reworkProcessType) {
                // 返工工序不校验上料
                return ValidateRuleResult.success()
            }
            List<DwkContinuousProcessRecord> records = domainContinuousProcess.getContinuousProcessRecords(taskItemBmfObject.getPrimaryKeyValue())
            if (CollectionUtils.isNotEmpty(records) && records.get(0).getTaskItemId() != taskItemBmfObject.getPrimaryKeyValue()) {
                // 工序连续非首道，不校验上料
                return ValidateRuleResult.success()
            }
        }

        def domainPassBox = new DomainPassBox()
        def domainProduceRule = new DomainProduceRule()
        def domainInstancePassBox = new DomainInstancePassBox(stationReal)
        List<DwkStationRealPassBox> upPassBoxes = domainInstancePassBox.getStationUpPassBoxes()
        // 工位所有任务所需的物料集合
        Set<String> tasksNeedMaterialCodes = domainProduceRule.findTasksNeedMaterialCodes(stationReal)
        for (def resource : upPassBoxes) {
            def passBoxReal = domainPassBox.getPassBoxReal(resource.getPassBoxCode())
            if (passBoxReal == null || passBoxReal.getString("materialCode") == null) {
                continue
            }
            def materialCode = passBoxReal.getString("materialCode") as String
            // 周转箱物料是否是工位所需物料
            if (!tasksNeedMaterialCodes.contains(materialCode)) {
                throw new BusinessException("任务不一致/物料不符合")
            }
        }

        return ValidateRuleResult.success()
    }
}