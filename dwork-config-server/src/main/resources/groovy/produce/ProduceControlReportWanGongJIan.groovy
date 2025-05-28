package groovy.produce

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.Limit
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.bmf.sql.Where
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.holder.ThreadLocalHolder
import com.chinajey.application.common.holder.UserAuthDto
import com.chinajey.application.common.holder.UserHolder
import com.chinajey.dwork.common.BmfServiceEnhance
import com.chinajey.dwork.common.enums.DocumentStatusEnum
import com.chinajey.dwork.modules.warehousingApplicant.service.WarehousingApplicantService
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.common.dto.BopFLDto
import com.tengnat.dwork.common.enums.InitiateTypeEnum
import com.tengnat.dwork.modules.basic_data.service.InspectionSchemeService
import com.tengnat.dwork.modules.manufacture.service.BoxOrderService
import com.tengnat.dwork.modules.manufacture.service.SerialNumberRealService
import com.tengnat.dwork.modules.manufacturev2.domain.DomainStationConf
import com.tengnat.dwork.modules.manufacturev2.domain.DomainTask
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ObjectResourceUnloadArea
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.manufacturev2.domain.form.SceneModuleProduceReportingForm
import com.tengnat.dwork.modules.manufacturev2.enums.InspectionBuzSource
import com.tengnat.dwork.modules.quality.domain.dto.InitiateInspectionDto
import com.tengnat.dwork.modules.quality.service.QualityInspectionService
import com.tengnat.dwork.modules.script.abstracts.ProduceControlGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.springframework.jdbc.core.JdbcTemplate

import java.util.stream.Collectors

/**
 * 生产末道报工
 *
 * 末道工序生成完工检验入库单没有检验方案直接创建下达入库申请单
 **/
