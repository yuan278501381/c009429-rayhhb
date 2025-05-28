package groovy.node

import com.chinajay.virgo.bmf.obj.BmfArray
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.common.FillUtils
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.common.enums.InitiateType
import com.chinajey.dwork.common.utils.WriteBackUtils
import com.tengnat.dwork.common.utils.CodeGenerator
import com.tengnat.dwork.common.utils.JsonUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.collections4.CollectionUtils

import java.util.stream.Collectors

/**
 * 销售发货任务 - 提交
 *
 * 控制不能超发，不能分批提交
 * 提交后：创建销售发货单、反写销售发货计划、清空周转箱
 */
class NodeGN10009Submit extends NodeGroovyClass {

    BmfService bmfService = SpringUtils.getBean(BmfService.class)

    CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class)

    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        if (CollectionUtils.isEmpty(passBoxes)) {
            throw new BusinessException("请提交周转箱")
        }
        long outboundTaskId = nodeData.getLong("dataSourceCode")
        BmfObject outboundTask = this.bmfService.find("outboundTask", outboundTaskId)
        if (outboundTask == null) {
            throw new BusinessException("出库任务[" + outboundTaskId + "]不存在")
        }
        // 待发货数量
        BigDecimal waitQuantity = nodeData.getBigDecimal("ext_unshipped_quantity")
        BigDecimal submitQuantity = passBoxes
                .stream()
                .map { it -> it.getBigDecimal("quantity") }
                .reduce { a, b -> a + b }
                .get()
        if (waitQuantity != submitQuantity) {
            throw new BusinessException("提交数量[" + submitQuantity + "]与待发货数量[" + waitQuantity + "]不一致")
        }
        String deliveryPlanCode = nodeData.getString("ext_shipping_order_code")
        BmfObject deliveryPlan = this.bmfService.findByUnique("salesDeliveryPlan", "code", deliveryPlanCode)
        if (deliveryPlan == null) {
            throw new BusinessException("销售发货计划[" + deliveryPlanCode + "]不存在")
        }
        // 创建销售发货单
        BmfObject deliveryNote = new BmfObject("salesDeliveryNote")
        deliveryNote.put("customerCode", deliveryPlan.getString("customerCode"))
        deliveryNote.put("customerName", deliveryPlan.getString("customerName"))
        deliveryNote.put("materialCode", nodeData.getString("ext_material_code"))
        deliveryNote.put("materialName", nodeData.getString("ext_material_name"))
        deliveryNote.put("sendObjectCode", nodeData.getString("sendObjectCode"))
        deliveryNote.put("sendObjectName", nodeData.getString("sendObjectName"))
        deliveryNote.put("shipQuantity", submitQuantity)
        deliveryNote.put("deliveryDate", deliveryPlan.getDate("deliveryDate"))
        deliveryNote.put("remark", deliveryPlan.getString("remark"))
        deliveryNote.put("salesDeliveryPlanId", deliveryPlan.getString("code"))
        FillUtils.fillComFields(Collections.singletonList(nodeData), deliveryNote, InitiateType.APP)
        deliveryNote.put("sourceSystem", deliveryPlan.getString("sourceSystem"))
        deliveryNote.put("orderBusinessType", deliveryPlan.getString("orderBusinessType"))
        deliveryNote.put("documentStatus", DocumentStatusEnum.COMPLETED.getCode())
        BmfArray notePassBoxes = new BmfArray()
        for (final def passBox in passBoxes) {
            BmfObject notePassBox = new BmfObject("salesDeliveryNotePassBox")
            notePassBox.put("passBoxCode", passBox.getString("passBoxCode"))
            notePassBox.put("passBoxName", passBox.getString("passBoxName"))
            notePassBox.put("materialCode", passBox.getString("materialCode"))
            notePassBox.put("materialName", passBox.getString("materialName"))
            notePassBox.put("quantity", passBox.getBigDecimal("quantity"))
            notePassBox.put("unit", passBox.get("quantityUnit"))
            notePassBoxes.add(notePassBox)
        }
        deliveryNote.put("salesDeliveryNotePassBoxIdAutoMapping", notePassBoxes)
        FillUtils.fillOperator(deliveryNote)

        List<BmfObject> allDeliveryPlanDetails = deliveryPlan.getAndRefreshList("salesDeliveryPlanDetailIdAutoMapping")
        String materialCode = nodeData.getString("ext_material_code")
        String warehouseCode = outboundTask.getString("sourceWarehouseCode")
        List<BmfObject> deliveryPlanDetails = allDeliveryPlanDetails
                .stream()
                .filter { it -> it.getString("materialCode") == materialCode && it.getString("sourceWarehouseCode") == warehouseCode }
                .collect(Collectors.toList())
        WriteBackUtils.writeBack(deliveryPlanDetails, submitQuantity, "shippedQuantity", "waitShippedQuantity")

        BmfArray noteDetails = new BmfArray()
        for (final def deliveryPlanDetail in deliveryPlanDetails) {
            if (!WriteBackUtils.isRealWriteBack(deliveryPlanDetail)) {
                continue
            }
            BmfObject noteDetail = new BmfObject("salesDeliveryNoteDetail")
            JsonUtils.mergeJSONObject(deliveryPlanDetail, noteDetail)
            noteDetail.remove("id")
            noteDetail.put("shippedQuantity", deliveryPlanDetail.getBigDecimal(WriteBackUtils.D_KEY))
            noteDetail.put("unit", deliveryPlanDetail.get("unit"))
            noteDetails.add(noteDetail)
        }
        deliveryNote.put("salesDeliveryNoteDetailIdAutoMapping", noteDetails)

        this.codeGenerator.setCode(deliveryNote)
        this.bmfService.saveOrUpdate(deliveryNote)

        // 反写销售计划状态
        boolean complete = allDeliveryPlanDetails
                .stream()
                .allMatch { it -> it.getBigDecimal("waitShippedQuantity") == BigDecimal.ZERO }
        deliveryPlan.put("documentStatus", complete ? DocumentStatusEnum.COMPLETED.getCode() : DocumentStatusEnum.PARTIAL.getCode())
        this.bmfService.updateByPrimaryKeySelective(deliveryPlan)
        // 清箱
        sceneGroovyService.clearPassBox(passBoxes)
        return nodeData
    }
}
