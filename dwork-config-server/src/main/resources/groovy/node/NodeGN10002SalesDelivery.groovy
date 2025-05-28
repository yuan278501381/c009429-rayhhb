package groovy.node

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.common.AssistantUtils
import com.chinajey.dwork.common.utils.WriteBackUtils
import com.tengnat.dwork.common.utils.BigDecimalUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.collections4.CollectionUtils
import org.springframework.jdbc.core.JdbcTemplate

import java.util.stream.Collectors

/**
 * 出库任务-创建销售发货任务
 */
class NodeGN10002SalesDelivery extends NodeGroovyClass {

    JdbcTemplate jdbcTemplate = SpringUtils.getBean(JdbcTemplate.class)

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        // 源头单据类型
        String sourceDocumentType = nodeData.getString("sourceDocumentType")
        if (sourceDocumentType != "salesDelivery") {
            return nodeData
        }
        // 出库单 - 一个出库单只会有一种物料
        BmfObject outboundOrder = nodeData.getBmfObject("_result_order")
        if (outboundOrder == null) {
            return nodeData
        }
        // 出库任务单
        BmfObject outboundTask = basicGroovyService.find("outboundTask", Long.valueOf(nodeData.getString("ext_outbound_task_code")))
        if (outboundTask == null) {
            throw new BusinessException("PC出库任务[" + nodeData.getString("ext_outbound_task_code") + "]不存在")
        }

        List<BmfObject> submitPassBoxes = nodeData.getList("passBoxes")
        String warehouseCode = outboundOrder.getString("sourceWarehouseCode")
        // 出库单明细 - 肯定会根据来源单据 + 物料的维度，所以这边不需要分组
        List<BmfObject> outboundOrderDetails = outboundOrder.getList("outboundOrderDetailIdAutoMapping")
        for (final def outboundOrderDetail in outboundOrderDetails) {
            if (outboundOrderDetail.getString("sourceDocumentType") != "salesDelivery") {
                continue
            }
            String deliveryPlanCode = outboundOrderDetail.getString("sourceDocumentCode")
            BmfObject deliveryPlan = this.basicGroovyService.findOne("salesDeliveryPlan", "code", deliveryPlanCode)
            if (deliveryPlan == null) {
                throw new BusinessException("销售发货计划[" + deliveryPlanCode + "]不存在")
            }
            BigDecimal quantity = outboundOrderDetail.getBigDecimal("quantity")
            String materialCode = outboundOrderDetail.getString("materialCode")
            List<BmfObject> allDeliveryPlanDetails = deliveryPlan.getAndRefreshList("salesDeliveryPlanDetailIdAutoMapping")
            List<BmfObject> deliveryPlanDetails = allDeliveryPlanDetails
                    .stream()
                    .filter { it -> it.getString("materialCode") == materialCode && it.getString("sourceWarehouseCode") == warehouseCode }
                    .collect(Collectors.toList())
            WriteBackUtils.writeBack(deliveryPlanDetails, quantity, "outboundQuantity", "waitOutboundQuantity", "waitShippedQuantity")

            List<Map<String, Object>> mapInfos = this.jdbcTemplate.queryForList(
                    "select t1.id " +
                            "from dwk_logistics_custom_gn10009_ext t0 " +
                            "inner join dwk_logistics_custom_gn10009 t1 on t1.id = t0.ext_GN10009_id " +
                            "where t0.is_delete = 0 and t1.is_delete = 0 and t1.logistics_status in ('1', '2') " +
                            "and t0.ext_shipping_order_code = ? and t0.ext_material_code = ? and ext_warehouse_code = ? limit 1",
                    deliveryPlanCode, materialCode, warehouseCode
            )
            if (CollectionUtils.isNotEmpty(mapInfos)) {
                long gn10009Id = (Long) mapInfos.get(0).get("id")
                BmfObject gn10009 = this.basicGroovyService.find("GN10009", gn10009Id)
                BigDecimal waitQuantity = gn10009.getBigDecimal("ext_unshipped_quantity")
                gn10009.put("ext_unshipped_quantity", BigDecimalUtils.add(waitQuantity, quantity))
                String newValues = AssistantUtils.getSplitValues(submitPassBoxes, "code")
                gn10009.put("ext_pass_box_real_codes", AssistantUtils.getSplitValues(gn10009.getString("ext_pass_box_real_codes"), newValues, ","))
                this.basicGroovyService.updateByPrimaryKeySelective(gn10009)
            } else {
                BmfObject material = this.basicGroovyService.findOne("material", "code", materialCode)
                JSONObject jsonObject = new JSONObject()
                jsonObject.put("externalDocumentType", outboundOrderDetail.getString("externalDocumentType"))
                jsonObject.put("externalDocumentCode", outboundOrderDetail.getString("externalDocumentCode"))
                jsonObject.put("sourceDocumentType", outboundOrderDetail.getString("sourceDocumentType"))
                jsonObject.put("sourceDocumentCode", outboundOrderDetail.getString("sourceDocumentCode"))
                jsonObject.put("dataSourceType", outboundTask.getBmfClassName())
                jsonObject.put("dataSourceCode", outboundTask.getPrimaryKeyValue())
                jsonObject.put("preDocumentType", outboundTask.getString("preDocumentType"))
                jsonObject.put("preDocumentCode", outboundTask.getString("preDocumentCode"))
                jsonObject.put("sourceLocationCode", nodeData.getString("targetLocationCode"))
                jsonObject.put("sourceLocationName", nodeData.getString("targetLocationName"))
                jsonObject.put("ext_shipping_order_code", deliveryPlanCode)
                jsonObject.put("ext_order_date", deliveryPlan.getDate("orderDate"))
                jsonObject.put("ext_delivery_date", deliveryPlan.getDate("deliveryDate"))
                jsonObject.put("ext_customer_code", deliveryPlan.getString("customerCode"))
                jsonObject.put("ext_customer_name", deliveryPlan.getString("customerName"))
                jsonObject.put("ext_material_code", material.getString("code"))
                jsonObject.put("ext_material_name", material.getString("name"))
                jsonObject.put("ext_location_code", nodeData.getString("targetLocationCode"))
                jsonObject.put("ext_location_name", nodeData.getString("targetLocationName"))
                jsonObject.put("ext_warehouse_code", warehouseCode)
                jsonObject.put("ext_warehouse_name", outboundOrder.getString("sourceWarehouseName"))
                jsonObject.put("ext_unshipped_quantity", quantity)
                jsonObject.put("ext_pass_box_real_codes", AssistantUtils.getSplitValues(submitPassBoxes, "code"))

                // 构建 tasks
                JSONArray tasks = new JSONArray()
                BmfObject task = new BmfObject("GN10009Tasks")
                task.put("materialCode", material.getString("code"))
                task.put("materialName", material.getString("name"))
                task.put("material", material)
                task.put("quantityUnit", material.getBmfObject("flowUnit"))
                tasks.add(task)
                jsonObject.put("tasks", tasks)
                this.sceneGroovyService.buzSceneStart("GN10009", "BS10008", jsonObject)
            }
        }
        return nodeData
    }
}
