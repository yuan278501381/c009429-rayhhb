package groovy.aps

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.ValueUtil
import com.tengnat.dwork.modules.aps.domain.dto.ApsDto
import com.tengnat.dwork.modules.script.abstracts.ApsNodeGroovyClass

import java.util.stream.Collectors

/**
 * 箱单排产-根据计划完成时间排序
 */
class ApsSortBoxOrder extends ApsNodeGroovyClass {

    BmfService bmfService = SpringUtils.getBean(BmfService.class)


    // 定义优先级映射
    private static final Map<String, Integer> PRIORITY_MAP = [
            "SCYXJ-1": 1,
            "SCYXJ-2": 2,
            "SCYXJ-3": 3
    ]

    @Override
    List<ApsDto> runScript(List<ApsDto> nodeData) {
        List<Object> codes = nodeData.stream().map(apsDto -> apsDto.getCode()).collect(Collectors.toList())
        Map<String, BmfObject> boxOrderGroup = this.bmfService.find("boxOrder", Collections.singletonList(
                Restriction.builder().conjunction(Conjunction.AND).attributeName("code").operationType(OperationType.IN).values(codes).build()
        )).stream().collect(Collectors.toMap(bmfObject -> bmfObject.getString("code"), bmfObject -> bmfObject))
        List<Object> productCodes = boxOrderGroup.values().stream().map(bmfObject1 -> bmfObject1.getString("productOrderCode")).collect(Collectors.toList())
        Map<String, BmfObject> productOrderGroup = this.bmfService.find("productOrder", Collections.singletonList(
                Restriction.builder().conjunction(Conjunction.AND).attributeName("code").operationType(OperationType.IN).values(productCodes).build()
        )).stream().collect(Collectors.toMap(bmfObject -> bmfObject.getString("code"), bmfObject -> bmfObject))
        //计划结束时间正序优先顺序倒序
        List<ApsDto> result = nodeData.stream().sorted(
                Comparator.comparing(ApsDto::getPlanEndTime)
                        .thenComparing(apsDto -> {
                            BmfObject sourceBoxOrder = boxOrderGroup.get(apsDto.getCode())
                            if (sourceBoxOrder == null) {
                                throw new BusinessException("找不到箱单数据，编码为：" + apsDto.getCode())
                            }
                            BmfObject sourceProductOrder = productOrderGroup.get(sourceBoxOrder.getString("productOrderCode"))
                            if (sourceProductOrder == null) {
                                throw new BusinessException("找不到订单数据，编码为：" + sourceBoxOrder.getString("productOrderCode"))
                            }
                            ValueUtil.toInt(PRIORITY_MAP.get(sourceProductOrder.getString("ext_priority")), 4)
                        }, Comparator.naturalOrder())
        ).collect(Collectors.toList())
        for (final def node in result) {
            node.setFlag(false)
        }
        return result
    }
}