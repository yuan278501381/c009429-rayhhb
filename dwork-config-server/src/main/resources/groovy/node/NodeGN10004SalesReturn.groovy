package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.common.utils.BusinessUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

import java.util.stream.Collectors

/**
 * 入库任务-反写销售退货申请单
 */
class NodeGN10004SalesReturn extends NodeGroovyClass {
    BusinessUtils businessUtils = SpringUtils.getBean(BusinessUtils.class)
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        String sourceDocumentType = nodeData.getString("sourceDocumentType")
        if (sourceDocumentType != "salesReturnApplicant") return nodeData
        //入库单
        BmfObject warehousingOrder = nodeData.getBmfObject("_result_order")
        if (warehousingOrder == null) return nodeData
        //入库单详情
        List<BmfObject> warehousingOrderDetails = warehousingOrder.getList("warehousingOrderDetailIdAutoMapping")
        //根据上级内部单据分组
        Map<String, List<BmfObject>> groupInfos = warehousingOrderDetails
                .stream()
                .collect(Collectors.groupingBy { it -> ((BmfObject) it).getString("preDocumentCode") })
        for (String preDocumentCode : groupInfos.keySet()) {
            List<BmfObject> groupDetail = groupInfos.get(preDocumentCode)
            //根据行号和数量
            Map<String, BigDecimal> lineNumQuantityMap = groupDetail.stream().collect(Collectors.toMap({ it -> ((BmfObject) it).getString("lineNum") }, { it -> ((BmfObject) it).getBigDecimal("quantity") }))
            //行号集合
            List<String> lineNumList = groupDetail.stream().map({ it -> ((BmfObject) it).getString("lineNum") }).collect(Collectors.toList())
            //销售退货申请单
            BmfObject salesReturnApplicant = basicGroovyService.getByCode("salesReturnApplicant", preDocumentCode)
            if (salesReturnApplicant == null) {
                throw new BusinessException("销售退货申请单为空，编码:" + preDocumentCode)
            }
            List<BmfObject> salesReturnDetails = salesReturnApplicant.getAndRefreshList("salesReturnApplicantDetailIdAutoMapping")
            //过滤物料编码和行号
            List<BmfObject> filterSalesReturnDetails = salesReturnDetails.stream().filter({ it -> (lineNumList.contains(it.getString("lineNum")) && warehousingOrder.getString("materialCode") == it.getString("materialCode")) }).collect(Collectors.toList())
            filterSalesReturnDetails.forEach({
                //已分配数量
                it.put("warehousingQuantity", it.getBigDecimal("warehousingQuantity").add(lineNumQuantityMap.get(it.getString("lineNum"))))
                //未分配数量
                BigDecimal waitQuantity=it.getBigDecimal("waitQuantity")
                if (waitQuantity>0){
                    it.put("waitQuantity", waitQuantity.subtract(lineNumQuantityMap.get(it.getString("lineNum"))))
                }

            })
            //批量保存退货明细
            basicGroovyService.updateByPrimaryKeySelective(filterSalesReturnDetails)

            //刷新最新的退货明细
            salesReturnDetails = salesReturnApplicant.getAndRefreshList("salesReturnApplicantDetailIdAutoMapping")
            boolean isAllDone = salesReturnDetails.stream().allMatch({ it -> (it.getBigDecimal("waitQuantity") == BigDecimal.ZERO&& it.getBigDecimal("noReceivedQuantity") == BigDecimal.ZERO)})
            if (isAllDone) {
                businessUtils.updateStatus(salesReturnApplicant, DocumentStatusEnum.COMPLETED.getCode())
            }
        }
        return nodeData
    }


}
