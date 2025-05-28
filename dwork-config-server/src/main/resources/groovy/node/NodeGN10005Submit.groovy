package groovy.node

import com.chinajey.dwork.common.FillUtils
import com.tengnat.dwork.common.utils.LogisticsUtils
import org.springframework.util.CollectionUtils
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.ValueUtil
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.tengnat.dwork.common.utils.BigDecimalUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import groovy.common.NodeUpdatePassBoxRealLocation
import com.chinajey.dwork.modules.warehousingApplicant.service.WarehousingApplicantService
import com.chinajey.dwork.modules.purchaseReceipt.service.PurchaseReceiptService
import com.chinajey.dwork.common.utils.WriteBackUtils
import java.util.stream.Collectors

/**
 * 采购收货任务提交脚本
 * @author angel.su
 * @createTime 2025/3/21 13:25
 */
class NodeGN10005Submit extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    NodeUpdatePassBoxRealLocation nodeUpdatePassBoxRealLocation = new NodeUpdatePassBoxRealLocation()
    WarehousingApplicantService warehousingApplicantService = SpringUtils.getBean(WarehousingApplicantService.class)
    PurchaseReceiptService purchaseReceiptService = SpringUtils.getBean(PurchaseReceiptService.class)
    private static final String PRE_BMF_CLASS = "purchaseReceipt"
    private static final String SOURCE_BMF_CLASS = "purchaseOrder"

    @Override
    Object runScript(BmfObject nodeData) {
        //前置校验
        validate(nodeData)
        //业务处理
        businessExecute(nodeData)
        return nodeData
    }

    void validate(BmfObject nodeData) {
        //本次收货数量(填写) 可以超收 ext_current_received_quantity
        BigDecimal currentReceivedQty = ValueUtil.toBigDecimal(nodeData.getBigDecimal("ext_current_received_quantity"))
        if (currentReceivedQty <= BigDecimal.ZERO) {
            throw new BusinessException("本次收货数量不能为0")
        }

        //位置
        String targetLocationCode = nodeData.getString("targetLocationCode")
        BmfObject location = basicGroovyService.getByCode("location", targetLocationCode)
        if (location == null) {
            throw new BusinessException("放置位置[" + targetLocationCode + "]不存在")
        }

        //周转箱
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        if (CollectionUtils.isEmpty(passBoxes)) {
            throw new BusinessException("周转箱不能为空")
        }
        //周转箱总数量
        BigDecimal totalQuantity = passBoxes.stream().map(it -> {
            BigDecimal receiveQuantity = it.getBigDecimal("receiveQuantity")
            if (receiveQuantity <= BigDecimal.ZERO) {
                throw new BusinessException("周转箱[" + it.getString("passBoxCode") + "]数量不能为0")
            }
            return receiveQuantity
        }).reduce(BigDecimal.ZERO, BigDecimal::add)

        if (totalQuantity != currentReceivedQty) {
            throw new BusinessException("周转箱总数量必须等于本次收货数量")
        }
        nodeData.putUncheck("totalQuantity", totalQuantity)

        //校验上级单据-采购收货计划
        String purchaseReceiptCode = nodeData.getString("preDocumentCode")
        BmfObject purchaseReceipt = basicGroovyService.getByCode(PRE_BMF_CLASS, purchaseReceiptCode)
        if (purchaseReceipt == null) {
            throw new BusinessException("采购收货计划[" + purchaseReceiptCode + "]不存在")
        }
        List<BmfObject> purchaseReceiptDetail = purchaseReceipt.getAndRefreshList("purchaseReceiptIdAutoMapping")
        if (CollectionUtils.isEmpty(purchaseReceiptDetail)) {
            throw new BusinessException("采购收货计划[" + purchaseReceiptCode + "]明细为空")
        }
        nodeData.putUncheck(PRE_BMF_CLASS, purchaseReceipt)

        //校验源头单据-采购订单
        String purchaseOrderCode = nodeData.getString("sourceDocumentCode")
        BmfObject purchaseOrder = basicGroovyService.getByCode(SOURCE_BMF_CLASS, purchaseOrderCode)
        if (purchaseOrder == null) {
            throw new BusinessException("采购订单[" + purchaseOrderCode + "]不存在")
        }
        nodeData.putUncheck(SOURCE_BMF_CLASS, purchaseOrder)
    }

    void businessExecute(BmfObject nodeData) {
        //更新周转箱位置信息
        nodeUpdatePassBoxRealLocation.updatePassBoxReal(nodeData)
        //物料编码
        String materialCode = nodeData.getString("ext_material_code")
        //周转箱总数量(等于本次收货数量)
        BigDecimal totalQuantity = nodeData.getBigDecimal("totalQuantity")
        //待收货数量 ext_wait_received_quantity
        BigDecimal waitReceivedQty = ValueUtil.toBigDecimal(nodeData.getBigDecimal("ext_wait_received_quantity"))

        //更新上级单据-采购收货计划明细
        updatePurchaseReceiptDetail(nodeData, materialCode, totalQuantity)
        //周转箱总数量小于待收货，则创建新任务
        if (totalQuantity < waitReceivedQty) {
            createNewTask(nodeData, waitReceivedQty, totalQuantity)
        }
    }

    private void updatePurchaseReceiptDetail(BmfObject nodeData, String materialCode, BigDecimal totalQuantity) {
        BmfObject purchaseReceipt = nodeData.getBmfObject(PRE_BMF_CLASS)
        //采购收货计划明细
        List<BmfObject> purchaseReceiptDetail = purchaseReceipt.getAndRefreshList("purchaseReceiptIdAutoMapping")
        //根据物料编码查找明细行列表
        List<BmfObject> groupByMaterialCode = purchaseReceiptDetail.stream()
                .filter(it -> it.getString("materialCode") == materialCode)
                .collect(Collectors.toList())
        //反写收货数量
        WriteBackUtils.writeBack(groupByMaterialCode, totalQuantity, "receivedQuantity", "waitReceivedQuantity")
        //反写待入库数量
        purchaseReceiptService.updateQuantity(groupByMaterialCode)

        //更新单据状态-部分处理
        purchaseReceipt.put("documentStatus", DocumentStatusEnum.PARTIAL.getCode())
        basicGroovyService.updateByPrimaryKeySelective(purchaseReceipt)

        //反写采购订单
        this.updatePurchaseOrderDetail(nodeData, groupByMaterialCode, materialCode)
        //创建并下达采购收货类型的入库申请单
        this.createWarehousingApplicant(nodeData, groupByMaterialCode)
    }

    private void updatePurchaseOrderDetail(BmfObject nodeData, List<BmfObject> purchaseReceiptIdAutoMapping, String materialCode) {
        BmfObject purchaseOrder = nodeData.getBmfObject(SOURCE_BMF_CLASS)
        //采购订单明细
        List<BmfObject> purchaseOrderDetailIdAutoMapping = purchaseOrder.getAndRefreshList("purchaseOrderDetailIdAutoMapping")
                .stream()
                .filter(it -> it.getString("materialCode") == materialCode)
                .collect(Collectors.toList())

        purchaseOrderDetailIdAutoMapping.forEach(purchaseOrderDetail -> {
            //行号
            String lineNum = purchaseOrderDetail.getString("lineNum")
            //采购收货计划明细对应物料行
            BmfObject purchaseReceiptDetail = purchaseReceiptIdAutoMapping.stream()
                    .filter(it -> Objects.equals(it.getString("lineNum"), lineNum))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("采购收货计划明细行不存在,物料编码:" + materialCode + ",行号:" + lineNum))
            //本次收货数量
            BigDecimal subtract = purchaseReceiptDetail.getBigDecimal(WriteBackUtils.D_KEY)
            //采购订单明细旧的已收货数量
            BigDecimal oldReceivedQuantity = purchaseOrderDetail.getBigDecimal("receivedQuantity")
            //采购订单明细新的已收货数量=旧的已收货数量+本次收货数量
            BigDecimal newReceivedQuantity = BigDecimalUtils.add(oldReceivedQuantity, subtract)
            purchaseOrderDetail.put("receivedQuantity", newReceivedQuantity)
            //采购订单明细计划数量
            BigDecimal planQuantity = purchaseOrderDetail.getBigDecimal("planQuantity")
            //采购订单明细新的待收货数量=采购订单明细计划数量-采购订单明细新的已收货数量
            BigDecimal newWaitReceivedQuantity = BigDecimalUtils.subtractResultMoreThanZero(planQuantity, newReceivedQuantity)
            purchaseOrderDetail.put("waitReceivedQuantity", newWaitReceivedQuantity)
            basicGroovyService.updateByPrimaryKeySelective(purchaseOrderDetail)
        })
        //反写待入库数量
        purchaseReceiptService.updateQuantity(purchaseOrderDetailIdAutoMapping)

        //更新单据状态-部分处理
        purchaseOrder.put("documentStatus", DocumentStatusEnum.PARTIAL.getCode())
        basicGroovyService.updateByPrimaryKeySelective(purchaseOrder)
    }

    private void createWarehousingApplicant(BmfObject nodeData, List<BmfObject> purchaseReceiptIdAutoMapping) {
        //子表
        List<BmfObject> warehousingApplicantIdAutoMapping = new ArrayList<>()
        for (int i = 0; i < purchaseReceiptIdAutoMapping.size(); i++) {
            BmfObject purchaseReceiptDetail = purchaseReceiptIdAutoMapping.get(i)
            BigDecimal subtract = purchaseReceiptDetail.getBigDecimal(WriteBackUtils.D_KEY)
            if (subtract <= BigDecimal.ZERO) continue
            String lineNum = purchaseReceiptDetail.getString("lineNum")
            BmfObject warehousingApplicantDetail = new BmfObject("warehousingApplicantDetail")
            warehousingApplicantDetail.put("lineNum", lineNum)
            warehousingApplicantDetail.put("materialCode", purchaseReceiptDetail.getString("materialCode"))
            warehousingApplicantDetail.put("materialName", purchaseReceiptDetail.getString("materialName"))
            warehousingApplicantDetail.put("specifications", purchaseReceiptDetail.getString("specifications"))
            BmfObject unit = purchaseReceiptDetail.getAndRefreshBmfObject("unit")
            warehousingApplicantDetail.put("unit", unit)
            warehousingApplicantDetail.put("targetWarehouseCode", purchaseReceiptDetail.getString("warehouseCode"))
            warehousingApplicantDetail.put("targetWarehouseName", purchaseReceiptDetail.getString("warehouseName"))
            warehousingApplicantDetail.put("planQuantity", subtract)
            warehousingApplicantDetail.put("warehousingQuantity", BigDecimal.ZERO)
            FillUtils.fillOperator(warehousingApplicantDetail)
            LogisticsUtils.setDocumentData(nodeData, warehousingApplicantDetail)
            warehousingApplicantIdAutoMapping.add(warehousingApplicantDetail)
        }
        //主表
        BmfObject bmfObject = new BmfObject("warehousingApplicant")
        FillUtils.fillOperator(bmfObject)
        //周转箱
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        BmfObject purchaseReceipt = purchaseReceiptIdAutoMapping.get(0)
        passBoxes.forEach(passBox -> {
            passBox.remove("id")
            passBox.put("passBoxRealCode", passBox.get("code"))
            passBox.put("warehouseCode", purchaseReceipt.get("warehouseCode"))
            passBox.put("warehouseName", purchaseReceipt.get("warehouseName"))
            passBox.put("unit", purchaseReceipt.get("unit"))
            passBox.put("specifications", purchaseReceipt.get("specifications"))
            passBox.put("documentStatus", DocumentStatusEnum.PENDING.getCode())
        })
        bmfObject.put("warehousingApplicantIdAutoMapping", warehousingApplicantIdAutoMapping)
        bmfObject.put("warehousingApplicantPassBoxIdAutoMapping", passBoxes)
        bmfObject.put("orderBusinessType", "purchaseReceipt")
        LogisticsUtils.setDocumentData(nodeData, bmfObject)
        bmfObject = warehousingApplicantService.save(bmfObject)
        if (bmfObject != null) {
            warehousingApplicantService.issued(Collections.singletonList(bmfObject.getPrimaryKeyValue()))
        }

    }

    private void createNewTask(BmfObject nodeData, BigDecimal waitReceivedQty, BigDecimal totalQuantity) {
        //新的待收货数量
        BigDecimal newWaitReceivedQty = BigDecimalUtils.subtractResultMoreThanZero(waitReceivedQty, totalQuantity)
        BmfObject clone = nodeData.deepClone()
        clone = BmfUtils.genericFromJsonExt(clone, clone.getBmfClassName())
        clone.put("ext_wait_received_quantity", newWaitReceivedQty)
        clone.put("ext_current_received_quantity", null)
        clone.put("ext_single_box_quantity", null)
        clone.put("targetLocationCode", null)
        clone.put("targetLocationName", null)
        clone.put("passBoxes", null)
        sceneGroovyService.saveBySelf(clone)
        //物流交易记录
        sceneGroovyService.saveLogisticsTransaction(Collections.singletonList(nodeData), clone)
    }


}
