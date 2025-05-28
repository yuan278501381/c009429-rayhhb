package groovy.common

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.bmf.sql.Where
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.ValueUtil
import com.tengnat.dwork.common.constant.BmfAttributeConst
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.common.enums.LoadMaterialTypeEnum
import com.tengnat.dwork.common.enums.OperateSourceEnum
import com.tengnat.dwork.modules.basic_data.service.PassBoxService
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.StringUtils
import org.springframework.util.CollectionUtils

import java.util.stream.Collectors

/**
 * 创建台账信息
 */
class NodeCreateLedger extends NodeGroovyClass{

    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    PassBoxService passBoxService = SpringUtils.getBean(PassBoxService.class)

    @Override
    BmfObject runScript(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes")
        if (CollectionUtils.isEmpty(passBoxes)){
            throw new BusinessException("创建台账信息失败，周转箱不能为空")
        }
        //根据物料的维度创建台账
        Map<String,List<BmfObject>> groupByMaterial = passBoxes.stream().collect(Collectors.groupingBy(p -> p.getString("materialCode")))
        for (String materialCode:groupByMaterial.keySet()){
            //生成台账信息
            createLedger(nodeData,groupByMaterial.get(materialCode),materialCode)
        }
        return nodeData
    }


    //自动生成台账  模具\夹具\刀具(刀柄)   根据物料主数据.刀具类型=刀柄
    void createLedger(BmfObject nodeData,List<BmfObject> passBoxes,String materialCode) {
        //物料
        BmfObject material = basicGroovyService.getByCode(BmfClassNameConst.MATERIAL, materialCode)
        if (material==null){
            throw new BusinessException("物料主数据不存在,编码:"+materialCode)
        }
        BmfObject warehouseBmfObject = null
        if (passBoxes == null || passBoxes.size() == 0) {
            return
        }
        //获取目标位置
        String targetLocationCode = nodeData.getString("targetLocationCode")
        String targetLocationName = nodeData.getString("targetLocationName")

        //一个托盘的周转箱位置是一样的
        BmfObject location = basicGroovyService.getByCode("location", targetLocationCode)
        //校验位置绑定仓库信息
        Map wareHouse = getWareHouse(targetLocationCode, targetLocationName)
        if (wareHouse.get("code") != null) {
            warehouseBmfObject = basicGroovyService.getByCode("warehouse", wareHouse.get("code"))
        }
        //获取数量
        BigDecimal quantity = passBoxes.stream()
                .map(p -> p.getBigDecimal("quantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        if (quantity <= 0) {
            return
        }
        //供应商信息
        String partenerCode = nodeData.getString("ext_provider_code")
        BmfObject businessPartner = basicGroovyService.getByCode(BmfClassNameConst.BUSINESS_PARTNER, partenerCode)


        String type = material.getString("type")
        if (StringUtils.isNotBlank(type)) {
            type = StringUtils.removeEnd(type, "Classification")
        }
        def types = Arrays.asList("fixture", "mold", "knife")
        //校验物料是否为模具\夹具\刀具(刀柄)
        if (!types.contains(type)) {
            return
        }
        if (StringUtils.equals(BmfClassNameConst.KNIFE, type) && !StringUtils.equals("hilt", material.getString("knifeType"))){
            return
        }
        if (StringUtils.isBlank(material.getString("ledgerClassificationCode"))) {
            throw new BusinessException("物料主数据[台账类别]不存在!")
        }

        Map<String, OperateSourceEnum> operateSourceEnumMap = new HashMap<>()
        //    KNIFE("KNIFE", "刀具"),
        operateSourceEnumMap.put(BmfClassNameConst.KNIFE, OperateSourceEnum.KNIFE)
        //    MOLD("MOLD", "模具"),
        operateSourceEnumMap.put(BmfClassNameConst.MOLD, OperateSourceEnum.MOLD)
        //    FIXTURE("FIXTURE", "夹具"),
        operateSourceEnumMap.put(BmfClassNameConst.FIXTURE, OperateSourceEnum.FIXTURE)
        Map<String, LoadMaterialTypeEnum> loadMaterialTypeEnumMap = LoadMaterialTypeEnum.getMapByBmfClass()

        BmfObject ledger = new BmfObject(type)
        BmfObject ledgerClassification = basicGroovyService.findOne(type + "Classification", Where.builder().restrictions(Arrays.asList(
                Restriction.builder()
                        .conjunction(Conjunction.AND)
                        .operationType(OperationType.EQUAL)
                        .attributeName(BmfAttributeConst.CODE)
                        .values(Collections.singletonList(material.getString("ledgerClassificationCode")))
                        .build()
        )).build())
        if (ledgerClassification == null) {
            throw new BusinessException("物料对应的台账类别不存在")
        }
        ledger.remove("id")
        ledger.remove("code")
        ledger.put("name", material.getString("name"))
        ledger.put("sourceType", "automatic")
        ledger.put("status", "warehouse")
        ledger.put(type + "Classification", ledgerClassification)
        ledger.put("life", ValueUtil.toBigDecimal("100", 100))
        ledger.put("locationClassification", "internal")
        //责任部门
        ledger.put("responsibleDept", material.getBmfObject("responsibleDept"))
        //责任人
        ledger.put("responsibleUser",  material.getBmfObject("responsibleUser"))
        //周期单位
        ledger.putUncheck("cycleUnit", material.getString("cycleUnit"))
        ledger.putUncheck("checkCycle", material.getString("checkCycle"))
        ledger.putUncheck("material", material)
        ledger.put("buGroup", material.getString("ext_buGroup"))
        if (businessPartner != null) {
            ledger.put("manufacturer", businessPartner)
        }
        for (int i = 0; i < ValueUtil.toInt(quantity,1); i++){
            BmfObject clone = ledger.deepClone()
            passBoxService.generatePassBox(clone,
                    loadMaterialTypeEnumMap.get(type).getCode(),
                    operateSourceEnumMap.get(type).getCode(),location)
            //默认刀柄
            if (StringUtils.equals(BmfClassNameConst.KNIFE, type)) {
                clone.put("type","hilt")
            }
            //仓库信息处理
            clone.put("warehouse",warehouseBmfObject)
            basicGroovyService.saveOrUpdate(clone)
            BmfObject passBox = clone.getBmfObject(BmfAttributeConst.PASS_BOX)
            //获取周转箱信息
            if (passBox == null) {
                throw new BusinessException("台账信息生成错误:周转箱生成失败")
            }
            BmfObject passBoxReal = basicGroovyService.findOne(BmfClassNameConst.PASS_BOX_REAL, Where.builder().restrictions(Collections.singletonList(
                    Restriction.builder()
                            .conjunction(Conjunction.AND)
                            .operationType(OperationType.EQUAL)
                            .attributeName(BmfAttributeConst.PASS_BOX_CODE)
                            .values(Collections.singletonList(passBox.getString("code")))
                            .build())
            ).build())
            if (passBoxReal == null) {
                throw new BusinessException("台账信息生成错误:周转箱实时生成失败")
            }
        }
        sceneGroovyService.clearPassBox(passBoxes)
    }

    private Map<String, String> getWareHouse(String locationCode, String locationName) {
        Map<String, String> map = new HashMap<>()
        //查询仓库资源id
        BmfObject resourceId = basicGroovyService.getByCode("resource", "warehouse")
        //位置名称 位置编码
        JSONObject loJson = new JSONObject();
        loJson.put("resourceCode", locationCode)
        loJson.put("resourceName", locationName)
        loJson.put("bindingResource", resourceId.getLong("id"))
        BmfObject resourceJson = basicGroovyService.findOne("resourceBinding", loJson)
        //仓库名称 仓库编码
        String warehouseName = null
        String warehouseCode = null
        if (resourceJson != null) {
            warehouseName = resourceJson.getString("bindingResourceName")
            warehouseCode = resourceJson.getString("bindingResourceCode")
        }
        map.put("code", warehouseCode)
        map.put("name", warehouseName)
        return map
    }
}
