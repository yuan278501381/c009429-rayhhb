package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.script.exception.ScriptInterruptedException
import com.chinajey.dwork.common.AssistantUtils
import com.chinajey.dwork.common.FillUtils
import com.chinajey.dwork.common.domain.DomainWriteBack
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.common.utils.WriteBackUtils
import com.chinajey.dwork.modules.warehousingApplicant.dto.StoreDimension
import com.chinajey.dwork.modules.warehousingApplicant.dto.StoreWriteField
import com.chinajey.dwork.modules.warehousingApplicant.dto.WriteBackResult
import com.tengnat.dwork.common.utils.BigDecimalUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.SceneGroovyService

import java.util.stream.Collectors

/**
 * 入库待确认提交脚本
 * 如果是新扫入的周转箱，只能扫空箱
 */
class NodeGN10003Submit extends NodeGroovyClass {

    BmfService bmfService = SpringUtils.getBean(BmfService.class)

    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        // 提交的数量
        BigDecimal submitQuantity = AssistantUtils.getSubmitQuantity(nodeData)
        // 反写入库申请单 - 返回被反写的行明细
        WriteBackResult store = writeBackApplicant(nodeData, submitQuantity)
        // 创建入库任务 - PC
        BmfObject warehousingTask = createWarehousingTask(nodeData, submitQuantity, store)
        // 创建入库任务 - PDA
        createGn10004(warehousingTask, nodeData)
        // 处理分批提交 - 创建新任务
        handlePartSubmit(nodeData, submitQuantity)
        throw new ScriptInterruptedException("不流转数据")
    }

    /**
     * 反写入库申请单
     */
    WriteBackResult writeBackApplicant(BmfObject nodeData, BigDecimal submitQuantity) {
        String applicantCode = nodeData.getString("preDocumentCode")
        DomainWriteBack domainWriteBack = new DomainWriteBack()
        StoreDimension dim = StoreDimension.builder()
                .materialCode(nodeData.getString("ext_material_code"))
                .warehouseCode(nodeData.getString("warehouseCode"))
                .build()
        StoreWriteField field = StoreWriteField.builder()
                .dis("confirmedQuantity")
                .unDis("waitConfirmQuantity")
                .inc("waitQuantity")
                .build()
        WriteBackResult writeBackResult = domainWriteBack.writeBackStoreDetails(applicantCode, submitQuantity, dim, field)

        BmfObject applicant = writeBackResult.getOrder()
        List<BmfObject> diskApplicantPassBoxes = applicant.getAndRefreshList("warehousingApplicantPassBoxIdAutoMapping")
        List<String> diskPassBoxRealCodes = diskApplicantPassBoxes
                .stream()
                .map { it -> it.getString("passBoxRealCode") }
                .collect(Collectors.toList())
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        for (final def passBox in passBoxes) {
            if (diskPassBoxRealCodes.contains(passBox.getString("code"))) {
                continue
            }
            BmfObject applicantPassBox = new BmfObject("warehousingApplicantPassBox")
            applicantPassBox.put("passBoxRealCode", passBox.getString("code"))
            applicantPassBox.put("passBoxCode", passBox.getString("passBoxCode"))
            applicantPassBox.put("passBoxName", passBox.getString("passBoxName"))
            applicantPassBox.put("materialCode", passBox.getString("materialCode"))
            applicantPassBox.put("materialName", passBox.getString("materialName"))
            applicantPassBox.put("specifications", nodeData.getString("ext_specifications"))
            applicantPassBox.put("quantity", passBox.getBigDecimal("quantity"))
            applicantPassBox.put("unit", nodeData.getLong("ext_unit_id"))
            applicantPassBox.put("documentStatus", DocumentStatusEnum.PENDING.getCode())
            applicantPassBox.put("warehouseCode", nodeData.getString("warehouseCode"))
            applicantPassBox.put("warehouseName", nodeData.getString("warehouseName"))
            applicantPassBox.put("warehousingApplicantPassBoxId", applicant)
            this.bmfService.saveOrUpdate(applicantPassBox)
        }
        return writeBackResult
    }

    /**
     * 创建入库任务 - PC
     */
    BmfObject createWarehousingTask(BmfObject nodeData, BigDecimal submitQuantity, WriteBackResult store) {
        String warehouseCode = nodeData.getString("warehouseCode")
        String materialCode = nodeData.getString("ext_material_code")
        // 构建PC入库任务
        BmfObject warehousingTask = new BmfObject("warehousingTask")
        FillUtils.fillOperator(warehousingTask)
        FillUtils.extendComFields(nodeData, warehousingTask)
        warehousingTask.put("orderBusinessType", nodeData.getString("ext_order_business_type"))
        warehousingTask.put("warehouseCode", warehouseCode)
        warehousingTask.put("warehouseName", nodeData.getString("warehouseName"))
        warehousingTask.put("documentStatus", DocumentStatusEnum.PENDING.getCode())
        warehousingTask.put("materialCode", materialCode)
        warehousingTask.put("materialName", nodeData.getString("ext_material_name"))
        warehousingTask.put("specifications", nodeData.getString("ext_specifications"))
        warehousingTask.put("unit", nodeData.getLong("ext_unit_id"))
        warehousingTask.put("warehousingQuantity", BigDecimal.ZERO)
        warehousingTask.put("planQuantity", submitQuantity)
        // 构建PC入库任务明细
        List<BmfObject> details = new ArrayList<>()
        List<BmfObject> applicantDetails = store.getDetails()
        for (final def applicantDetail in applicantDetails) {
            BigDecimal distributeQuantity = applicantDetail.getBigDecimal(WriteBackUtils.D_KEY)
            BmfObject detail = new BmfObject("warehousingTaskDetail")
            FillUtils.fillOperator(detail)
            FillUtils.extendComDocFields(applicantDetail, detail)
            detail.put("planQuantity", distributeQuantity)
            detail.put("warehousingQuantity", BigDecimal.ZERO)
            detail.put("waitQuantity", distributeQuantity)
            detail.put("lineNum", applicantDetail.getString("lineNum"))
            detail.put("unit", applicantDetail.get("unit"))
            detail.put("remark", applicantDetail.getString("remark"))
            details.add(detail)
        }
        // 构建PC入库任务周转箱
        List<BmfObject> taskPassBoxes = new ArrayList<>()
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        for (final def passBox in passBoxes) {
            BmfObject taskPassBox = new BmfObject("warehousingTaskPassBox")
            taskPassBox.put("passBoxRealCode", passBox.getString("code"))
            taskPassBox.put("passBoxCode", passBox.getString("passBoxCode"))
            taskPassBox.put("passBoxName", passBox.getString("passBoxName"))
            taskPassBox.put("quantity", passBox.getBigDecimal("quantity"))
            taskPassBox.put("documentStatus", DocumentStatusEnum.PENDING.getCode())
            taskPassBoxes.add(taskPassBox)
        }
        warehousingTask.put("warehousingTaskDetailIdAutoMapping", details)
        warehousingTask.put("warehousingTaskPassBoxIdAutoMapping", taskPassBoxes)
        this.bmfService.saveOrUpdate(warehousingTask)
        return warehousingTask
    }

    /**
     * 创建入库任务 - PDA
     */
    void createGn10004(BmfObject warehousingTask, BmfObject nodeData) {
        BmfObject material = this.bmfService.findByUnique("material", "code", warehousingTask.getString("materialCode"))
        BmfObject gn10004 = new BmfObject("GN10004")
        FillUtils.extendComDocFields(warehousingTask, gn10004)
        gn10004.put("logisticsStatus", "1")
        gn10004.put("buzSceneInstanceNode", nodeData.getBmfObject("nextInstanceNode"))
        gn10004.put("dataSourceType", warehousingTask.getBmfClassName())
        gn10004.put("dataSourceCode", warehousingTask.getPrimaryKeyValue())
        gn10004.put("sourceLocationCode", nodeData.getString("targetLocationCode"))
        gn10004.put("sourceLocationName", nodeData.getString("targetLocationName"))
        gn10004.put("ext_target_warehouse_code", warehousingTask.getString("warehouseCode"))
        gn10004.put("ext_target_warehouse_name", warehousingTask.getString("warehouseName"))
        gn10004.put("ext_order_business_type", warehousingTask.getString("orderBusinessType"))
        gn10004.put("ext_material_code", warehousingTask.getString("materialCode"))
        gn10004.put("ext_material_name", warehousingTask.getString("materialName"))
        gn10004.put("ext_specifications", warehousingTask.getString("specifications"))
        gn10004.put("ext_wait_quantity", warehousingTask.getBigDecimal("planQuantity"))
        gn10004.put("ext_unit", nodeData.get("ext_unit"))
        gn10004.put("ext_unit_id", nodeData.get("ext_unit_id"))

        // 构建移动应用tasks
        BmfObject task = new BmfObject("GN10004Tasks")
        task.put("materialCode", material.getString("code"))
        task.put("materialName", material.getString("name"))
        task.put("material", material)
        task.put("quantityUnit", material.getBmfObject("flowUnit"))
        List<BmfObject> tasks = new ArrayList<>()
        tasks.add(task)
        gn10004.put("tasks", tasks)

        // 构建移动应用passBoxes
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        List<BmfObject> gn10004PassBoxes = new ArrayList<>()
        for (final def passBox in passBoxes) {
            BmfObject gn10004PassBox = passBox.deepClone()
            gn10004PassBox.remove("id")
            gn10004PassBox.setBmfClassName("GN10004PassBoxes")
            gn10004PassBox.putUncheck("submit", null)
            gn10004PassBoxes.add(gn10004PassBox)
        }
        gn10004.put("passBoxes", gn10004PassBoxes)

        this.bmfService.saveOrUpdate(gn10004)
        // 物流交易记录
        this.sceneGroovyService.saveLogisticsTransaction(Collections.singletonList(nodeData), gn10004)
    }

    void handlePartSubmit(BmfObject nodeData, BigDecimal submitQuantity) {
        BigDecimal planQuantity = nodeData.getBigDecimal("ext_plan_quantity")
        if (submitQuantity >= planQuantity) {
            return
        }
        BmfObject clone = nodeData.deepClone()
        clone.put("ext_plan_quantity", BigDecimalUtils.subtract(planQuantity, submitQuantity))
        this.sceneGroovyService.saveBySelf(clone)
        // 物流交易记录
        this.sceneGroovyService.saveLogisticsTransaction(Collections.singletonList(nodeData), clone)
    }
}
