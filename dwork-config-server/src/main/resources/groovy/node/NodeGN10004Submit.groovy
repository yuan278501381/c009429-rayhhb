package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.common.AssistantUtils
import com.chinajey.dwork.common.FillUtils
import com.chinajey.dwork.common.domain.DomainWriteBack
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.common.utils.WriteBackUtils
import com.chinajey.dwork.modules.warehousingApplicant.dto.StoreDimension
import com.chinajey.dwork.modules.warehousingApplicant.dto.StoreWriteField
import com.chinajey.dwork.modules.warehousingApplicant.dto.WriteBackResult
import com.tengnat.dwork.common.utils.BigDecimalUtils
import com.tengnat.dwork.common.utils.CodeGenerator
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import org.apache.commons.lang3.StringUtils

import java.util.stream.Collectors

/**
 * 入库任务提交脚本
 */
class NodeGN10004Submit extends NodeGroovyClass {

    BmfService bmfService = SpringUtils.getBean(BmfService.class)

    CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class)

    @Override
    Object runScript(BmfObject nodeData) {
        String dataSourceCode = nodeData.getString("dataSourceCode")
        if (StringUtils.isBlank("dataSourceCode")) {
            throw new BusinessException("入库任务的来源单据不存在")
        }
        BigDecimal submitQuantity = AssistantUtils.getSubmitQuantity(nodeData)
        List<String> passBoxRealCodes = nodeData.getList("passBoxes")
                .stream()
                .map { it -> it.getString("code") }
                .collect(Collectors.toList())
        // 反写PC入库任务 - 明细数量
        BmfObject warehousingTask = this.bmfService.find("warehousingTask", Long.parseLong(dataSourceCode))
        if (warehousingTask == null) {
            throw new BusinessException("入库任务单[" + dataSourceCode + "]不存在")
        }
        List<BmfObject> taskDetails = warehousingTask.getAndRefreshList("warehousingTaskDetailIdAutoMapping")
        WriteBackUtils.writeBack(taskDetails, submitQuantity, "warehousingQuantity", "waitQuantity")
        // 反写PC入库任务 - 周转箱状态
        List<BmfObject> taskPassBoxes = warehousingTask.getAndRefreshList("warehousingTaskPassBoxIdAutoMapping")
        for (final def taskPassBox in taskPassBoxes) {
            if (!passBoxRealCodes.contains(taskPassBox.getString("passBoxRealCode"))) {
                continue
            }
            taskPassBox.put("documentStatus", DocumentStatusEnum.COMPLETED.getCode())
            this.bmfService.updateByPrimaryKeySelective(taskPassBox)
        }
        warehousingTask.put("warehousingQuantity", BigDecimalUtils.add(warehousingTask.getBigDecimal("warehousingQuantity"), submitQuantity))
        boolean taskComplete = AssistantUtils.isCompleted(taskDetails, "waitQuantity")
        warehousingTask.put("documentStatus", taskComplete ? DocumentStatusEnum.COMPLETED.getCode() : DocumentStatusEnum.PARTIAL.getCode())
        this.bmfService.updateByPrimaryKeySelective(warehousingTask)

        // 反写入库申请单 - 明细数量
        String applicantCode = nodeData.getString("preDocumentCode")
        DomainWriteBack domainWriteBack = new DomainWriteBack()
        StoreDimension dim = StoreDimension.builder()
                .materialCode(warehousingTask.getString("materialCode"))
                .warehouseCode(warehousingTask.getString("warehouseCode"))
                .build()
        StoreWriteField field = StoreWriteField.builder()
                .dis("warehousingQuantity")
                .unDis("waitQuantity")
                .build()
        WriteBackResult writeBackResult = domainWriteBack.writeBackStoreDetails(applicantCode, submitQuantity, dim, field)
        BmfObject applicant = writeBackResult.getOrder()
        // 反写入库申请单 - 周转箱状态
        List<BmfObject> applicantPassBoxes = applicant.getAndRefreshList("warehousingApplicantPassBoxIdAutoMapping")
        for (final def applicantPassBox in applicantPassBoxes) {
            if (!passBoxRealCodes.contains(applicantPassBox.getString("passBoxRealCode"))) {
                continue
            }
            applicantPassBox.put("documentStatus", DocumentStatusEnum.COMPLETED.getCode())
            this.bmfService.updateByPrimaryKeySelective(applicantPassBox)
        }
        // 反写入库申请单 - 状态
        List<BmfObject> applicantAllDetails = writeBackResult.getAllDetails()
        boolean applicantComplete = AssistantUtils.isCompleted(applicantAllDetails, "waitConfirmQuantity", "confirmedQuantity", "warehousingQuantity")
        applicant.put("documentStatus", applicantComplete ? DocumentStatusEnum.COMPLETED.getCode() : DocumentStatusEnum.PARTIAL.getCode())
        this.bmfService.updateByPrimaryKeySelective(applicant)

        // 生成入库单 - 结果单据
        List<BmfObject> details = taskDetails
                .stream()
                .filter(it -> WriteBackUtils.isRealWriteBack(it))
                .collect(Collectors.toList())
        BmfObject warehousingOrder = createWarehousingOrder(nodeData, warehousingTask, details, submitQuantity)

        // 多脚本的情况，给其他脚本设置结果单据
        nodeData.put("_result_order", warehousingOrder)
        // 扣减PDA入库任务的待入库数量
        nodeData.put("ext_wait_quantity", BigDecimalUtils.subtractResultMoreThanZero(nodeData.getBigDecimal("ext_wait_quantity"), submitQuantity))
        nodeData.put("targetLocationCode", null)
        nodeData.put("targetLocationName", null)
        this.bmfService.updateByPrimaryKeySelective(nodeData)
        return nodeData
    }

    /**
     * 生成入库单 - 结果单据
     * @param warehousingTask PC入库任务
     * @param passBoxes 提交的周转箱
     */
    BmfObject createWarehousingOrder(BmfObject nodeData, BmfObject warehousingTask, List<BmfObject> taskDetails, BigDecimal submitQuantity) {
        BmfObject bmfObject = new BmfObject("warehousingOrder")
        FillUtils.fillOperator(bmfObject)
        FillUtils.extendComDocFields(warehousingTask, bmfObject)
        bmfObject.put("orderBusinessType", warehousingTask.getString("orderBusinessType"))
        bmfObject.put("targetWarehouseCode", warehousingTask.getString("warehouseCode"))
        bmfObject.put("targetWarehouseName", warehousingTask.getString("warehouseName"))
        bmfObject.put("materialCode", warehousingTask.getString("materialCode"))
        bmfObject.put("materialName", warehousingTask.getString("materialName"))
        bmfObject.put("specifications", warehousingTask.getString("specifications"))
        bmfObject.put("unit", warehousingTask.get("unit"))
        bmfObject.put("quantity", submitQuantity)
        bmfObject.put("remark", nodeData.getBmfClassName() + " - " + nodeData.getPrimaryKeyValue())
        this.codeGenerator.setCode(bmfObject)
        // 入库单 - 构建明细
        List<BmfObject> details = new ArrayList<>()
        for (final def taskDetail in taskDetails) {
            BmfObject detail = new BmfObject("warehousingOrderDetail")
            detail.put("lineNum", taskDetail.getString("lineNum"))
            detail.put("materialCode", warehousingTask.getString("materialCode"))
            detail.put("materialName", warehousingTask.getString("materialName"))
            detail.put("specifications", warehousingTask.getString("specifications"))
            detail.put("quantity", taskDetail.getBigDecimal(WriteBackUtils.D_KEY))
            detail.put("unit", taskDetail.getBmfObject("unit"))
            detail.put("sourceSystem", taskDetail.getString("sourceSystem"))
            detail.put("orderBusinessCode", taskDetail.getString("orderBusinessCode"))
            FillUtils.extendComDocFields(taskDetail, detail)
            details.add(detail)
        }
        bmfObject.put("warehousingOrderDetailIdAutoMapping", details)
        // 入库单 - 构建周转箱
        List<BmfObject> orderPassBoxes = new ArrayList<>()
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        for (final def passBox in passBoxes) {
            BmfObject orderPassBox = new BmfObject("warehousingOrderPassBox")
            orderPassBox.put("passBoxRealCode", passBox.getString("code"))
            orderPassBox.put("passBoxCode", passBox.getString("passBoxCode"))
            orderPassBox.put("passBoxName", passBox.getString("passBoxName"))
            orderPassBox.put("quantity", passBox.getBigDecimal("quantity"))
            orderPassBox.put("unit", warehousingTask.get("unit"))
            orderPassBoxes.add(orderPassBox)
        }
        bmfObject.put("warehousingOrderPassBoxIdAutoMapping", orderPassBoxes)
        this.bmfService.saveOrUpdate(bmfObject)
        return bmfObject
    }
}
