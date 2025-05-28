package groovy.produce

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.manufacturev2.domain.DomainBoxOrder
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ToolSchemeValidate
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ValidateRuleResult
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.manufacturev2.domain.model.ToolModel
import com.tengnat.dwork.modules.manufacturev2.mapper.ProduceExecuteCommonMapper
import com.tengnat.dwork.modules.script.abstracts.ProduceValidateGroovyClass

import java.util.stream.Collectors

/**
 * 刀具是否符合工器具方案
 */
class ProduceValidateProcessResourceKnife extends ProduceValidateGroovyClass {

    static final String TOOL_TYPE = "knife"

    static final String TOOL_CLASSIFICATION = "knifeClassification"

    ProduceExecuteCommonMapper produceExecuteCommonMapper = SpringUtils.getBean(ProduceExecuteCommonMapper.class)

    @Override
    ValidateRuleResult nodeData(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject) {
        // 登记到工位的刀具
        List<ToolModel> toolModels = this.produceExecuteCommonMapper.selectStationTools(stationReal.getStationCode(), TOOL_TYPE)
        if (toolModels.size() == 0) {
            throw new BusinessException("必须登记刀具")
        }
        def domainBoxOrder = new DomainBoxOrder()
        ToolSchemeValidate validate = domainBoxOrder.getStationEquipmentProcessToolScheme(stationReal)
        // 工器具方案的刀具配置
        List<String> materialCodes = validate.getToolItems()
                .stream()
                .filter { item -> item.getToolType() == TOOL_CLASSIFICATION }
                .map { item -> item.getMaterialCode() }
                .collect(Collectors.toList())
        for (final def toolModel in toolModels) {
            if (!materialCodes.contains(toolModel.getMaterialCode())) {
                throw new BusinessException("确认机台刀具是否正确")
            }
        }
        return ValidateRuleResult.success()
    }
}