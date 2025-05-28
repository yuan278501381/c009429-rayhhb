package groovy.produce

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.manufacturev2.domain.DomainInstanceTask
import com.tengnat.dwork.modules.manufacturev2.domain.DomainNodeCommon
import com.tengnat.dwork.modules.manufacturev2.domain.DomainPassBox
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ValidateRuleResult
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.manufacturev2.domain.form.SceneNodeLoadPassBoxForm
import com.tengnat.dwork.modules.script.abstracts.ProduceValidateGroovyClass
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils

/**
 * 生产准备 - 上料登记
 * 登记上料箱物料为BOM物料
 * 不是BOM物料，无法登记
 * 验证当前周转箱内的物料是否是当前小箱单的工序所需物料
 */
class ProduceValidateLoginBom extends ProduceValidateGroovyClass {

    @Override
    ValidateRuleResult submit(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject) {
        String passBoxCode = jsonObject.getJSONArray("resources")
                .toJavaList(SceneNodeLoadPassBoxForm.BatchForm.class)
                .stream()
                .map { it -> it.getPassBoxCode() }
                .findFirst()
                .orElse(null)
        if (StringUtils.isBlank(passBoxCode)) {
            return ValidateRuleResult.success()
        }
        DomainPassBox domainPassBox = new DomainPassBox()
        BmfObject passBoxReal = domainPassBox.getPassBoxReal(passBoxCode)
        if (passBoxReal == null) {
            return ValidateRuleResult.success()
        }
        if (StringUtils.isBlank(passBoxReal.getString("materialCode"))) {
            return ValidateRuleResult.success()
        }
        List<BmfObject> taskItemBmfObjects = new DomainInstanceTask(stationReal).getTaskItemBmfObjects()
        if (CollectionUtils.isEmpty(taskItemBmfObjects)) {
            return ValidateRuleResult.success()
        }
        // 任务所需要的在制品/原材料/返工物料
        Set<String> tasksNeedMaterialCodes = new DomainNodeCommon().getTasksNeedMaterialCodes(taskItemBmfObjects)
        if (CollectionUtils.isEmpty(tasksNeedMaterialCodes)) {
            return ValidateRuleResult.success()
        }
        if (!tasksNeedMaterialCodes.contains(passBoxReal.getString("materialCode"))) {
            throw new BusinessException("请登记任务所需的物料")
        }
        return ValidateRuleResult.success()
    }
}