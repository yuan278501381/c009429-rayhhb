package groovy.produce

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.basic_data.mapper.BoxOrderMapperV2
import com.tengnat.dwork.modules.manufacturev2.domain.DomainInstanceTask
import com.tengnat.dwork.modules.manufacturev2.domain.DomainTask
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ValidateRuleResult
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.manufacturev2.domain.form.SceneMeasuringToolForm
import com.tengnat.dwork.modules.script.abstracts.ProduceValidateGroovyClass
import org.apache.commons.collections.CollectionUtils

/**
 * 生产准备 - 登记量检具
 * 登记量检具与任务匹配
 * 当前任务关联的工序可用量检具/量检具类别与当前登记的量具符合
 * 请登记自检需要的量检检具
 */
class ProduceValidateProcessMeasuringToolLogin extends ProduceValidateGroovyClass {

    BmfService bmfService = SpringUtils.getBean(BmfService.class)

    BoxOrderMapperV2 boxOrderMapper = SpringUtils.getBean(BoxOrderMapperV2.class)

    @Override
    ValidateRuleResult login(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject) {
        def stationTasks = new DomainInstanceTask(stationReal).getStationTasks()
        def taskItemBmfObjects = new DomainTask().getTaskItemBmfObjects(stationTasks)
        def resources = jsonObject.getJSONArray("resources").toJavaList(SceneMeasuringToolForm.BatchForm.class)
        for (def resource : resources) {
            for (def taskItemBmfObject : taskItemBmfObjects) {
                def measuringToolCode = resource.getMeasuringToolCode()
                def measuringTool = this.bmfService.findByUnique("measuringTool", "code", measuringToolCode)
                if (measuringTool == null) {
                    throw new BusinessException("量检具[" + measuringToolCode + "]信息不存在")
                }
                BmfObject boxOrderProcessProduceTask = taskItemBmfObject.getBmfObject("boxOrderProcessProduceTask")
                BmfObject boxOrderProcess = boxOrderProcessProduceTask.getBmfObject("boxOrderProcess")
                def bindingMeasuringCodes = boxOrderMapper.selectBoxOrderProcessResourceCode(boxOrderProcess.getPrimaryKeyValue(), "measuringTool")
                if (CollectionUtils.isEmpty(bindingMeasuringCodes)) {
                    def bindingMeasuringClassCodes = boxOrderMapper.selectBoxOrderProcessResourceCode(boxOrderProcess.getPrimaryKeyValue(), "measuringToolClassification")
                    if (CollectionUtils.isEmpty(bindingMeasuringClassCodes)) {
                        continue
                    }
                    def measuringToolClassification = measuringTool.getAndRefreshBmfObject("measuringToolClassification")
                    if (measuringToolClassification == null) {
                        throw new BusinessException("请登记自检需要的量检具")
                    }
                    if (!bindingMeasuringClassCodes.contains(measuringToolClassification.getString("code"))) {
                        throw new BusinessException("请登记自检需要的量检具")
                    }
                }
            }
        }
        return ValidateRuleResult.success()
    }
}
