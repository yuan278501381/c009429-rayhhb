package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.common.FillUtils
import com.tengnat.dwork.common.utils.CodeGenerator
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.ObjectUtils

import java.util.stream.Collectors

//转储任务提交脚本
//兼容了多物料和单物料
class NodeGN10011Submit extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class)


    @Override
    Object runScript(BmfObject nodeData) {
        //前置校验
        validate(nodeData)
        //业务处理
        businessExecute(nodeData)
    }

    /**
     * 前置校验
     * @param bmfObjects
     */
    void validate(BmfObject nodeData) {
        List<BmfObject> tasks = nodeData.getAndRefreshList("tasks")
        if (ObjectUtils.isEmpty(tasks) || tasks.size() != 1) {
            throw new RuntimeException("只允许单物料")
        }
    }

    /**
     * 业务处理
     * @param bmfObjects
     */
    void businessExecute(BmfObject nodeData) {
        //更新库存转储
        updateOrder(nodeData)
        //创建未完成任务
        createNode(nodeData)
        //创建库存转储单
        createInventoryTransfer(nodeData)
    }

    void createInventoryTransfer(BmfObject nodeData) {

        List<BmfObject> inventoryTransferOutPassBoxs = new ArrayList<>()
        nodeData.getList("passBoxes").stream().forEach(object -> {
            def inventoryTransferOutPassBox = new BmfObject("inventoryTransferOutPassBox")
            inventoryTransferOutPassBox.put("passBoxName", object.getString("passBoxName"))
            inventoryTransferOutPassBox.put("passBoxCode", object.getString("passBoxCode"))
            inventoryTransferOutPassBox.put("passBoxRealCode", object.getString("code"))
            inventoryTransferOutPassBox.put("transferOutQuantity", object.getBigDecimal("quantity"))
            inventoryTransferOutPassBox.put("unit", object.get("quantityUnit"))
            inventoryTransferOutPassBoxs.add(inventoryTransferOutPassBox)
        })
        def transferOrderCode = nodeData.getString("preDocumentCode")
        BmfObject inventoryTransferApplicant = basicGroovyService.findOne("inventoryTransferApplicant", "code", transferOrderCode)
        def inventoryTransfer = new BmfObject("inventoryTransfer")
        inventoryTransfer.put("sourceWarehouseName", inventoryTransferApplicant.getString("sourceWarehouseName"))
        inventoryTransfer.put("sourceWarehouseCode", inventoryTransferApplicant.getString("sourceWarehouseCode"))
        inventoryTransfer.put("targetWarehouseName", inventoryTransferApplicant.getString("targetWarehouseName"))
        inventoryTransfer.put("targetWarehouseCode", inventoryTransferApplicant.getString("targetWarehouseCode"))
        inventoryTransfer.put("transferDate", new Date())
        inventoryTransfer.put("preDocumentType", inventoryTransferApplicant.getBmfClassName())
        inventoryTransfer.put("preDocumentCode", inventoryTransferApplicant.getString("code"))
        inventoryTransfer.put("inventoryTransferOutPassBoxIdAutoMapping", inventoryTransferOutPassBoxs)
        def lineNum = nodeData.getString("ext_line_num")
        BigDecimal quantity = nodeData.getList("passBoxes").stream().collect(Collectors.reducing(BigDecimal.ZERO, it -> it.getBigDecimal("quantity"), BigDecimal::add))
        BmfObject detail = inventoryTransferApplicant.getAndRefreshList("inventoryTransferApplicantDetailIdAutoMapping").stream()
                .filter(it -> it.getString("lineNum").equals(lineNum)).findFirst().orElseThrow(() -> new BusinessException("转储申请单明细行不存在,转储申请单编码:" + transferOrderCode + "行号:" + lineNum))
        List<BmfObject> inventoryTransferDetails = new ArrayList<>()
        def inventoryTransferDetail = new BmfObject("inventoryTransferDetail")
        inventoryTransferDetail.put("materialCode", detail.getString("materialCode"))
        inventoryTransferDetail.put("materialName", detail.getString("materialName"))
        inventoryTransferDetail.put("specifications", detail.getString("specifications"))
        inventoryTransferDetail.put("quantity", quantity)
        inventoryTransferDetail.put("unit", detail.get("unit"))
        inventoryTransferDetail.put("lineNum", detail.getString("lineNum"))
        inventoryTransferDetail.put("sourceSystem", detail.getString("sourceSystem"))
        inventoryTransferDetail.put("preDocumentCode", detail.getString("preDocumentCode"))
        inventoryTransferDetail.put("preDocumentType", detail.getString("preDocumentType"))
        inventoryTransferDetail.put("sourceDocumentCode", detail.getString("sourceDocumentCode"))
        inventoryTransferDetail.put("sourceDocumentType", detail.getString("sourceDocumentType"))
        inventoryTransferDetail.put("externalDocumentCode", detail.getString("externalDocumentCode"))
        inventoryTransferDetail.put("externalDocumentType", detail.getString("externalDocumentType"))
        inventoryTransferDetails.add(inventoryTransferDetail)
        inventoryTransfer.put("inventoryTransferDetailIdAutoMapping", inventoryTransferDetails)
        codeGenerator.setCode(inventoryTransfer)
        FillUtils.fillOperator(inventoryTransfer)
        basicGroovyService.saveOrUpdate(inventoryTransfer)
    }

    /**
     * 创建新的转储任务
     * @param bmfObject
     * @param tasks 待调拨物料
     */
    void createNode(BmfObject bmfObject) {
        BigDecimal pendingQuantity = bmfObject.getBigDecimal("ext_pending_quantity")
        BigDecimal quantity = bmfObject.getList("passBoxes").stream().collect(Collectors.reducing(BigDecimal.ZERO, it -> it.getBigDecimal("quantity"), BigDecimal::add))
        def subtract = pendingQuantity.subtract(quantity)
        if (subtract <= 0) {
            return
        }
        def clone = bmfObject.deepClone()
        for (final def task in clone.getAndRefreshList("tasks")) {
            task.remove("ID")
        }
        clone.put("targetLocationCode", null)
        clone.put("targetLocationName", null)
        clone.put("ext_pending_quantity", subtract)
        sceneGroovyService.saveBySelf(clone)
        //新增/更新物流交易记录
        sceneGroovyService.saveLogisticsTransaction(Collections.singletonList(bmfObject), clone)
    }

    /**
     * 更新库存转储
     * @param bmfObject
     * @return
     */
    void updateOrder(BmfObject bmfObject) {
        //库存子表物料剩余数量用来判断状态
        def lineNum = bmfObject.getString("ext_line_num")
        def transferOrderCode = bmfObject.getString("preDocumentCode")
        BmfObject inventoryTransferApplicant = basicGroovyService.findOne("inventoryTransferApplicant", "code", transferOrderCode)
        BigDecimal quantity = bmfObject.getList("passBoxes").stream().collect(Collectors.reducing(BigDecimal.ZERO, it -> it.getBigDecimal("quantity"), BigDecimal::add))

        BmfObject detail = inventoryTransferApplicant.getAndRefreshList("inventoryTransferApplicantDetailIdAutoMapping").stream()
                .filter(it -> it.getString("lineNum").equals(lineNum)).findFirst().orElseThrow(() -> new BusinessException("转储申请单明细行不存在,转储申请单编码:" + transferOrderCode + "行号:" + lineNum))
        //本次没提交
        if (quantity == null || quantity == BigDecimal.ZERO) {
            return
        }
        //提交了处理反写数据
        def transferQuantity = detail.getBigDecimal("transferQuantity")
        detail.put("transferQuantity", transferQuantity + quantity)
        basicGroovyService.updateByPrimaryKeySelective(detail)
        def updateTask = new BmfObject("inventoryTransferApplicant")
        updateTask.put("id", inventoryTransferApplicant.getPrimaryKeyValue())
        def detailTasks = inventoryTransferApplicant.getAndRefreshList("inventoryTransferApplicantDetailIdAutoMapping").stream()
                .filter(it -> it.getBigDecimal("quantity").subtract(it.getBigDecimal("transferQuantity")) > BigDecimal.ZERO)
                .collect(Collectors.toList())
        if (ObjectUtils.isEmpty(detailTasks)) {
            updateTask.put("documentStatus", "completed")
        } else {
            updateTask.put("documentStatus", "partial")
        }
        basicGroovyService.updateByPrimaryKeySelective(updateTask)
    }
}