class ProduceControlFinalProcessReport extends ProduceControlGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    InspectionSchemeService inspectionSchemeService = SpringUtils.getBean(InspectionSchemeService.class)
    QualityInspectionService qualityInspectionService = SpringUtils.getBean(QualityInspectionService.class)
    BmfServiceEnhance bmfServiceEnhance = SpringUtils.getBean(BmfServiceEnhance.class)
    JdbcTemplate jdbcTemplate = SpringUtils.getBean(JdbcTemplate.class)
    SerialNumberRealService serialNumberRealService = SpringUtils.getBean(SerialNumberRealService.class)
    BoxOrderService boxOrderService = SpringUtils.getBean(BoxOrderService.class)
    WarehousingApplicantService warehousingApplicantService = SpringUtils.getBean(WarehousingApplicantService.class)


    @Override
    void submit(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject) {
        Long taskItemId = jsonObject.getLong("taskItemId")
        BmfObject taskItemBmfObject = new DomainTask().getTaskItemBmfObject(taskItemId)
        BmfObject taskBmfObject = taskItemBmfObject.getBmfObject("boxOrderProcessProduceTask")
        BmfObject boxOrderProcess = taskBmfObject.getAndRefreshBmfObject("boxOrderProcess")
        BmfObject boxOrder = boxOrderProcess.getBmfObject("boxOrder")
        JSONArray list = boxOrderProcess.getJSONArray("inspectionTypes")
        String materialCode = boxOrder.getString("materialCode")
        def processCode = boxOrderProcess.getString("processCode")
        def processNo = boxOrderProcess.getString("processNo")
        DomainStationConf domainStationConf = new DomainStationConf(stationReal.getStationCode());
        List<Long> qualifiedAreaIds = domainStationConf.getStationUnloadAreas()
                .stream()
                .filter(item -> "qualified".equals(item.getReportType()) && !"sideProduct".equals(item.getType()))
                .map(ObjectResourceUnloadArea::getId)
                .collect(Collectors.toList());
        List<SceneModuleProduceReportingForm.PassBox> reportPassBoxes = jsonObject.getJSONArray("reportPassBoxes").toJavaList(SceneModuleProduceReportingForm.PassBox.class)
        reportPassBoxes = reportPassBoxes.stream().filter(it -> qualifiedAreaIds.contains(it.getBlankingAreaId())).collect(Collectors.toList())
        BopFLDto bopDto = boxOrderService.judgeBoxOrderProcessFL(boxOrder.getString("code"), boxOrderProcess.getPrimaryKeyValue())
        BmfObject inspectionType = basicGroovyService.findOne(BmfClassNameConst.INSPECTION_TYPE, "code", "LX00005")
        if (inspectionType == null) {
            throw new BusinessException("找不到检验类型[LX00005]信息")
        }
        if (!bopDto.isLast()) {
            //只有末道工序报工才会生成完工检验入库单并且下达
            return
        }
        //获取报工总数
        BigDecimal reportSum = reportPassBoxes.stream().map(obj -> obj.getReportQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add)
        BmfObject material = basicGroovyService.findOne("material", Collections.singletonMap("code", materialCode))
        if (material == null) {
            throw new BusinessException("物料信息为空")
        }
        //生产订单
        BmfObject productOrder = basicGroovyService.findOne("productOrder", "code", boxOrder.getString("productOrderCode"))
        if (productOrder == null) {
            throw new BusinessException("生产订单信息不存在，无法生成生产备料单" + boxOrder.getString("productOrderCode"))
        }
        List<String> passBoxCodes = reportPassBoxes.stream().map(it -> it.getPassBoxCode()).collect(Collectors.toList())
        List<BmfObject> passBoxes = this.bmfServiceEnhance.findIn("passBoxReal", "passBoxCode", passBoxCodes)
        //没有工序检验方案直接入库
        if (list == null || list.isEmpty()) {
            createWarehousingApplicantByBoxOrder(boxOrder, material, productOrder, reportSum, passBoxes)
            return
        }
        List<BmfObject> collect = list.stream().filter { it.getString("resourceCode").equals("LX00005") }.collect(Collectors.toList())
        if (collect.isEmpty()) {
            createWarehousingApplicantByBoxOrder(boxOrder, material, productOrder, reportSum, passBoxes)
            return
        }
        //匹配检验方案
        List<BmfObject> inspectionSchemes = inspectionSchemeService.getInspectionSchemeByMaterialAndProcessCode(materialCode, processCode);
        if (inspectionSchemes == null) {
            createWarehousingApplicantByBoxOrder(boxOrder, material, productOrder, reportSum, passBoxes)
            return
        }
        BmfObject sourceInspectionSheet = getInspectionSheet(stationReal.getStationCode(), materialCode, processCode, processNo, inspectionType, inspectionSchemes.get(0))
        if (sourceInspectionSheet == null) {
            //新增检验单
            insertInspectionSheet(material, boxOrderProcess, boxOrder, inspectionType, inspectionSchemes.get(0), stationReal, reportSum, passBoxes, taskItemId)
        } else {
            //更新检验单
            updateInspectionSheet(sourceInspectionSheet, passBoxes)
        }
    }

    private void createWarehousingApplicantByBoxOrder(BmfObject boxOrder, BmfObject material, BmfObject productOrder, BigDecimal reportSum, List<BmfObject> passBoxes) {
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
        detail.put("preDocumentType", "boxOrder")
        detail.put("preDocumentCode", boxOrder.getString("code"))
        warehousingApplicantIdAutoMapping.add(detail)

        //周转箱
        passBoxes.forEach(passBox -> {
            passBox.remove("id")
            passBox.put("warehouseCode", productOrder.getString("inboundWarehouseCode"))
            passBox.put("warehouseName", productOrder.getString("inboundWarehouseName"))
            passBox.put("passBoxRealCode", passBox.getString("code"))
            passBox.put("unit", passBox.getBmfObject("quantityUnit"))
            passBox.put("documentStatus", DocumentStatusEnum.PENDING.getCode())
        });

        warehousingApplicant.put("warehousingApplicantPassBoxIdAutoMapping", passBoxes)
        warehousingApplicant = warehousingApplicantService.save(warehousingApplicant)
        if (warehousingApplicant != null) {
            //下达
            warehousingApplicantService.issued(Arrays.asList(warehousingApplicant.getPrimaryKeyValue()))
        }
    }

    /**
     * 获取相同报检工位、相同工序编码、相同工序号、相同检验方案、相同检验类型、非复检未取件、未检验的检验单
     * @param materialCode
     * @param stationRealCode
     * @param processCode
     * @param processNo
     * @param inspectionType
     * @param inspectionScheme
     * @return
     */
    BmfObject getInspectionSheet(String stationRealCode, String materialCode, String processCode, String processNo, BmfObject inspectionType, BmfObject inspectionScheme) {
        return basicGroovyService.findOne("inspectionSheet", Where.builder()
                .restrictions(Arrays.asList(
                        //工位编码
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("stationCode")
                                .values(Collections.singletonList(stationRealCode))
                                .build(),
                        //物料编码
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("materialCode")
                                .values(Collections.singletonList(materialCode))
                                .build(),
                        //工序编码
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("processCode")
                                .values(Collections.singletonList(processCode))
                                .build(),
                        //工序号
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("processNo")
                                .values(Collections.singletonList(processNo))
                                .build(),
                        //检验状态
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("inspectionStatus")
                                .values(Collections.singletonList("1"))
                                .build(),
                        //检验方案编码
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("inspectionSchemeCode")
                                .values(Collections.singletonList(inspectionScheme.getString("code")))
                                .build(),
                        //取件状态
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("pickupStatus")
                                .values(Collections.singletonList("notCollected"))
                                .build(),
                        //检验类型
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("inspectionType")
                                .values(Collections.singletonList(inspectionType.getPrimaryKeyValue()))
                                .build(),
                        //复检
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("reInspection")
                                .values(Collections.singletonList(false))
                                .build())).limit(Limit.builder().size(1).build()).build())
    }

    def void insertInspectionSheet(BmfObject material, BmfObject boxOrderProcess, BmfObject boxOrder, BmfObject inspectionType, BmfObject inspectionScheme, DwkStationReal stationReal, BigDecimal sum, List<BmfObject> passBoxReals, Long taskItemId) {
        List<BmfObject> serialNumberReals = serialNumberRealService.getPassBoxRealCode(passBoxReals.stream().map(t -> t.getString("code")).collect(Collectors.toList()), false)
        String recordCode = UUID.randomUUID().toString();
        UserAuthDto.LoginInfo loginInfo = ThreadLocalHolder.getLoginInfo()
        InitiateInspectionDto dto = new InitiateInspectionDto()
        dto.setSchemeId(inspectionScheme.getLong("id"))
        dto.setInspectionTypeId(inspectionType.getPrimaryKeyValue())
        dto.setBoxOrderCode(boxOrder.getString("code"))
        dto.setProcessCode(boxOrderProcess.getString("processCode"))
        dto.setProcessNo(boxOrderProcess.getString("processNo"))
        dto.setInitiateType(InitiateTypeEnum.AUTO_INITIATE.getCode())
        dto.setSourceCode(boxOrder.getString("code"))
        dto.setStartMethod("station")
        dto.setSourceType("production")
        dto.setStartMethodObject(stationReal.getStationCode())
        dto.setQuantity(sum)
        dto.setMaterialCode(material.getString("code"))
        dto.setMaterialName(material.getString("name"))
        dto.setApplicantName(loginInfo.getResource().getResourceName())
        dto.setApplicantCode(loginInfo.getResource().getResourceCode())
        dto.setUnit(material.getAndRefreshBmfObject("flowUnit"))
        // 检验单业务来源
        dto.setBuzSourceType(InspectionBuzSource.PRODUCE_EXECUTION.getValue());
        dto.setBuzSourceCode(recordCode);
        dto.setPassBoxs(passBoxReals)
        for (final def serialNumberReal in serialNumberReals) {
            serialNumberReal.setBmfClassName("inspectionSheetSerialNumber")
            serialNumberReal.remove("id")
            serialNumberReal.put("serialNumberCode", serialNumberReal.getString("code"))

        }
        dto.setSerialNumbers(serialNumberReals)
        //发起检验
        def code = qualityInspectionService.initiateInspectionFront(dto)
        // 生成生产执行的检验记录
        BmfObject bmfObject = new BmfObject("stationRealInspectionRecord");
        bmfObject.put("code", recordCode);
        bmfObject.put("stationCode", stationReal.getStationCode());
        bmfObject.put("instanceCode", stationReal.getInstanceCode());
        bmfObject.put("taskItemId", taskItemId);
        bmfObject.put("opUserCode", UserHolder.getLoginUser().getResourceCode())
        bmfObject.put("opUserName", UserHolder.getLoginUser().getResourceName())
        bmfObject.put("inspectionType", code)
        bmfObject.put("inspectionCode", inspectionType.getString("code"))
        bmfObject.put("status", false);
        this.basicGroovyService.saveOrUpdate(bmfObject);
    }

    void updateInspectionSheet(BmfObject inspectionSheet, List<BmfObject> passBoxes) {
        BmfObject inspectionSheetUpdate = new BmfObject(inspectionSheet.getBmfClassName())
        inspectionSheetUpdate.put("id", inspectionSheet.getPrimaryKeyValue())
        //补充周转箱编码一致先删除再新增否则同周转箱编码会产生多个
        def passBoxCodes = passBoxes.stream().map { it -> it.getString("code") }.collect(Collectors.toList())
        String sql = "delete from `dwk_inspection_sheet_pass_box` WHERE inspection_sheet_id = ? and pass_box_real_code in (?)"
        jdbcTemplate.update(sql, inspectionSheet.getPrimaryKeyValue(), passBoxCodes.join(","))
        //创建周转箱
        List<BmfObject> needUpdates = new ArrayList<>()
        for (JSONObject passBox : passBoxes) {
            BmfObject passBoxUpdate = BmfUtils.genericFromJsonExt(passBox, "inspectionSheetPassBox")
            passBoxUpdate.remove("id")
            passBoxUpdate.put("inspectionSheet", inspectionSheetUpdate)
            passBoxUpdate.put("unit", passBox.get("quantityUnit"))
            passBoxUpdate.put("passBoxRealCode", passBox.getString("code"))
            needUpdates.add(passBoxUpdate)
        }
        basicGroovyService.saveOrUpdate(needUpdates)
        BigDecimal quantityDeclared = inspectionSheet.getAndRefreshList("passBoxes").stream()
                .filter { it != null && it.getBigDecimal("quantity") != null }
                .map { it.getBigDecimal("quantity") }
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        //更新报检数量
        inspectionSheetUpdate.put("quantityDeclared", quantityDeclared)
        basicGroovyService.updateByPrimaryKeySelective(inspectionSheetUpdate)
        qualityInspectionService.updateInspection(inspectionSheet.getString("code"))
    }


}