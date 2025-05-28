package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.ValueUtil
import com.chinajey.dwork.common.AssistantUtils
import com.chinajey.dwork.common.FillUtils
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.common.enums.SourceSystemEnum
import com.chinajey.dwork.modules.productionReturnApplicant.service.ProductionReturnApplicantService
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.common.utils.LogisticsUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.StringUtils

import java.util.stream.Collectors

/**
 * 发起生产退料-提交脚本
 */
class NodeGN10008Submit extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    ProductionReturnApplicantService productionReturnApplicantService = SpringUtils.getBean(ProductionReturnApplicantService.class)
    @Override
    Object runScript(BmfObject nodeData) {
        //业务处理
        businessExecute(nodeData)
        //反写外部逻辑
        return nodeData
    }

    void businessExecute(BmfObject nodeData) {
        List<BmfObject> passBoxes=nodeData.getList("passBoxes")
        List<BmfObject> tasks=nodeData.getList("tasks")
        //生产订单编码
        String productionOrderCode = nodeData.getString("ext_product_order_code")
        //生产箱单编码
        String boxOrderCode = nodeData.getString("ext_box_order_code")
        //物料对应的仓库
        Map<String,BmfObject> materialWarehouseMap = tasks.stream().filter({ it -> StringUtils.isNotBlank(it.getString("ext_target_warehouse_code")) }).collect(Collectors.toMap({ it -> it.getString("materialCode") }, { it -> it}))
        //生产箱单
        BmfObject boxOrder = basicGroovyService.findOne(BmfClassNameConst.BOX_ORDER, "code", boxOrderCode)
        if (boxOrder == null) {
            throw new BusinessException("生产箱单不存在,编码：" + boxOrderCode)
        }
        //生产订单
        BmfObject productOrder = basicGroovyService.findOne(BmfClassNameConst.PRODUCT_ORDER, "code", productionOrderCode)
        if (productOrder == null) {
            throw new BusinessException("生产订单不存在,编码：" + productionOrderCode)
        }
        List<BmfObject> productOrderMaterials = productOrder.getAndRefreshList("productOrderAutoMapping")
        //物料对应的行号
        Map<String,String> materialLineNumMap = productOrderMaterials.stream().collect(Collectors.toMap({ it -> it.getString("materialCode") }, { it -> ValueUtil.toStr( it.getString("lineNum"),"1") }))

        //生产退料申请单周转箱
        List<BmfObject> applicantPassBoxs = new ArrayList<>()
        for (BmfObject passBox : passBoxes) {
            BmfObject applicantPassBox = new BmfObject("productionReturnApplicantPassBox")
            applicantPassBox.put("passBoxRealCode", passBox.get("code"))
            applicantPassBox.put("passBoxCode", passBox.get("passBoxCode"))
            applicantPassBox.put("passBoxName", passBox.get("passBoxName"))
            applicantPassBox.put("materialCode", passBox.get("materialCode"))
            applicantPassBox.put("materialName", passBox.get("materialName"))
            applicantPassBox.put("specifications", passBox.get("specifications"))
            applicantPassBox.put("unit", passBox.get("quantityUnit"))
            applicantPassBox.put("quantity", passBox.get("quantity"))
            //物料对应的仓库
            BmfObject materialWarehouse =materialWarehouseMap.get(passBox.get("materialCode"))
            applicantPassBox.put("targetWarehouseCode", materialWarehouse.get("ext_target_warehouse_code"))
            applicantPassBox.put("targetWarehouseName", materialWarehouse.get("ext_target_warehouse_name"))
            applicantPassBoxs.add(applicantPassBox)
        }
        //根据物料分组
        Map<String,List<BmfObject>> applicantPassBoxsMap = applicantPassBoxs.stream().collect(Collectors.groupingBy({ it -> it.get("materialCode") }))
        //生产退料申请单明细
        List<BmfObject> applicantDetails = new ArrayList<>()
        for (String key:applicantPassBoxsMap.keySet()){
            //退料数量
            BigDecimal planReturnQuantity= applicantPassBoxsMap.get(key).stream().map(passBox -> passBox.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
            BmfObject materialPassBox = applicantPassBoxsMap.get(key).get(0)

            BmfObject applicantDetail=new BmfObject("productionReturnApplicantDetail")
            applicantDetail.put("lineNum", ValueUtil.toStr(materialLineNumMap.get(materialPassBox.get("materialCode")),"1"))
            applicantDetail.put("materialCode", materialPassBox.get("materialCode"))
            applicantDetail.put("materialName", materialPassBox.get("materialName"))
            applicantDetail.put("specifications", materialPassBox.get("specifications"))
            applicantDetail.put("unit", materialPassBox.get("unit"))
            applicantDetail.put("planReturnQuantity", planReturnQuantity)
            applicantDetail.put("returnedQuantity", new BigDecimal(0))
            applicantDetail.put("targetWarehouseCode", materialPassBox.get("targetWarehouseCode"))
            applicantDetail.put("targetWarehouseName", materialPassBox.get("targetWarehouseCode"))
            //设置单据数据
            LogisticsUtils.setDocumentData(boxOrder, applicantDetail)
            applicantDetails.add(applicantDetail)
        }

        //生产退料申请单
        BmfObject productionReturnApplicant = new BmfObject("productionReturnApplicant")
        productionReturnApplicant.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode())
        productionReturnApplicant.put("sourceSystem", SourceSystemEnum.DWORK.getCode())
        productionReturnApplicant.put("sourceDocumentType", AssistantUtils.getSplitValues(applicantDetails, "sourceDocumentType"))
        productionReturnApplicant.put("sourceDocumentCode", AssistantUtils.getSplitValues(applicantDetails, "sourceDocumentCode"))
        productionReturnApplicant.put("externalDocumentType", AssistantUtils.getSplitValues(applicantDetails, "externalDocumentType"))
        productionReturnApplicant.put("externalDocumentCode", AssistantUtils.getSplitValues(applicantDetails, "externalDocumentCode"))
        productionReturnApplicant.put("preDocumentType", AssistantUtils.getSplitValues(applicantDetails, "preDocumentType"))
        productionReturnApplicant.put("preDocumentCode", AssistantUtils.getSplitValues(applicantDetails, "preDocumentCode"))
        productionReturnApplicant.put("processCode",nodeData.getString("ext_process_code"))
        productionReturnApplicant.put("processName",nodeData.getString("ext_process_name"))
        productionReturnApplicant.put("productionReturnApplicantPassBoxIdAutoMapping",applicantPassBoxs)
        productionReturnApplicant.put("productionReturnApplicantDetailIdAutoMapping",applicantDetails)
        FillUtils.fillOperator(productionReturnApplicant)
        productionReturnApplicantService.save(productionReturnApplicant)
    }
}
