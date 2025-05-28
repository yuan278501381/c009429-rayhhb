package groovy.node

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.holder.UserAuthDto
import com.chinajey.application.common.holder.UserHolder
import com.chinajey.application.common.utils.ValueUtil
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.tengnat.dwork.common.utils.BigDecimalUtils
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import java.util.stream.Collectors

/**
 * 采购退货任务提交脚本
 @author erton.bi
 */
class NodeGN10006Submit extends NodeGroovyClass{

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //前置校验
        validate(nodeData)
        //业务处理
        businessExecute(nodeData)
        //反写外部逻辑
        return nodeData
    }

    void validate(BmfObject nodeData) {
        //校验数量
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        BigDecimal totalQuantity = passBoxes.stream().map(it -> it.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
        if (totalQuantity <= BigDecimal.ZERO) {
            throw new BusinessException("本次退货数量不能为0")
        }
        nodeData.putUncheck("totalQuantity", totalQuantity)

        //待退货数量
        BigDecimal pendingReturnNumber = ValueUtil.toBigDecimal(nodeData.getBigDecimal("ext_pending_return_number"))
        if (pendingReturnNumber <= BigDecimal.ZERO) {
            throw new BusinessException("数据异常,待退货数量不能小于等于0")
        }
        if (totalQuantity > pendingReturnNumber) {
            throw new BusinessException("本次退货数量不能大于待退货数量")
        }

        //校验采购退货申请单
        BmfObject purchaseReturnApplication = basicGroovyService.getByCode("purchaseReturnApplication", nodeData.getString("preDocumentCode"))
        if (purchaseReturnApplication == null) {
            throw new BusinessException("数据异常,采购退货申请单不存在")
        }
        nodeData.putUncheck("purchaseReturnApplication", purchaseReturnApplication)

        //来源系统-mes 来源单据-采购订单 TODO
//        if ("mes" == purchaseReturnApplication.getString("sourceSys") && "purchaseOrder" == purchaseReturnApplication.getString("sourceOrderType")) {
//            BmfObject purchaseOrder = basicGroovyService.findOne("purchaseOrder","purchaseOrderCode", purchaseReturnApplication.getString("sourceOrderCode"))
//            if (purchaseOrder == null) {
//                throw new BusinessException("数据异常,来源单据采购订单不存在")
//            }
//            nodeData.putUncheck("purchaseOrder", purchaseOrder)
//        }
    }

    void businessExecute(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        //周转箱总数量
        BigDecimal totalQuantity = nodeData.getBigDecimal("totalQuantity")
        String materialCode = nodeData.getString("ext_material_code")
        BmfObject purchaseReturnApplication = nodeData.getBmfObject("purchaseReturnApplication")

        //1、反写采购退货申请单和采购订单
        updatePurchaseReturnApplication(nodeData,purchaseReturnApplication, materialCode, totalQuantity)

        //2、生产采购退货单
        createPurchaseReturn(purchaseReturnApplication, passBoxes, totalQuantity)

        //3、清空周转箱
        sceneGroovyService.clearPassBox(passBoxes)

        //4、创建新任务
        createNewTask(nodeData)
    }

    private void updatePurchaseReturnApplication(BmfObject nodeData,BmfObject purchaseReturnApplication, String materialCode, BigDecimal totalQuantity) {
        String sourceWarehouseCode = ValueUtil.toStr(nodeData.getString("ext_source_warehouse_code"))
        List<BmfObject> purchaseReturnApplicationDetails = purchaseReturnApplication.getAndRefreshList("purchaseReturnApplicationIdAutoMapping")
        List<BmfObject> details = purchaseReturnApplicationDetails
                .stream()
                .filter(item -> Objects.equals(item.getString("materialCode"), materialCode) && Objects.equals(ValueUtil.toStr(item.getString("sourceWarehouseCode")), sourceWarehouseCode))
                .sorted(Comparator.comparing(item -> ((BmfObject) item).getLong("lineNum"))).collect(Collectors.toList())
        Map<String, BigDecimal> updateQuantityMap = new HashMap<>(details.size())
        for (BmfObject detail : details) {
            //待退货数量
            BigDecimal waitReturnQuantity = detail.getBigDecimal("waitReturnQuantity")
            if (totalQuantity > BigDecimal.ZERO && waitReturnQuantity > BigDecimal.ZERO) {
                //已退货数量
                BigDecimal returnedQuantity = detail.getBigDecimal("returnedQuantity")
                if (totalQuantity >= waitReturnQuantity) {
                    detail.put("waitReturnQuantity", BigDecimal.ZERO)
                    detail.put("returnedQuantity", BigDecimalUtils.add(returnedQuantity, waitReturnQuantity))
                    updateQuantityMap.put(detail.getString("lineNum"), waitReturnQuantity)
                    totalQuantity = BigDecimalUtils.subtractResultMoreThanZero(totalQuantity, waitReturnQuantity)
                } else {
                    detail.put("waitReturnQuantity", BigDecimalUtils.subtractResultMoreThanZero(waitReturnQuantity, totalQuantity))
                    detail.put("returnedQuantity", BigDecimalUtils.add(returnedQuantity, totalQuantity))
                    updateQuantityMap.put(detail.getString("lineNum"), totalQuantity)
                    totalQuantity = BigDecimal.ZERO
                }
                //更新行信息
                basicGroovyService.updateByPrimaryKeySelective(detail)
            }
        }
        //更新采购退货申请单状态
        BmfObject bmfObject = new BmfObject("purchaseReturnApplication")
        bmfObject.put("id", purchaseReturnApplication.getPrimaryKeyValue())
        boolean isAllReturned = purchaseReturnApplicationDetails
                .stream()
                .allMatch(item -> Objects.equals(item.getBigDecimal("waitReturnQuantity"), BigDecimal.ZERO))
        if (isAllReturned) {
            bmfObject.put("documentStatus", DocumentStatusEnum.COMPLETED.getCode())
        } else {
            bmfObject.put("documentStatus", DocumentStatusEnum.PARTIAL.getCode())
        }
        basicGroovyService.updateByPrimaryKeySelective(bmfObject)

        //反写采购订单 TODO
//        if ("mes" == purchaseReturnApplication.getString("sourceSys") && "purchaseOrder" == purchaseReturnApplication.getString("sourceOrderType")) {
//            BmfObject purchaseOrder = nodeData.getBmfObject("purchaseOrder")
//            //反写采购订单
//            List<BmfObject> purchaseOrderDetails = purchaseOrder.getAndRefreshList("purchaseOrderDetailIdAutoMapping")
//            for (BmfObject purchaseOrderDetail : purchaseOrderDetails) {
//                BigDecimal returnQuantity = ValueUtil.toBigDecimal(updateQuantityMap.get(purchaseOrderDetail.getString("lineNum")))
//                if (returnQuantity != BigDecimal.ZERO) {
//                    purchaseOrderDetail.put("returnQuantity", BigDecimalUtils.add(purchaseOrderDetail.getBigDecimal("returnQuantity"), returnQuantity))
//                    basicGroovyService.updateByPrimaryKeySelective(purchaseOrderDetail)
//                }
//            }
//        }
    }

    private void createPurchaseReturn(BmfObject purchaseReturnApplication, List<BmfObject> passBoxes, BigDecimal totalQuantity) {
        BmfObject purchaseReturn = new BmfObject("purchaseReturn")
        purchaseReturn.put("preDocumentCode", purchaseReturnApplication.getString("code"))
        purchaseReturn.put("preDocumentType", "purchaseReturnApplication")
        purchaseReturn.put("externalDocumentType", purchaseReturnApplication.getString("externalDocumentType"))
        purchaseReturn.put("externalDocumentCode", purchaseReturnApplication.getString("externalDocumentCode"))
        purchaseReturn.put("sourceDocumentType", purchaseReturnApplication.getString("sourceDocumentType"))
        purchaseReturn.put("sourceDocumentCode", purchaseReturnApplication.getString("sourceDocumentCode"))
        purchaseReturn.put("sourceSystem", purchaseReturnApplication.getString("sourceSystem"))
        purchaseReturn.put("providerCode", purchaseReturnApplication.getString("providerCode"))
        purchaseReturn.put("providerName", purchaseReturnApplication.getString("providerName"))
        purchaseReturn.put("returnerCode", purchaseReturnApplication.getString("returnerCode"))
        purchaseReturn.put("returnerName", purchaseReturnApplication.getString("returnerName"))
        purchaseReturn.put("returnDate", purchaseReturnApplication.getDate("returnDate"))
        purchaseReturn.put("materialCode", passBoxes.get(0).getString("materialCode"))
        purchaseReturn.put("materialName", passBoxes.get(0).getString("materialName"))
        purchaseReturn.put("returnQuantity", totalQuantity)
        UserAuthDto.Resource loginUser = UserHolder.getLoginUser()
        if (loginUser != null) {
            purchaseReturn.put("operatorCode", loginUser.getResourceCode())
            purchaseReturn.put("operatorName", loginUser.getResourceName())
        }
        List<BmfObject> purchaseReturnDetails = new ArrayList<>()
        for (BmfObject passBox : passBoxes) {
            BmfObject purchaseReturnPassBox = new BmfObject("purchaseReturnPassBox")
            purchaseReturnPassBox.put("passBoxRealCode", passBox.getString("code"))
            purchaseReturnPassBox.put("passBoxCode", passBox.getString("passBoxCode"))
            purchaseReturnPassBox.put("passBoxName", passBox.getString("passBoxName"))
            purchaseReturnPassBox.put("quantity", passBox.getString("quantity"))
            try {
                purchaseReturnPassBox.put("unit", passBox.getAndRefreshBmfObject("quantityUnit"))
            } catch (Exception ignored) {
                purchaseReturnPassBox.put("unit", basicGroovyService.find("measurementUnit", passBox.getLong("quantityUnit")))
            }
            purchaseReturnPassBox.put("locationCode", passBox.getString("locationCode"))
            purchaseReturnPassBox.put("locationName", passBox.getString("locationName"))
            purchaseReturnDetails.add(purchaseReturnPassBox)
        }
        purchaseReturn.put("purchaseReturnIdAutoMapping", purchaseReturnDetails)
        purchaseReturn = basicGroovyService.setCode(purchaseReturn)
        basicGroovyService.saveOrUpdate(purchaseReturn)
    }

    private void createNewTask(BmfObject nodeData) {
        BigDecimal pendingReturnNumber = ValueUtil.toBigDecimal(nodeData.getBigDecimal("ext_pending_return_number"))
        BigDecimal totalQuantity = ValueUtil.toBigDecimal(nodeData.getBigDecimal("totalQuantity"))
        if (pendingReturnNumber > totalQuantity) {
            BmfObject clone = nodeData.deepClone()
            clone = BmfUtils.genericFromJsonExt(clone, clone.getBmfClassName())
            clone.put("ext_pending_return_number", BigDecimalUtils.subtractResultMoreThanZero(pendingReturnNumber, totalQuantity))
            clone.put("resourceCurrency", null)
            clone.put("resourceCurrencyName", null)
            clone.put("resourceCurrencyCode", null)
            clone.put("sendObjectCode", null)
            clone.put("sendObjectName", null)
            clone.put("sendObjectType", null)
            clone.put("passBoxes", null)
            sceneGroovyService.saveBySelf(clone)
            sceneGroovyService.saveLogisticsTransaction(Collections.singletonList(nodeData), clone)
        }
    }
}
