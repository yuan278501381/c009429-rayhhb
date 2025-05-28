package groovy.common

import cn.hutool.core.collection.CollectionUtil
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.sql.*
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.holder.ThreadLocalHolder
import com.chinajey.application.common.holder.UserAuthDto
import com.chinajey.application.common.utils.ValueUtil
import com.chinajey.application.script.exception.ScriptInterruptedException
import com.tengnat.dwork.common.enums.InitiateTypeEnum
import com.tengnat.dwork.modules.basic_data.service.InspectionSchemeService
import com.tengnat.dwork.modules.basic_data.service.MaterialService
import com.tengnat.dwork.modules.quality.domain.dto.InitiateInspectionDto
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import com.tengnat.dwork.modules.system.domain.dto.MessageDto
import com.tengnat.dwork.modules.system.service.MessageService
import org.apache.commons.lang3.ObjectUtils
import org.apache.commons.lang3.StringUtils
import org.springframework.util.CollectionUtils

import java.util.stream.Collectors

/**
 * 发起检验
 * inspectionType 这个一定要有发起的检验类型
 */
class NodeCreateInspectionSheet extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    MaterialService materialService = SpringUtils.getBean(MaterialService.class)
    InspectionSchemeService inspectionSchemeService = SpringUtils.getBean(InspectionSchemeService.class)
    MessageService messageService = SpringUtils.getBean(MessageService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    BmfObject runScript(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        if (CollectionUtils.isEmpty(nodeData.getList("passBoxes"))) {
            throw new BusinessException("发起检验失败，周转箱不能为空")
        }
        if (ObjectUtils.isEmpty(nodeData.getString("inspectionLocationCode"))){
            throw new BusinessException("发起检验失败，检验位置不能为空")
        }
        //根据物料的维度发起检验
        Map<String, List<BmfObject>> groupByMaterial = passBoxes.stream().collect(Collectors.groupingBy(p -> p.getString("materialCode")))
        for (String materialCode : groupByMaterial.keySet()) {
            //发起检验
            createInspectionSheet(nodeData, passBoxes, materialCode)
        }

        return nodeData
    }

    //发起检验
    void createInspectionSheet(BmfObject nodeData, List<BmfObject> passBoxes, String materialCode) {
        //物料
        BmfObject material = basicGroovyService.getByCode("material", materialCode)
        if (material == null) {
            throw new BusinessException("物料主数据不存在,编码:" + materialCode)
        }
        //数量
        BigDecimal quantity = passBoxes.stream().map(passBox -> passBox.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add)
        List<BmfObject> inspectionTypes = materialService.getInspectionTypes(material)
        Boolean isInspection = CollectionUtil.isNotEmpty(inspectionTypes) && inspectionTypes.stream().filter(inspectionType -> StringUtils.equals(inspectionType.getString("code"), "LX00004")).count() > 0
        //当前物料不需要检验
        if (!isInspection) {
            return
        }
        //检验控制规则
        if (nodeData.getBmfClassName() == "GN0001") {
            //采购收货的检验控制

            //查询 同日期 且 同供应商同批次 有没有发起检验单。有则不发起检验
            String businessPartnerBatchCode = nodeData.getString("ext_business_partner_batch_code")
            if (ObjectUtils.isNotEmpty(businessPartnerBatchCode)) {
               Map<String, Object> inspectionSheet = basicGroovyService.findOne("SELECT\n" +
                        "\tt0.`code` FROM dwk_inspection_sheet t0\n" +
                        "LEFT JOIN dwk_logistics_custom_gn0001 t1 ON t1.data_source_code=t0.source_code\n" +
                        "LEFT JOIN dwk_logistics_custom_gn0001_ext t2 ON t2.ext_GN0001_id=t1.id\n" +
                        "WHERE t0.material_code='"+nodeData.getString("ext_material_code")+"' AND t2.ext_business_partner_batch_code='"+businessPartnerBatchCode+"' AND  DATE_FORMAT(t0.create_time, '%Y-%m-%d')=CURDATE()  AND t1.id <> "+nodeData.getPrimaryKeyValue() +" LIMIT 1")
                if (inspectionSheet != null && ObjectUtils.isNotEmpty(inspectionSheet.get("code"))) {
                    return
                }
            }

            List<String> codes = basicGroovyService.getUsersByRoleName("IQC质检员")
            if (CollectionUtil.isNotEmpty(codes)) {
                Map<String, String> mapInfo = new HashMap<>();
                mapInfo.put("taskType", "已收货待检验");
                MessageDto message = new MessageDto();
                message.setCode("XT000029");
                message.setType("2");
                message.setResourceType("user");
                message.setMessageInfo(mapInfo);
                message.setResourceCodes(codes);
                messageService.sendMessage(message);
            }
        }

        BmfObject inspectionType = nodeData.getBmfObject("inspectionType")
        if (inspectionType == null) {
            throw new BusinessException("未找到检验类型")
        }
        BmfObject inspectionScheme = inspectionSchemeService.getSchemeByMaterialOrProcessCode(material.getString("code"), null)
        if (inspectionScheme == null) {
            inspectionScheme = inspectionSchemeService.getSchemeByMaterialClassification(material.getAndRefreshBmfObject("materialClassification").getString("code"))
            if (inspectionScheme == null) {
                throw new BusinessException("未找到物料[" + material.getString("code") + "]的默认检验方案")
            }
        }

        BmfObject sheet = getInspectionSheet(nodeData, inspectionType, inspectionScheme, material)

        //判断通过条件获取对应的检验的
        if (sheet == null) {
            //新增检验单
            buildInspectionSheet(nodeData, passBoxes, inspectionType, inspectionScheme, quantity, material)
        } else {
            //修改检验单
            updateInspectionSheet(sheet, passBoxes, quantity)
        }
        throw new ScriptInterruptedException("不流转")
    }

    //获取检验单
    BmfObject getInspectionSheet(BmfObject nodeData, BmfObject inspectionType, BmfObject inspectionScheme, BmfObject material) {
        return basicGroovyService.findOne("inspectionSheet", Where.builder()
                .restrictions(Arrays.asList(
                        //物料编码
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("materialCode")
                                .values(Collections.singletonList(material.getString("code")))
                                .build(),
                        //来源编码
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("sourceCode")
                                .values(Collections.singletonList(nodeData.getString("dataSourceCode")))
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
                                .build()
                )).limit(Limit.builder().size(1).build()).build())
    }

    //构造数据
    void buildInspectionSheet(BmfObject nodeData, List<BmfObject> passBoxes, BmfObject inspectionType, BmfObject inspectionScheme, BigDecimal quantity, BmfObject material) {
        UserAuthDto.LoginInfo loginInfo = ThreadLocalHolder.getLoginInfo();
        BmfObject user = basicGroovyService.findOne("user", loginInfo.getLoginId());
        if (user == null) {
            throw new BusinessException("未获取到当前登录人信息");
        }
        InitiateInspectionDto dto = new InitiateInspectionDto()
        dto.setSchemeId(inspectionScheme.getLong("id"))
        dto.setInspectionTypeId(inspectionType.getPrimaryKeyValue())
        dto.setInitiateType(InitiateTypeEnum.AUTO_INITIATE.getCode())
        dto.setSourceCode(nodeData.getString("dataSourceCode"))
        dto.setStartMethod("location")
        dto.setSourceType(nodeData.getString("dataSourceType"))
        dto.setStartMethodObject(nodeData.getString("inspectionLocationCode"))
        dto.setQuantity(quantity)
        dto.setMaterialCode(material.getString("code"))
        dto.setMaterialName(material.getString("name"))
        dto.setApplicantName(user.getString("name"))
        dto.setApplicantCode(user.getString("code"))
        dto.setUnit(material.getAndRefreshBmfObject("flowUnit"))
        dto.setPassBoxs(passBoxes)
        dto.setInstanceNodeId(nodeData.getAndRefreshBmfObject("buzSceneInstanceNode").getPrimaryKeyValue())
        dto.setLogisticsAppId(nodeData.getPrimaryKeyValue())
        //发起检验
        String inspectionSheetCode =sceneGroovyService.initiateInspectionFront(dto)
        //是否自动检验
        boolean autoInspection= ValueUtil.toBool(material.getBoolean("ext_auto_inspect"),false);
        if (autoInspection){
            sceneGroovyService.autoCompleteInspectionSheet(inspectionSheetCode)
        }

    }

    //更新检验单
    void updateInspectionSheet(BmfObject inspectionSheet, List<BmfObject> passBoexs, BigDecimal quantity) {
        //更新报检数量
        BmfObject inspectionSheetUpdate = new BmfObject(inspectionSheet.getBmfClassName())
        inspectionSheetUpdate.put("id", inspectionSheet.getPrimaryKeyValue())
        inspectionSheetUpdate.put("quantityDeclared", inspectionSheet.getBigDecimal("quantityDeclared").add(quantity))
        basicGroovyService.updateByPrimaryKeySelective(inspectionSheetUpdate)
        //创建周转箱
        for (BmfObject passBox : passBoexs) {
            BmfObject passBoeUpdate = BmfUtils.genericFromJsonExt(passBox, "inspectionSheetPassBox")
            passBoeUpdate.remove("id")
            passBoeUpdate.put("inspectionSheet", inspectionSheetUpdate)
            passBoeUpdate.put("unit", basicGroovyService.findOne("measurementUnit", passBox.getLong("quantityUnit")))
            passBoeUpdate.put("passBoxRealCode", passBox.getString("code"))
            basicGroovyService.saveOrUpdate(passBoeUpdate)
        }
        sceneGroovyService.updateInspection(inspectionSheet.getString("code"))
    }
}
