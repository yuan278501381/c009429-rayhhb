package groovy.aop

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.common.enums.DeliveryTargetTypeEnum
import com.tengnat.dwork.common.utils.CodeGenerator
import com.tengnat.dwork.common.utils.LogisticsUtils
import com.tengnat.dwork.modules.script.abstracts.AopAfterGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang3.ObjectUtils

import java.util.stream.Collectors

/**
 * 生产箱单发布生成生产备料单
 */
class AopAfterReleaseBoxOderProductStock extends AopAfterGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class)

    @Override
    void runScript(Object data) {
        if (data != null) {
            if (data instanceof JSONObject) {
                //单个箱单发布
                JSONObject boxOrder = data as JSONObject
                Long boxOderId = boxOrder.getLong("id")
                handleProductStockWithBoxOrderId(boxOderId)
            } else if (data instanceof List<Long>) {
                //批量箱单发布
                List<Long> orderIds = data as List<Long>
                orderIds.forEach(id -> handleProductStockWithBoxOrderId(id))
            }
        }
    }

    private void handleProductStockWithBoxOrderId(Long boxOrderId) {
        //查询箱单
        BmfObject boxOrder = basicGroovyService.find(BmfClassNameConst.BOX_ORDER, boxOrderId)
        if (boxOrder == null) {
            throw new BusinessException("小箱单信息不存在，无法生成生产备料单," + boxOrderId)
        }
        //生产订单
        BmfObject productOrder = basicGroovyService.findOne("productOrder", "code", boxOrder.getString("productOrderCode"))
        if (productOrder == null) {
            throw new BusinessException("生产订单信息不存在，无法生成生产备料单" + boxOrder.getString("productOrderCode"))
        }
        //生产订单物料信息
        List<BmfObject> productOrderMaterials = productOrder.getAndRefreshList("productOrderAutoMapping")
        //生产订单物料和工序对应的发出仓库
        Map<String, BmfObject> orderMaterialWarehouseType = productOrderMaterials.stream().collect(Collectors.toMap(
                processMaterial -> (String) processMaterial.getString("materialCode") + "-" + (String) processMaterial.getString("processCode"),
                processMaterial -> {
                    //仓库主数据
                    BmfObject warehouse = basicGroovyService.findOne("warehouse", "code", processMaterial.getString("warehouseCode"))
                    return warehouse == null ? new BmfObject() : warehouse
                },
                (v1, v2) -> v1
        ))

        //小箱单所有工序的原材料
        List<BmfObject> boxOrderProcesses = boxOrder.getAndRefreshList("processes")
        List<BmfObject> materials = new ArrayList<>()
        Map<String, BmfObject> boxOrderProcessMap = new HashMap<>()
        for (BmfObject boxOrderProcess : boxOrderProcesses) {
            //返工订单，则需要添加返工周转箱的物料
            if (StringUtils.equals("rework", boxOrder.getString("productOrderType"))) {
                List<BmfObject> passBoxes = boxOrderProcess.getAndRefreshList("passBoxes")
                for (BmfObject passBox : passBoxes) {
                    //去工序计划单中获取数据,外部行号先默认给0
                    BmfObject processPlanPassBox = this.basicGroovyService.find("processPlanTargetPassBox", passBox.getLong("processPlanPassBoxId"))
                    processPlanPassBox.put("processCode", boxOrderProcess.getString("processCode"))
                    processPlanPassBox.put("totalUsage", processPlanPassBox.getBigDecimal("quantity"))
                    processPlanPassBox.put("uint", processPlanPassBox.getAndRefreshBmfObject("quantityUint"))
                    materials.add(processPlanPassBox)
                }
            }
            //原材料备料
            List<BmfObject> processMaterials = boxOrderProcess.getAndRefreshList("materials")
            if (Boolean.TRUE != boxOrderProcess.getBoolean("prepareMaterial") || CollectionUtils.isEmpty(processMaterials)) {
                continue
            }
            if (CollectionUtils.isNotEmpty(processMaterials)) {
                for (BmfObject processMaterial : processMaterials) {
                    processMaterial.put("processCode", boxOrderProcess.getString("processCode"))
                }
                materials.addAll(processMaterials)
            }

            boxOrderProcessMap.put(boxOrderProcess.getString("processCode"), boxOrderProcess)
        }
        if (CollectionUtils.isEmpty(materials)) {
            return
        }

        //根据原材料编码+仓库编码分组，并累加用量
        Map<String, BmfObject> processMaterialMap = new HashMap<>()
        for (BmfObject material : materials) {
            //物料编码
            String materialCode = material.getString("materialCode")
            //工序编码
            String processCode = material.getString("processCode")
            //物料主数据
            BmfObject materialBmfObject = basicGroovyService.findOne("material", "code", materialCode)
            if (materialBmfObject == null) {
                throw new BusinessException("小箱单下的原材料信息不存在,编码:" + materialCode)
            }
            //生产订单物料和工序发出仓库
            BmfObject sendWarehouse = orderMaterialWarehouseType.get(materialCode + "-" + processCode)
            if (ObjectUtils.isEmpty(sendWarehouse)) {
                //查询物料默认仓库
                sendWarehouse = basicGroovyService.findOne("warehouse", "code", materialBmfObject.getString("defaultWarehouseCode"))
            }
            if (ObjectUtils.isEmpty(sendWarehouse)) {
                throw new BusinessException("生产订单原材料发出仓库信息不存在,编码:" + materialCode + "-" + processCode)
            }
            //仓库编码
            String warehouseCode = sendWarehouse.getString("code")
            //唯一key
            String key = materialCode + "-" + warehouseCode
            if (processMaterialMap.containsKey(key)) {
                BmfObject bmfObject = processMaterialMap.get(key)
                bmfObject.put("totalUsage", material.getBigDecimal("totalUsage").add(bmfObject.getBigDecimal("totalUsage")))
                processMaterialMap.put(key, bmfObject)
            } else {
                material.put("specifications", materialBmfObject.getString("specifications"))
                //发出仓库
                material.put("outputWarehouseCode", sendWarehouse.getString("code"))
                material.put("outputWarehouseName", sendWarehouse.getString("name"))
                processMaterialMap.put(key, material)
            }
        }

        //生产备料单
        BmfObject productionStock = new BmfObject("productionStock")
        productionStock.put("boxOrderCode", boxOrder.getString("code"))
        productionStock.put("materialCode", boxOrder.getString("materialCode"))
        productionStock.put("materialName", boxOrder.getString("materialName"))
        productionStock.put("specifications", boxOrder.getString("specifications"))
        productionStock.put("documentStatus", "untreated")
        productionStock.put("productOrderType", boxOrder.getString("productOrderType"))

        productionStock.put("sourceOrderCode", productOrder.getString("code"));
        productionStock.put("sourceSystem", productOrder.getString("sourceSys"));
        //设置单据
        LogisticsUtils.setDocumentData(boxOrder,productionStock);
        //单据业务类型
        productionStock.put("orderBusinessType", "produceOutbound");
        //生产备料明细合集
        List<BmfObject> tasks = new ArrayList<>()
        //物料+发出仓库
        Set keySet = processMaterialMap.keySet()
//        for (String key : processMaterialMap.keySet()) {
        for (int i = 0; i < keySet.size(); i++) {
//            BmfObject processMaterial = processMaterialMap.get(key)
            BmfObject processMaterial = processMaterialMap.get(keySet[i]);
            //生产备料明细
            BmfObject productionStockDetail = new BmfObject("productionStockDetail")
            //工序编码
            String processCode = processMaterial.getString("processCode")
            //备料数量
            String totalUsage = processMaterial.getString("totalUsage")
            productionStockDetail.put("productOrderType", productionStock.getString("productOrderType"))
            productionStockDetail.put("quantity", totalUsage)
            productionStockDetail.put("materialCode", processMaterial.getString("materialCode"))
            productionStockDetail.put("materialName", processMaterial.getString("materialName"))
            productionStockDetail.put("specifications", processMaterial.getString("specifications"))
            productionStockDetail.put("readyQuantity", BigDecimal.ZERO)
            productionStockDetail.put("unReadyQuantity", totalUsage)
            productionStockDetail.put("transferQuantity", BigDecimal.ZERO)
            productionStockDetail.put("unTransferQuantity", totalUsage)
            productionStockDetail.put("requisitionTypeName", "领用样品、原材料")
            productionStockDetail.put("accountCode", "6602045")
            productionStockDetail.put("accountName", "领取生产材料")
            productionStockDetail.put("productionStockCode", productionStock.getString("code"))
            productionStockDetail.put("boxOrderCode", boxOrder.getString("code"))
            productionStockDetail.put("status", "toConfirm")
            productionStockDetail.put("unit", processMaterial.getAndRefreshBmfObject("unit"))
            productionStockDetail.put("outputWarehouseCode", processMaterial.getString("outputWarehouseCode"))
            productionStockDetail.put("outputWarehouseName", processMaterial.getString("outputWarehouseName"))
            productionStockDetail.put("lineNum", i + 1)
            //转入仓库编码 -> 物料配送目标类型为仓库，工序里的配送目标
            BmfObject boxOrderProcess = boxOrderProcessMap.get(processCode)
            if (boxOrderProcess != null && DeliveryTargetTypeEnum.WAREHOUSE.getCode() == boxOrderProcess.getString("deliveryTargetType")) {
                productionStockDetail.put("inputWarehouseCode", boxOrderProcess.getString("deliveryTargetCode"))
                productionStockDetail.put("inputWarehouseName", boxOrderProcess.getString("deliveryTargetName"))
            }
            tasks.add(productionStockDetail)
        }
        codeGenerator.setCode(productionStock)
        productionStock.put("productionStockAutoMapping", tasks)
        println productionStock.get("productionStockAutoMapping")
        basicGroovyService.saveOrUpdate(productionStock)
    }

}
