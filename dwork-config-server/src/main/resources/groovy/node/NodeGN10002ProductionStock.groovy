package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

import java.util.stream.Collectors

class NodeGN10002ProductionStock extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        String sourceDocumentType = nodeData.getString("sourceDocumentType")
        if (sourceDocumentType != "productOrder") return nodeData
        //查询出库任务提交脚本生成的结果单据
        BmfObject outboundOrders = nodeData.getBmfObject("_result_order")
        if (outboundOrders == null) return nodeData
        List<BmfObject> outboundOrderDetails = outboundOrders.getList("outboundOrderDetailIdAutoMapping")
        Map<String, List<BmfObject>> groupInfos = outboundOrderDetails
                .stream()
                .collect(Collectors.groupingBy(it -> ((BmfObject) it).getString("preDocumentCode")))
        for (String preDocumentCode : groupInfos.keySet()) {
            BmfObject outboundApplication = basicGroovyService.findOne("outboundApplicant", "code", preDocumentCode)
            List<BmfObject> outboundApplicationDetails = outboundApplication.getAndRefreshList("outboundApplicantIdAutoMapping")
            BmfObject productionStock = basicGroovyService.findOne("productionStock", "code", outboundApplication.get("preDocumentCode"))
            List<BmfObject> productionStockDetails = productionStock.getAndRefreshList("productionStockAutoMapping")
            productionStockDetails.forEach({
                BmfObject outboundApplicationDetail = outboundApplicationDetails.find({ child -> child.get("materialCode") == it.get("materialCode") })
                if (outboundApplicationDetail != null) {
                    it.put("waitQuantity", outboundApplicationDetail.getBigDecimal("waitQuantity"))
                    it.put("outboundQuantity", outboundApplicationDetail.getBigDecimal("outboundQuantity"))
                    it.put("readyQuantity", outboundApplicationDetail.getBigDecimal("outboundQuantity"))
                    it.put("unReadyQuantity", outboundApplicationDetail.getBigDecimal("waitQuantity"))
                    basicGroovyService.updateByPrimaryKeySelective(it)
                }
            })
            Boolean flag = productionStockDetails.stream().allMatch { child -> child.getBigDecimal("unReadyQuantity") == 0 }
            productionStock.put("documentStatus", flag ? "completed" : "partial")
            basicGroovyService.saveOrUpdate(productionStock)
        }
        return nodeData
    }
}
