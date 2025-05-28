package groovy.aop

import cn.hutool.core.collection.CollectionUtil
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.modules.warehousingApplicant.service.WarehousingApplicantService
import com.tengnat.dwork.modules.script.abstracts.AopAfterGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 提交检验审批-创建入库申请单
 */
class AopAfterSheetApproveCreateWarehousingApplicant extends AopAfterGroovyClass {

    Logger log = LoggerFactory.getLogger(getClass())
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    WarehousingApplicantService warehousingApplicantService = SpringUtils.getBean(WarehousingApplicantService.class)


    private BmfObject material
    private BmfObject inspectionSheet

    @Override
    void runScript(Object object) {
        log.info("-----------------------开始执行[检验审批提交]脚本----------------------------")
        BmfObject sheet = (BmfObject) object
        if (!validate(sheet)) {
            return
        }
        List<BmfObject> inspectionSheetPassBoxes = sheet.getAndRefreshList("passBoxes")
        //计算数量
        BigDecimal sumQuantity = inspectionSheetPassBoxes.stream().map(bmfObject -> bmfObject.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
        //查找生产订单
        //查找生产箱单
        def boxOrder = basicGroovyService.findOne("boxOrder", "code", sheet.getString("boxOrderCode"))
        if (boxOrder == null) {
            throw new BusinessException("生产箱单不存在")
        }
        //生产订单
        BmfObject productOrder = basicGroovyService.findOne("productOrder", "code", boxOrder.getString("productOrderCode"))
        if (productOrder == null) {
            throw new BusinessException("生产订单信息不存在，无法生成生产备料单" + boxOrder.getString("productOrderCode"))
        }
        createWarehousingApplicantByBoxOrder(sheet.getString("code"), boxOrder, material, productOrder, sumQuantity, inspectionSheetPassBoxes)
        log.info("-----------------------结束执行[检验审批提交]脚本----------------------------")
    }

    private void createWarehousingApplicantByBoxOrder(String inspectionSheetCode, BmfObject boxOrder, BmfObject material, BmfObject productOrder, BigDecimal reportSum, List<BmfObject> passBoxes) {
        JSONObject warehousingApplicant = new BmfObject("warehousingApplicant")
        warehousingApplicant.put("orderBusinessType", "produceWarehousing") //单据业务类型生产入库
        warehousingApplicant.put("sourceDocumentType", "productOrder") //源头内部单据类型生产订单
        warehousingApplicant.put("sourceDocumentCode", boxOrder.getString("productOrderCode")) //源头内部单据编码生产订单
        warehousingApplicant.put("preDocumentType", "boxOrder")//生产箱单
        warehousingApplicant.put("preDocumentCode", boxOrder.getString("code"))//生产箱单编码
        warehousingApplicant.put("externalDocumentType", null)
        warehousingApplicant.put("externalDocumentCode", null)

        List<JSONObject> warehousingApplicantIdAutoMapping = new ArrayList<>()
        warehousingApplicant.put("warehousingApplicantIdAutoMapping", warehousingApplicantIdAutoMapping)
        JSONObject detail = new JSONObject()
        detail.put("materialCode", material.getString("code"))
        detail.put("materialName", material.getString("name"))
        detail.put("specifications", material.get("specifications"))
        detail.put("unit", material.get("flowUnit"))
        detail.put("targetWarehouseName", productOrder.getString("inboundWarehouseName"))
        detail.put("targetWarehouseCode", productOrder.getString("inboundWarehouseCode"))
        detail.put("planQuantity", reportSum)
        detail.put("warehousingQuantity", BigDecimal.ZERO)
        detail.put("waitQuantity", reportSum)
        detail.put("lineNum", "1")
        detail.put("sourceDocumentType", "productOrder") //源头内部单据类型
        detail.put("sourceDocumentCode", boxOrder.getString("productOrderCode")) //源头内部单据编码
        detail.put("preDocumentType", "inspectionSheet")
        detail.put("preDocumentCode", inspectionSheetCode)
        warehousingApplicantIdAutoMapping.add(detail)
        //周转箱
        passBoxes.forEach(passBox -> {
            passBox.remove("id")
            passBox.put("warehouseCode", productOrder.getString("inboundWarehouseCode"))
            passBox.put("warehouseName", productOrder.getString("inboundWarehouseName"))
            passBox.put("documentStatus", DocumentStatusEnum.PENDING.getCode())
        });
        warehousingApplicant.put("warehousingApplicantPassBoxIdAutoMapping", passBoxes)
        warehousingApplicant = warehousingApplicantService.save(warehousingApplicant)
        if (warehousingApplicant != null) {
            //下达
            warehousingApplicantService.issued(Arrays.asList(warehousingApplicant.getPrimaryKeyValue()))
        }
    }

    //校验
    boolean validate(BmfObject bmfObject) {
        //检验合格且审批通过
        if (!StringUtils.equals("qualified", bmfObject.getString("judgementResult")) || !StringUtils.equals("pass", bmfObject.getString("approvalResult"))) {
            return false
        }
        //检验类型 - 完工入库检
        BmfObject inspectionType = bmfObject.getAndRefreshBmfObject("inspectionType")
        if (inspectionType == null || !StringUtils.equals("LX00005", inspectionType.getString("code"))) {
            return false
        }
        List<BmfObject> passBoxes = bmfObject.getAndRefreshList("passBoxes")
        if (CollectionUtil.isEmpty(passBoxes)) {
            throw new BusinessException("检验周转箱不能为空")
        }
        material = basicGroovyService.getByCode("material", bmfObject.getString("materialCode"))
        if (material == null) {
            throw new BusinessException("物料[" + bmfObject.getString("materialCode") + "]不存在")
        }
        inspectionSheet = basicGroovyService.findOne("inspectionSheet", "code", bmfObject.getString("code"))
        if (inspectionSheet == null) {
            throw new BusinessException("检验单不存在,code:" + bmfObject.getString("code"))
        }
        return true
    }
}
