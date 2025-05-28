package groovy.produce

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.tengnat.dwork.common.utils.BigDecimalUtils
import com.tengnat.dwork.modules.manufacture.domain.BoxOrderFunc
import com.tengnat.dwork.modules.manufacturev2.domain.DomainBoxOrder
import com.tengnat.dwork.modules.manufacturev2.domain.DomainTask
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ReportResp
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.script.abstracts.ProduceControlGroovyClass

/**
 * 报工控制
 * 报工箱单完成情况下自动完成
 */
class ProduceControlReportAutoComplete extends ProduceControlGroovyClass {

    @Override
    void submit(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject, Object result) {
        DomainTask domainTask = new DomainTask()
        BmfObject taskItemBmfObject = ((ReportResp) result).getTaskInfo()
        boolean lastProcess = new DomainBoxOrder().isLastProcessTask(taskItemBmfObject)
        if (!lastProcess) {
            return
        }
        BmfObject boxOrder = domainTask.getBoxOrderBmfObject(taskItemBmfObject)
        BigDecimal planQuantity = BigDecimalUtils.get(boxOrder.getBigDecimal("planQuantity"))
        BigDecimal reportingQuantity = BigDecimalUtils.get(boxOrder.getBigDecimal("reportingQuantity"))
        if (planQuantity != reportingQuantity) {
            return
        }
        BoxOrderFunc func = new BoxOrderFunc()
        func.complete(Arrays.asList(boxOrder), true)
    }
}