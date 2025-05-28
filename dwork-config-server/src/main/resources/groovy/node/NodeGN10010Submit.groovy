package groovy.node

import cn.hutool.core.collection.CollectionUtil
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.common.utils.BusinessUtils
import com.chinajey.dwork.modules.warehousingApplicant.service.WarehousingApplicantService
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.common.utils.BigDecimalUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils

/**
 * 销售退货接收-提交脚本
 */
class NodeGN10010Submit extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    BusinessUtils businessUtils = SpringUtils.getBean(BusinessUtils.class)
    WarehousingApplicantService warehousingApplicantService = SpringUtils.getBean(WarehousingApplicantService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        updateSalesReturnApplication(nodeData)
        return nodeData
    }

    private void updateSalesReturnApplication(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        passBoxes.forEach {
            if (it.getBigDecimal("receiveQuantity") == null || it.getBigDecimal("receiveQuantity") <= BigDecimal.ZERO) throw new BusinessException("请填写接收数量")
        }
        if (CollectionUtil.isEmpty(passBoxes)) throw new BusinessException("请扫描周转箱或添加虚拟周转箱")
        BigDecimal receiveQuantity = passBoxes.sum(it -> it.getBigDecimal("receiveQuantity")) as BigDecimal
        if (receiveQuantity <= BigDecimal.ZERO) throw new BusinessException("请输入接收数量")
        if (receiveQuantity > nodeData.getBigDecimal("ext_return_quantity")) throw new BusinessException("本次接收数量超过待接收数量")
        //查询销售退货申请单
        String ext_return_order_code = nodeData.getString("sourceDocumentCode")
        if (StringUtils.isBlank(ext_return_order_code)) throw new BusinessException("销售退货申请单编码不存在")
        BmfObject salesReturnApplicant = basicGroovyService.getByCode("salesReturnApplicant", ext_return_order_code)
        if (salesReturnApplicant == null) throw new BusinessException("销售退货申请单不存在")
        List<BmfObject> details = salesReturnApplicant.getAndRefreshList("salesReturnApplicantDetailIdAutoMapping")
        if (CollectionUtil.isEmpty(details)) throw new BusinessException("销售退货单${ext_return_order_code}明细不存在")
        String ext_target_warehouse_code = nodeData.getString("ext_target_warehouse_code")
        if (StringUtils.isEmpty(ext_target_warehouse_code)) throw new BusinessException("仓库编码不存在")
        List<BmfObject> tasks = nodeData.getList("tasks")
        if (CollectionUtil.isEmpty(tasks)) throw new BusinessException("销售退货任务信息不存在")
        String materialCode = tasks.get(0).getString("materialCode")
        List<BmfObject> filteredDetails = details.findAll { it.getString("warehouseCode") == ext_target_warehouse_code && it.getString("materialCode") == materialCode && it.getBigDecimal("returnQuantity") != it.getBigDecimal("receivedQuantity") }
        if (CollectionUtil.isEmpty(filteredDetails)) throw new BusinessException("对应明细信息不存在")
        List<BmfObject> updateDetails = new ArrayList<>()
        List<BmfObject> sortDetails = filteredDetails.sort { it.getBigDecimal("lineNum").intValue() }
        passBoxes.forEach { it.put("handleQuantity", it.getBigDecimal("receiveQuantity")) }
        for (BmfObject detail : sortDetails) {
            BigDecimal noReceivedQuantity = detail.getBigDecimal("noReceivedQuantity")
            passBoxes.findAll { it.getBigDecimal("newHandleQuantity") != null && it.getBigDecimal("newHandleQuantity") > BigDecimal.ZERO }.forEach {
                it.put("handleQuantity", it.getBigDecimal("newHandleQuantity"))
            }
            List<BmfObject> remainingPassBox = passBoxes.findAll { it.getBigDecimal("handleQuantity") != null && it.getBigDecimal("handleQuantity") > BigDecimal.ZERO }
            if (CollectionUtil.isEmpty(remainingPassBox)) continue
            BigDecimal remainingReceiveQuantity = remainingPassBox.sum { it.getBigDecimal("handleQuantity") } as BigDecimal
            if (remainingReceiveQuantity > noReceivedQuantity) {
                List<BmfObject> remainingNewPassBox = new ArrayList<>()
                BigDecimal sumHandleQuantity = BigDecimal.ZERO
                for (BmfObject it : remainingPassBox) {
                    BigDecimal handleQuantity = it.getBigDecimal("handleQuantity")
                    if (sumHandleQuantity + handleQuantity > noReceivedQuantity) {
                        BigDecimal newHandleQuantity = noReceivedQuantity - sumHandleQuantity
                        it.put("handleQuantity", newHandleQuantity)
                        it.put("newHandleQuantity", handleQuantity - newHandleQuantity)
                        remainingNewPassBox.add(it)
                        break
                    } else {
                        sumHandleQuantity += handleQuantity
                        remainingNewPassBox.add(it)
                    }
                }
                updateData(detail, remainingNewPassBox)
                updateDetails.add(detail)
            } else {
                updateData(detail, remainingPassBox)
                updateDetails.add(detail)
                break
            }
        }

        basicGroovyService.saveOrUpdate(updateDetails)

        businessUtils.updateStatus(salesReturnApplicant, DocumentStatusEnum.PARTIAL.getCode())
        //更新本次任务的待接收数量
        nodeData.put("ext_return_quantity", nodeData.getBigDecimal("ext_return_quantity") - receiveQuantity)
        basicGroovyService.updateByPrimaryKeySelective(nodeData)
        //生成入库申请单
        createAndIssuedInOrder(nodeData, updateDetails)
        //支持分批提交
        if (nodeData.getBigDecimal("ext_return_quantity") > BigDecimal.ZERO) {
            BmfObject clone = nodeData.deepClone()
            clone.put("targetLocationCode", null)
            clone.put("targetLocationName", null)
            clone = BmfUtils.genericFromJsonExt(clone, clone.getBmfClassName())
            sceneGroovyService.saveBySelf(clone)
            //物流交易记录
            sceneGroovyService.saveLogisticsTransaction(Collections.singletonList(nodeData), clone)
        }
    }

    private void updateData(BmfObject detail, List<BmfObject> passBoxes) {
        BigDecimal receiveQuantity = passBoxes.sum(it -> it.getBigDecimal("handleQuantity")) as BigDecimal
        BigDecimal ext_return_quantity = detail.getBigDecimal("noReceivedQuantity")
        //更新明细中的待接收数量和已接收数量
        BigDecimal noReceivedQuantity = ext_return_quantity - receiveQuantity
        detail.put("noReceivedQuantity", noReceivedQuantity)
        BigDecimal receivedQuantity = detail.getBigDecimal("receivedQuantity") + receiveQuantity
        detail.put("receivedQuantity", receivedQuantity)
        detail.put("waitQuantity", BigDecimalUtils.add(detail.getBigDecimal("waitQuantity"), receiveQuantity))
        if (detail.getBigDecimal("warehousingQuantity") <= BigDecimal.ZERO) {
            detail.put("warehousingQuantity", BigDecimal.ZERO)
        }
        passBoxes.forEach { it.put("handleQuantity", null) }
        //本次接收数量
        detail.put("receiveQuantity", receiveQuantity)
        /*
        //明细周转箱
        List<BmfObject> detailPassBoxes = detail.getAndRefreshList("salesReturnApplicantPassBoxIdAutoMapping")
        List<JSONObject> detailNewPassBoxes = new ArrayList<>() ·
        detailNewPassBoxes.addAll(detailPassBoxes)
        detail.put("salesReturnApplicantPassBoxIdAutoMapping", detailNewPassBoxes)
        passBoxes.forEach {
            JSONObject existBox = detailNewPassBoxes.find(subIt -> subIt.getString("passBoxCode") == it.getString("passBoxCode"))
            if (existBox != null) {
                //数量合并
                existBox.put("quantity", existBox.getBigDecimal("quantity") + it.getBigDecimal("handleQuantity"))
            } else {
                BmfObject passBoxReal = basicGroovyService.findOne(BmfClassNameConst.PASS_BOX_REAL, "passBoxCode", it.getString("passBoxCode"))
                if (passBoxReal == null) throw new BusinessException("周转箱${it.getString("passBoxCode")}实时信息不存在")
                BmfObject passBox = new BmfObject("salesReturnApplicantPassBox")
                passBox.put("passBoxCode", passBoxReal.getString("passBoxCode"))
                passBox.put("passBoxName", passBoxReal.getString("passBoxName"))
                passBox.put("passBoxRealCode", passBoxReal.getString("code"))
                passBox.put("quantity", it.getBigDecimal("handleQuantity"))
                BmfObject materialObject = passBoxReal.getAndRefreshBmfObject("material")
                if (materialObject == null) {
                    materialObject = basicGroovyService.findOne(BmfClassNameConst.MATERIAL, "code", passBoxReal.getString("materialCode"))
                }
                if (materialObject != null) {
                    passBox.put("unit", materialObject.getAndRefreshBmfObject("flowUnit"))
                }
                UpdateDataUtils.updateOperateInfo(passBox)
                detailNewPassBoxes.add(passBox)
            }
            it.put("handleQuantity", null)
        }

         */
    }

    //自动创建并下达销售退货类型的入库申请单
    private void createAndIssuedInOrder(BmfObject nodeData, List<JSONObject> details) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        List<BmfObject> tasks = nodeData.getList("tasks")
        JSONObject task = tasks.get(0)
        String ext_target_warehouse_code = nodeData.getString("ext_target_warehouse_code")
        String ext_target_warehouse_name = nodeData.getString("ext_target_warehouse_name")
        BmfObject material = basicGroovyService.getByCode(BmfClassNameConst.MATERIAL, task.getString("materialCode"))
        if (material == null) throw new BusinessException("物料主数据信息不存在")

        JSONObject warehousingApplicant = new BmfObject("warehousingApplicant")
        warehousingApplicant.put("orderBusinessType", "salesReturn") //单据业务类型
        warehousingApplicant.put("sourceDocumentType", "salesReturnApplicant") //源头内部单据类型
        warehousingApplicant.put("sourceDocumentCode", nodeData.getString("sourceDocumentCode")) //源头内部单据编码
        warehousingApplicant.put("preDocumentType", nodeData.getString("preDocumentType"))
        warehousingApplicant.put("preDocumentCode", nodeData.getString("preDocumentCode"))
        warehousingApplicant.put("externalDocumentType", nodeData.getString("externalDocumentType"))
        warehousingApplicant.put("externalDocumentCode", nodeData.getString("externalDocumentCode"))

        List<JSONObject> warehousingApplicantIdAutoMapping = new ArrayList<>()
        warehousingApplicant.put("warehousingApplicantIdAutoMapping", warehousingApplicantIdAutoMapping)
        details.forEach {
            JSONObject detail = new JSONObject()
            detail.put("materialCode", material.getString("code"))
            detail.put("materialName", material.getString("name"))
            detail.put("specifications", material.get("specifications"))
            detail.put("unit", material.get("flowUnit"))
            detail.put("targetWarehouseName", ext_target_warehouse_name)
            detail.put("targetWarehouseCode", ext_target_warehouse_code)
            detail.put("planQuantity", it.getBigDecimal("receiveQuantity"))
            detail.put("warehousingQuantity", BigDecimal.ZERO)
            detail.put("waitQuantity", it.getBigDecimal("receiveQuantity"))
            detail.put("lineNum", it.get("lineNum"))
            detail.put("sourceDocumentType", "salesReturnApplicant") //源头内部单据类型
            detail.put("sourceDocumentCode", nodeData.getString("sourceDocumentCode")) //源头内部单据编码
            detail.put("preDocumentType", "salesReturnApplicant")
            detail.put("preDocumentCode", nodeData.getString("sourceDocumentCode"))
            detail.put("externalDocumentType", nodeData.getString("externalDocumentType"))
            detail.put("externalDocumentCode", nodeData.getString("externalDocumentCode"))
            warehousingApplicantIdAutoMapping.add(detail)
        }
        //设置入库申请单周转箱
        List<JSONObject> warehousingApplicantPassBoxs= new ArrayList<>()
        passBoxes.forEach(it -> {
            JSONObject applicantPassBox = new JSONObject()
            applicantPassBox.put("warehouseCode", nodeData.getString("ext_target_warehouse_code"))
            applicantPassBox.put("warehouseName", nodeData.getString("ext_target_warehouse_name"))
            applicantPassBox.put("materialCode", it.getString("materialCode"))
            applicantPassBox.put("materialName", it.getString("materialName"))
            applicantPassBox.put("specifications", it.getString("specifications"))
            applicantPassBox.put("passBoxRealCode", it.getString("code"))
            applicantPassBox.put("passBoxCode", it.getString("passBoxCode"))
            applicantPassBox.put("passBoxName", it.getString("passBoxName"))
            applicantPassBox.put("quantity", it.getBigDecimal("quantity"))
            applicantPassBox.put("unit", it.getBmfObject("quantityUnit"))
            applicantPassBox.put("documentStatus", DocumentStatusEnum.PENDING.getCode())
            warehousingApplicantPassBoxs.add(applicantPassBox)
        })
        warehousingApplicant.put("warehousingApplicantPassBoxIdAutoMapping", warehousingApplicantPassBoxs)
        warehousingApplicant = warehousingApplicantService.save(warehousingApplicant)
        if (warehousingApplicant != null) {
            //下达
            warehousingApplicantService.issued(Arrays.asList(warehousingApplicant.getPrimaryKeyValue()))
        }
    }


}
