package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.ValueUtil
import com.chinajey.application.script.exception.ScriptInterruptedException
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils

import java.util.stream.Collectors

//出库待确认提交脚本
class NodeGN10001Submit extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //兼容批量提交
        List<BmfObject> batchList = nodeData.getList("batchList")
        if (batchList == null || batchList.size() == 0) {
            batchList = new ArrayList<>()
            batchList.add(nodeData)
        }
        //前置校验
        validate(batchList)
        //业务处理
        businessExecute(batchList)
        //手动流转数据
        throw new ScriptInterruptedException("手动创建渠道小件拣配任务,不流转任务")
    }

    /**
     * 前置校验
     * @param bmfObjects
     */
    void validate(List<BmfObject> bmfObjects) {

    }

    /**
     * 业务处理
     * @param bmfObjects
     */
    void businessExecute(List<BmfObject> batchList) {
        //同类型同仓库合并
        Map<String, List<BmfObject>> objMaps = batchList.stream().collect(Collectors.groupingBy(
                it -> ValueUtil.toStr(((BmfObject) it).getString("ext_order_business_type")) +
                        ValueUtil.toStr(((BmfObject) it).getString("ext_source_warehouse_code")) +
                        ValueUtil.toStr(((BmfObject) it).getString("materialCode"))
        ))
        for (final def key in objMaps.keySet()) {
            def objs = objMaps.get(key)
            //创建出库任务
            def task = createOutboundTask(objs)
            //处理流转数据
            processdata(objs, task)

        }
    }

    //创建出库任务
    BmfObject createOutboundTask(List<BmfObject> bmfObjects) {
        def outboundTaskDetailList = new ArrayList<BmfObject>()
        def material = basicGroovyService.getByCode("material", bmfObjects.get(0).getString("materialCode"))
        if (material == null) {
            throw new BusinessException("物料[" + bmfObjects.get(0).getString("materialCode") + "]不存在")
        }
        //组装子表
        for (final def bmfObject in bmfObjects) {
            def one = basicGroovyService.findOne(bmfObject.getString("preDocumentType"), "code", bmfObject.getString("preDocumentCode"))
            List<BmfObject> outboundApplicantDetails = one.getAndRefreshList("outboundApplicantIdAutoMapping")
            List<BmfObject> outboundApplicantDetailByMaterialCodeAndSourceWarehouseCode = outboundApplicantDetails
                    .stream()
                    .filter { it -> it.getString("materialCode") == bmfObject.getString("materialCode") && it.getString("sourceWarehouseCode") == bmfObject.getString("ext_source_warehouse_code") }
                    .collect(Collectors.toList())
            for (final def outboundApplicantDetail in outboundApplicantDetailByMaterialCodeAndSourceWarehouseCode) {
                def outboundTaskDetail = new BmfObject("outboundTaskDetail")
                outboundTaskDetail.put("materialName", bmfObject.getString("materialName"))
                outboundTaskDetail.put("materialCode", bmfObject.getString("materialCode"))
                outboundTaskDetail.put("specifications", material.getString("specifications"))
                outboundTaskDetail.put("unit", material.get("flowUnit"))
                outboundTaskDetail.put("sourceWarehouseCode", outboundApplicantDetail.getString("sourceWarehouseCode"))
                outboundTaskDetail.put("sourceWarehouseName", outboundApplicantDetail.getString("sourceWarehouseName"))
                outboundTaskDetail.put("targetWarehouseCode", outboundApplicantDetail.getString("targetWarehouseCode"))
                outboundTaskDetail.put("targetWarehouseName", outboundApplicantDetail.getString("targetWarehouseName"))
                outboundTaskDetail.put("planQuantity", outboundApplicantDetail.getBigDecimal("planQuantity"))
                outboundTaskDetail.put("outboundQuantity", outboundApplicantDetail.getBigDecimal("outboundQuantity"))
                outboundTaskDetail.put("waitQuantity", outboundApplicantDetail.getBigDecimal("planQuantity").subtract(outboundApplicantDetail.getBigDecimal("outboundQuantity")))
                outboundTaskDetail.put("preDocumentCode", bmfObject.getString("preDocumentCode"))
                outboundTaskDetail.put("preDocumentType", bmfObject.getString("preDocumentType"))
                outboundTaskDetail.put("externalDocumentType", one.getString("externalDocumentType"))
                outboundTaskDetail.put("externalDocumentCode", one.getString("externalDocumentCode"))
                if (one.getString("sourceDocumentType") != null) {
                    outboundTaskDetail.put("sourceDocumentType", one.getString("sourceDocumentType"))
                    outboundTaskDetail.put("sourceDocumentCode", one.getString("sourceDocumentCode"))
                } else {
                    outboundTaskDetail.put("sourceDocumentType", outboundTaskDetail.getString("preDocumentType"))
                    outboundTaskDetail.put("sourceDocumentCode", outboundTaskDetail.getString("preDocumentCode"))
                }
                outboundTaskDetail.put("lineNum", outboundApplicantDetail.getString("lineNum"))
                outboundTaskDetailList.add(outboundTaskDetail)
            }
        }
        def outboundTask = new BmfObject("outboundTask")
        outboundTask.put("materialName", bmfObjects.get(0).getString("materialName"))
        outboundTask.put("materialCode", bmfObjects.get(0).getString("materialCode"))
        outboundTask.put("specifications", material.getString("specifications"))
        outboundTask.put("unit", material.get("flowUnit"))
        //计划出库总数
        outboundTask.put("planQuantity", outboundTaskDetailList.stream().map(t -> t.getBigDecimal("planQuantity")).reduce(BigDecimal.ZERO, BigDecimal::add))
        outboundTask.put("outboundQuantity", outboundTaskDetailList.stream().map(t -> t.getBigDecimal("outboundQuantity")).reduce(BigDecimal.ZERO, BigDecimal::add))
        outboundTask.put("waitQuantity", outboundTask.getBigDecimal("planQuantity").subtract(outboundTask.getBigDecimal("outboundQuantity")))
        outboundTask.put("externalDocumentType", outboundTaskDetailList.get(0).getString("externalDocumentType"))
        outboundTask.put("externalDocumentCode", outboundTaskDetailList.stream().map(t -> t.getString("externalDocumentCode")).filter(t -> StringUtils.isNotBlank(t)).distinct().collect(Collectors.joining(",")))
        outboundTask.put("sourceDocumentCode", outboundTaskDetailList.stream().map(t -> t.getString("sourceDocumentCode")).filter(t -> StringUtils.isNotBlank(t)).distinct().collect(Collectors.joining(",")))
        outboundTask.put("sourceDocumentType", outboundTaskDetailList.get(0).getString("sourceDocumentType"))
        outboundTask.put("preDocumentCode", outboundTaskDetailList.stream().map(t -> t.getString("preDocumentCode")).filter(t -> StringUtils.isNotBlank(t)).distinct().collect(Collectors.joining(",")))
        outboundTask.put("preDocumentType", outboundTaskDetailList.get(0).getString("preDocumentType"))
        outboundTask.put("orderBusinessType", bmfObjects.get(0).getString("ext_order_business_type"))
        outboundTask.put("sourceWarehouseCode", bmfObjects.get(0).getString("ext_source_warehouse_code"))
        outboundTask.put("sourceWarehouseName", bmfObjects.get(0).getString("ext_source_warehouse_name"))
        outboundTask.put("documentStatus", DocumentStatusEnum.PENDING.getCode())
        outboundTask.put("outboundTaskIdAutoMapping", outboundTaskDetailList)
        basicGroovyService.saveOrUpdate(outboundTask)
        return outboundTask
    }

    BmfObject processdata(List<BmfObject> nodeDatas, BmfObject outboundTask) {
        BmfObject nodeData = nodeDatas.get(0)
        def material = basicGroovyService.getByCode("material", outboundTask.getString("materialCode"))
        def planQuantity = outboundTask.getBigDecimal("planQuantity")
        String preDocumentCodes = outboundTask.getString("preDocumentCode")
        def task = new BmfObject("GN10002Tasks")
        task.put("materialCode", material.getString("code"))
        task.put("materialName", material.getString("name"))
        task.put("material", material)
        task.put("quantityUnit", material.get("flowUnit"))
        task.put("ext_pending_qty", planQuantity)
        task.put("ext_outbound_applicant_codes", preDocumentCodes)
        BmfObject buzSceneInstanceNode = nodeData.getBmfObject("nextInstanceNode")
        def GN10002 = new BmfObject("GN10002")
        GN10002.put("buzSceneInstanceNode", buzSceneInstanceNode)
        GN10002.put("ext_material_type_count", 1)
        GN10002.put("ext_material_name", outboundTask.getString("materialName"))
        GN10002.put("ext_material_code",outboundTask.getString("materialCode"))
        GN10002.put("ext_specifications", outboundTask.getString("specifications"))
        GN10002.put("ext_pending_qty", planQuantity)
        GN10002.put("ext_source_warehouse_code", outboundTask.getString("sourceWarehouseCode"))
        GN10002.put("ext_source_warehouse_name", outboundTask.getString("sourceWarehouseName"))
        GN10002.put("ext_order_business_type", nodeData.getString("ext_order_business_type"))
        GN10002.put("logisticsMoveBusinessType", nodeData.getString("ext_order_business_type"))
        GN10002.put("logisticsStatus", "1")
        GN10002.put("preDocumentType", outboundTask.getString("preDocumentType"))
        GN10002.put("preDocumentCode", outboundTask.getString("preDocumentCode"))
        GN10002.put("sourceDocumentType", outboundTask.getString("sourceDocumentType"))
        GN10002.put("sourceDocumentCode", outboundTask.getString("sourceDocumentCode"))
        GN10002.put("externalDocumentType", outboundTask.getString("externalDocumentType"))
        GN10002.put("externalDocumentCode", outboundTask.getString("externalDocumentCode"))
        //出库任务编码
        GN10002.put("ext_outbound_task_code", String.valueOf(outboundTask.getPrimaryKeyValue()))
        List<BmfObject> tasks = new ArrayList<>()
        tasks.add(task)
        GN10002.put("tasks", tasks)
        basicGroovyService.saveOrUpdate(GN10002)
        //物流交易记录
        sceneGroovyService.saveLogisticsTransaction(nodeDatas, GN10002)

    }
}
