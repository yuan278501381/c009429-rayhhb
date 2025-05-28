package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.script.exception.ScriptInterruptedException
import com.chinajey.dwork.common.utils.WriteBackUtils
import com.chinajey.dwork.modules.purchaseOrder.service.PurchaseOrderService
import com.chinajey.dwork.modules.purchaseReceipt.service.PurchaseReceiptService
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.common.utils.BigDecimalUtils

import java.util.stream.Collectors

/**
 * 入库任务提交反写采购收货计划、采购订单
 * @author angel.su
 * createTime 2025/4/11 09:18
 */
class NodeGN10004PurchaseReceipt extends NodeGroovyClass {
    BmfService bmfService = SpringUtils.getBean(BmfService.class)
    PurchaseOrderService purchaseOrderService = SpringUtils.getBean(PurchaseOrderService.class)
    PurchaseReceiptService purchaseReceiptService = SpringUtils.getBean(PurchaseReceiptService.class)

    private static final String SOURCE_BMF_CLASS = "purchaseOrder"
    private static final String BMF_CLASS = "purchaseReceipt"

    @Override
    Object runScript(BmfObject nodeData) {
        //入库单
        BmfObject warehousingOrder = nodeData.getBmfObject("_result_order")
        if (warehousingOrder == null) return nodeData
        writeBackPurchase(warehousingOrder)
        return nodeData
    }

    private void writeBackPurchase(BmfObject warehousingOrder) {
        //源头单据类型-采购订单
        String sourceDocumentType = warehousingOrder.getString("sourceDocumentType")
        if (SOURCE_BMF_CLASS != sourceDocumentType) return

        //入库单明细
        List<BmfObject> warehousingOrderDetail = warehousingOrder.getList("warehousingOrderDetailIdAutoMapping")
        warehousingOrderDetail.forEach(detail -> {
            //行号集合 逗号分隔
            List<String> lineNumList = detail.getString("lineNum").split(",")
            //物料编码
            String materialCode = detail.getString("materialCode")
            //本次入库数量
            BigDecimal incQuantity = detail.getBigDecimal("quantity")

            //采购收货计划编码-入库单上级
            String purchaseReceiptCode = detail.getString("preDocumentCode")
            //采购收货计划
            BmfObject purchaseReceipt = bmfService.findByUnique(BMF_CLASS, "code", purchaseReceiptCode)
            if (purchaseReceipt == null) {
                throw new ScriptInterruptedException("采购收货计划不存在,编码:" + purchaseReceiptCode)
            }
            //采购收货计划明细
            List<BmfObject> purchaseReceiptIdAutoMapping = purchaseReceipt.getAndRefreshList("purchaseReceiptIdAutoMapping")
                    .stream()
                    .filter(it -> it.getString("materialCode") == materialCode && lineNumList.contains(it.getString("lineNum")))
                    .collect(Collectors.toList())
            if (purchaseReceiptIdAutoMapping.isEmpty()) {
                throw new ScriptInterruptedException("采购收货计划明细不存在,编码:" + purchaseReceiptCode)
            }

            //采购订单编码-入库单源头
            String purchaseOrderCode = detail.getString("sourceDocumentCode")
            //采购订单
            BmfObject purchaseOrder = bmfService.findByUnique(SOURCE_BMF_CLASS, "code", purchaseOrderCode)
            if (purchaseOrder == null) {
                throw new ScriptInterruptedException("采购订单不存在,编码:" + purchaseOrderCode)
            }
            //采购订单明细
            List<BmfObject> purchaseOrderDetailIdAutoMapping = purchaseOrder.getAndRefreshList("purchaseOrderDetailIdAutoMapping")
                    .stream()
                    .filter(it -> it.getString("materialCode") == materialCode && lineNumList.contains(it.getString("lineNum")))
                    .collect(Collectors.toList())
            if (purchaseOrderDetailIdAutoMapping.isEmpty()) {
                throw new ScriptInterruptedException("采购订单明细不存在,编码:" + purchaseOrderCode)
            }

            //-----------------反写采购收货计划-----------------
            WriteBackUtils.writeBack(purchaseReceiptIdAutoMapping, incQuantity, "warehousedQuantity", "waitWarehousedQuantity")

            //-----------------反写采购订单-----------------
            purchaseReceiptIdAutoMapping.forEach(purchaseReceiptDetail -> {
                //行号
                String currentLineNum = purchaseReceiptDetail.getString("lineNum")
                //采购收货计划明细对应物料行
                BmfObject purchaseOrderDetail = purchaseOrderDetailIdAutoMapping.stream()
                        .filter(it -> Objects.equals(it.getString("lineNum"), currentLineNum))
                        .findFirst()
                        .orElseThrow(() -> new ScriptInterruptedException("采购订单明细行不存在,物料编码:" + materialCode + "行号:" + currentLineNum))
                //采购收货计划本次入库数量
                BigDecimal subtract = purchaseReceiptDetail.getBigDecimal(WriteBackUtils.D_KEY)
                //采购订单明细旧的已入库数量
                BigDecimal oldOrderWarehousedQuantity = purchaseOrderDetail.getBigDecimal("warehousedQuantity")
                //采购订单明细新的已入库数量=旧的已入库数量+本次入库数量
                BigDecimal newOrderWarehousedQuantity = BigDecimalUtils.add(oldOrderWarehousedQuantity, subtract)
                purchaseOrderDetail.put("warehousedQuantity", newOrderWarehousedQuantity)
            })
            //更新待入库数量
            purchaseReceiptService.updateQuantity(purchaseOrderDetailIdAutoMapping)

            //入库后更新采购收货计划状态
            purchaseOrderService.updateStatus(purchaseReceiptCode, BMF_CLASS, "purchaseReceiptIdAutoMapping")
            //入库后更新采购订单状态
            purchaseOrderService.updateStatus(purchaseOrderCode, SOURCE_BMF_CLASS, "purchaseOrderDetailIdAutoMapping")
        })
    }
}
