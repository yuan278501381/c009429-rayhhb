package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.common.utils.UpdateDataUtils
import com.tengnat.dwork.common.utils.CodeGenerator
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils

import java.util.stream.Collectors

/**
 * 出库任务提交脚本
 */
class NodeGN10002Submit extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class)
    BigDecimal passBoxesQuantity = BigDecimal.ZERO

    @Override
    Object runScript(BmfObject nodeData) {
        //前置校验
        validate(nodeData)
        //部分提交处理
        partialSubmissions(nodeData)
        //业务处理
        businessExecute(nodeData)
    }

    /**
     * 前置校验
     * @param bmfObject
     */
    void validate(BmfObject bmfObject) {
        //校验周转箱数量不能大于待出库数量
        //同物料周转箱装箱数
        passBoxesQuantity = bmfObject.getAndRefreshList("passBoxes").stream().collect(Collectors.reducing(BigDecimal.ZERO, it -> it.getBigDecimal("quantity"), BigDecimal::add)) as BigDecimal
        if (passBoxesQuantity == null || passBoxesQuantity <= BigDecimal.ZERO) {
            throw new BusinessException("周转箱数量不能小于0")
        }
    }

    /**
     * 业务处理
     * @param bmfObjects
     */
    void businessExecute(BmfObject nodeData) {
        //返写任务单
        reverseWritingOutboundTask(nodeData)

    }


    //创建出库单
    BmfObject createoutboundOrder(Map<Long, BigDecimal> detailsQuantity, BmfObject outboundTask, BmfObject bmfObject) {
        def list = outboundTask.getAndRefreshList("outboundTaskIdAutoMapping")
        //子表按物料分组和移动应用对齐
        Map<Long, BmfObject> outboundTaskDetailsById = list.stream().collect(Collectors.groupingBy(it -> it.getLong("id")))
        def details = new ArrayList<BmfObject>()
        for (Long id in detailsQuantity.keySet()) {
            BigDecimal quantity = detailsQuantity.get(id)
            BmfObject it = outboundTaskDetailsById.get(id)
            BmfObject outboundOrderDetail = new BmfObject("outboundOrderDetail")
            outboundOrderDetail.put("materialCode", it.getString("materialCode"))
            outboundOrderDetail.put("materialName", it.getString("materialName"))
            outboundOrderDetail.put("specifications", it.getString("specifications"))
            outboundOrderDetail.put("sourceDocumentType", it.getString("sourceDocumentType"))
            outboundOrderDetail.put("sourceDocumentCode", it.getString("sourceDocumentCode"))
            outboundOrderDetail.put("preDocumentType", it.getString("preDocumentType"))
            outboundOrderDetail.put("preDocumentCode", it.getString("preDocumentCode"))
            outboundOrderDetail.put("externalDocumentType", it.getString("externalDocumentType"))
            outboundOrderDetail.put("externalDocumentCode", it.getString("externalDocumentCode"))
            outboundOrderDetail.put("unit", it.get("unit"))
            outboundOrderDetail.put("quantity", quantity)
            outboundOrderDetail.put("lineNum", it.get("lineNum"))
            details.add(outboundOrderDetail)
        }
        //周转箱明细
        def passBoxes = new ArrayList<BmfObject>()
        bmfObject.getAndRefreshList("passBoxes").forEach(ps -> {
            BmfObject outboundOrderPassBox = new BmfObject("outboundOrderPassBox")
            outboundOrderPassBox.put("passBoxName", ps.getString("passBoxName"))
            outboundOrderPassBox.put("passBoxCode", ps.getString("passBoxCode"))
            outboundOrderPassBox.put("passBoxRealCode", ps.getString("code"))
            outboundOrderPassBox.put("quantity", ps.getBigDecimal("quantity"))
            outboundOrderPassBox.put("unit", ps.getLong("quantityUnit"))
            passBoxes.add(outboundOrderPassBox)
        })
        def externalDocumentCodes = details.stream().map(t -> t.getString("externalDocumentCode")).filter(t -> StringUtils.isNotBlank(t)).distinct().collect(Collectors.joining(","))
        def sourceDocumentCodes = details.stream().map(t -> t.getString("sourceDocumentCode")).filter(t -> StringUtils.isNotBlank(t)).distinct().collect(Collectors.joining(","))
        def preDocumentCodes = details.stream().map(t -> t.getString("preDocumentCode")).filter(t -> StringUtils.isNotBlank(t)).distinct().collect(Collectors.joining(","))
        BmfObject outboundOrder = new BmfObject("outboundOrder")
        outboundOrder.put("orderBusinessCode", bmfObject.getString("ext_order_business_type"))
        outboundOrder.put("providerCode", bmfObject.getString("ext_source_warehouse_code"))
        outboundOrder.put("providerName", bmfObject.getString("ext_source_warehouse_name"))
        outboundOrder.put("sourceWarehouseCode", bmfObject.getString("ext_source_warehouse_code"))
        outboundOrder.put("sourceWarehouseName", bmfObject.getString("ext_source_warehouse_name"))
        outboundOrder.put("preDocumentType", bmfObject.getString("preDocumentType"))
        outboundOrder.put("preDocumentCode", preDocumentCodes)
        outboundOrder.put("sourceDocumentType", bmfObject.getString("sourceDocumentType"))
        outboundOrder.put("sourceDocumentCode", sourceDocumentCodes)
        outboundOrder.put("externalDocumentType", bmfObject.getString("externalDocumentType"))
        outboundOrder.put("externalDocumentCode", externalDocumentCodes)
        outboundOrder.put("outboundOrderDetailIdAutoMapping", details)
        outboundOrder.put("outboundOrderPassBoxIdAutoMapping", passBoxes)
        codeGenerator.setCode(outboundOrder)
        UpdateDataUtils.updateOperateInfo(outboundOrder)
        basicGroovyService.saveOrUpdate(outboundOrder)
        bmfObject.put("_result_order", outboundOrder)
        return outboundOrder
    }

    //反写任务单
    void reverseWritingOutboundTask(BmfObject bmfObject) {

        def outboundTaskId = bmfObject.getString("ext_outbound_task_code")
        BmfObject outboundTask = basicGroovyService.find("outboundTask", Long.valueOf(outboundTaskId))
        def outboundTaskDetailList = outboundTask.getAndRefreshList("outboundTaskIdAutoMapping")
        Map<Long, BigDecimal> detailsQuantity = new HashMap<>()
        def size = outboundTaskDetailList.size()
        int index = 0
        outboundTaskDetailList.forEach(it -> {
            index++
            BigDecimal waitQuantity = BigDecimal.valueOf(it.getBigDecimal("waitQuantity"))
            if (passBoxesQuantity == BigDecimal.ZERO || waitQuantity <= 0) {
                return
            }
            if (passBoxesQuantity > it.getBigDecimal("waitQuantity") && index == size) {
                log.info("返写最后一个出库任务单当前物料{},当前出库数量:{},剩余待出{}", it.getString("materialCode"), passBoxesQuantity, waitQuantity)
                //最后一个节点直接全部为出库数量
                it.put("outboundQuantity", it.getBigDecimal("outboundQuantity").add(passBoxesQuantity))
                it.put("waitQuantity", BigDecimal.ZERO)
                basicGroovyService.updateByPrimaryKeySelective(it)
                //反写申请单明细
                reverseWritingOutboundApplicant(it, passBoxesQuantity)
                detailsQuantity.put(it.getLong("id"), passBoxesQuantity)
                passBoxesQuantity = BigDecimal.ZERO
            } else if (passBoxesQuantity > it.getBigDecimal("waitQuantity")) {
                log.info("返写出库任务单当前物料{},当前出库数量:{},剩余待出{}", it.getString("materialCode"), passBoxesQuantity, waitQuantity)
                //剩余待出数量大于当前待出数量时待出库数量就是本次出库数量
                it.put("outboundQuantity", it.getBigDecimal("outboundQuantity").add(waitQuantity))
                passBoxesQuantity = passBoxesQuantity.subtract(waitQuantity)
                it.put("waitQuantity", BigDecimal.ZERO)
                basicGroovyService.updateByPrimaryKeySelective(it)
                //反写申请单明细
                reverseWritingOutboundApplicant(it, waitQuantity)
                detailsQuantity.put(it.getLong("id"), waitQuantity)

            } else {
                log.info("返写出库任务单当前物料{},当前出库数量:{}", it.getString("materialCode"), passBoxesQuantity)
                it.put("outboundQuantity", it.getBigDecimal("outboundQuantity").add(passBoxesQuantity))
                it.put("waitQuantity", waitQuantity.subtract(passBoxesQuantity))
                basicGroovyService.updateByPrimaryKeySelective(it)
                //反写申请单明细
                reverseWritingOutboundApplicant(it, passBoxesQuantity)
                detailsQuantity.put(it.getLong("id"), passBoxesQuantity)
                passBoxesQuantity = BigDecimal.ZERO
            }
        })
        createoutboundOrder(detailsQuantity, outboundTask, bmfObject)
        //判断剩余待出库数量来更新任务单状态
        def updateTask = new BmfObject("outboundTask")
        updateTask.put("id", outboundTask.getPrimaryKeyValue())
        def waitQuantity = outboundTaskDetailList.stream().map(t -> t.getBigDecimal("waitQuantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
        updateTask.put("outboundQuantity", outboundTaskDetailList.stream().map(t -> t.getBigDecimal("outboundQuantity")).reduce(BigDecimal.ZERO, BigDecimal::add))
        updateTask.put("waitQuantity", waitQuantity)
        if (waitQuantity <= 0) {
            updateTask.put("documentStatus", "done")
        } else {
            updateTask.put("documentStatus", DocumentStatusEnum.PARTIAL.getCode())
        }
        basicGroovyService.updateByPrimaryKeySelective(updateTask)
    }

    //反写申请单
    void reverseWritingOutboundApplicant(BmfObject outboundTaskDetail, BigDecimal quantity) {
        if (quantity <= 0) {
            return
        }
        def outboundTask = outboundTaskDetail.getAndRefreshBmfObject("outboundTaskId")
        def preDocumentCode = outboundTaskDetail.getString("preDocumentCode")
        def outboundApplicant = basicGroovyService.findOne("outboundApplicant", "code", preDocumentCode)
        List<BmfObject> outboundApplicantDetailList = outboundApplicant.getAndRefreshList("outboundApplicantIdAutoMapping")
        def outboundApplicantDetailFilterList = outboundApplicantDetailList.stream().filter(it -> it.getString("materialCode").equals(outboundTaskDetail.getString("materialCode"))
                && it.getString("lineNum").equals(outboundTaskDetail.getString("lineNum"))).collect(Collectors.toList())

        def outboundApplicantDetail = outboundApplicantDetailFilterList.get(0)
        log.info("返写出库申请单当前物料{}，申请单任务id{},当前出库数量:{}", outboundApplicantDetail.getString("materialCode"), outboundApplicantDetail.getLong("id"), quantity)
        //这里不用去判断了只要前面任务控制的好
        outboundApplicantDetail.put("outboundQuantity", outboundApplicantDetail.getBigDecimal("outboundQuantity").add(quantity))
        if (quantity > outboundApplicantDetail.getBigDecimal("waitQuantity")) {
            outboundApplicantDetail.put("waitQuantity", BigDecimal.ZERO)
        } else {
            outboundApplicantDetail.put("waitQuantity", outboundApplicantDetail.getBigDecimal("waitQuantity").subtract(quantity))
        }
        basicGroovyService.updateByPrimaryKeySelective(outboundApplicantDetail)
        if (outboundApplicantDetailList.stream().filter(it -> it.getBigDecimal("waitQuantity").compareTo(BigDecimal.ZERO) > 0).count() == 0) {
            outboundApplicant.put("documentStatus", "done")
        } else {
            outboundApplicant.put("documentStatus", DocumentStatusEnum.PARTIAL.getCode())
        }
        log.info("返写出库申请单状态{},当前id:{}", outboundApplicant.getString("documentStatus"), outboundApplicant.getPrimaryKeyValue())
        basicGroovyService.updateByPrimaryKeySelective(outboundApplicant)
    }

    //部分提交处理
    void partialSubmissions(BmfObject bmfObject) {
        if (passBoxesQuantity >= bmfObject.getBigDecimal("ext_pending_qty")) {
            return
        }
        def tasks = bmfObject.getAndRefreshList("tasks")
        BmfObject clone = bmfObject.deepClone()
        clone = BmfUtils.genericFromJsonExt(clone, clone.getBmfClassName())
        clone.put("targetLocationCode", null)
        clone.put("targetLocationName", null)
        clone.put("ext_pending_qty", clone.getBigDecimal("ext_pending_qty").subtract(passBoxesQuantity))
        clone.put("tasks", tasks)
        sceneGroovyService.saveBySelf(clone)
        //物流交易记录
        sceneGroovyService.saveLogisticsTransaction(Collections.singletonList(bmfObject), clone)

    }
}
