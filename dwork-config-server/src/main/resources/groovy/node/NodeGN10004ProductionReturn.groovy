package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.ValueUtil
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.common.utils.WriteBackUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang.ObjectUtils

import java.util.stream.Collectors

/**
 * 入库任务-反写生产退料申请单
 */
class NodeGN10004ProductionReturn extends NodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    private static final String SOURCE_BMF_CLASS = "productionReturnApplicant"

    @Override
    Object runScript(BmfObject nodeData) {
        //入库单
        BmfObject warehousingOrder = nodeData.getBmfObject("_result_order")
        if (warehousingOrder == null) return nodeData
        writeBackPurchase(warehousingOrder)
        return nodeData
    }

    private void writeBackPurchase(BmfObject warehousingOrder) {
        //源头生产订单 且 业务类型生产退料
        String sourceDocumentType = warehousingOrder.getString("sourceDocumentType")
        String orderBusinessType = warehousingOrder.getString("orderBusinessType")
        if (!(ObjectUtils.equals("productOrder",sourceDocumentType)&&ObjectUtils.equals("productionReturn",orderBusinessType))) return

        //入库单明细
        List<BmfObject> warehousingOrderDetail = warehousingOrder.getList("warehousingOrderDetailIdAutoMapping")
        warehousingOrderDetail.forEach(detail -> {
            //行号集合 逗号分隔
            List<String> lineNumList = detail.getString("lineNum").split(",")
            //物料编码
            String materialCode = detail.getString("materialCode")
            //本次入库数量
            BigDecimal incQuantity = detail.getBigDecimal("quantity")

            //生产退料申请单编码-入库单上级
            String productionReturnApplicantCode = detail.getString("preDocumentCode")

            //生产退料申请单
            BmfObject productionReturnApplicant = basicGroovyService.getByCode(SOURCE_BMF_CLASS,productionReturnApplicantCode)
            if (productionReturnApplicant == null) {
                throw new BusinessException("生产退料申请单不存在,编码:" + productionReturnApplicantCode)
            }
            //生产退料申请单明细
            List<BmfObject> productionReturnApplicantDetailIdAutoMapping = productionReturnApplicant.getAndRefreshList("productionReturnApplicantDetailIdAutoMapping")
                    .stream()
                    .filter(it -> it.getString("materialCode") == materialCode && lineNumList.contains(it.getString("lineNum")))
                    .collect(Collectors.toList())
            if (productionReturnApplicantDetailIdAutoMapping.isEmpty()) {
                throw new BusinessException("生产退料申请单明细不存在,编码:" + productionReturnApplicantCode)
            }

            //-----------------反写生产退料申请单-----------------
            WriteBackUtils.writeBack(productionReturnApplicantDetailIdAutoMapping, incQuantity, "returnedQuantity", "waitReturnQuantity")
            //刷新明细
            productionReturnApplicantDetailIdAutoMapping = productionReturnApplicant.getAndRefreshList("productionReturnApplicantDetailIdAutoMapping")
            //调整生产退料申请单状态
            String documentStatus = DocumentStatusEnum.PARTIAL.getCode()
            boolean isAllDone =productionReturnApplicantDetailIdAutoMapping.stream().allMatch({ it -> ValueUtil.toBigDecimal(it.getBigDecimal("waitReturnQuantity"), new BigDecimal(0)) == 0 })
            if (isAllDone) {
                documentStatus = DocumentStatusEnum.COMPLETED.getCode()
            }
            productionReturnApplicant.put("documentStatus", documentStatus)
            basicGroovyService.updateByPrimaryKeySelective(productionReturnApplicant)
        })
    }
}
