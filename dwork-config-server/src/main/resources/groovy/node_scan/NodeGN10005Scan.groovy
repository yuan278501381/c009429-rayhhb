package groovy.node_scan

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.StringUtils

/**
 * 采购收货任务扫描脚本
 * @author angel.su
 * createTime 2025/3/22 08:35
 */
class NodeGN10005Scan extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        //返回实体
        DomainScanResult result = new DomainScanResult()

        String materialCode = nodeData.getString("ext_material_code")
        String materialName = nodeData.getString("ext_material_name")
        String codeBmfClass = nodeData.getString("codeBmfClass")
        String passBoxCode = nodeData.getString("code")
        String model = nodeData.getString("model")
        //每箱数量
        BigDecimal quantity = nodeData.getBigDecimal("ext_single_box_quantity")

        //可以不是空箱，但要同物料
        if (StringUtils.isNotBlank(codeBmfClass) && "passBox" == codeBmfClass && 'virtualPassBox' == model) {
            BmfObject passBoxReal = basicGroovyService.findOne(BmfClassNameConst.PASS_BOX_REAL, "passBoxCode", passBoxCode)
            BmfObject newPassBox = new BmfObject()
            if (passBoxReal != null) {
                String passBoxMaterialCode = passBoxReal.getString("materialCode")
                if (passBoxMaterialCode != materialCode) {
                    throw new BusinessException("请扫描相同物料的周转箱,物料编码" + materialCode)
                }
                newPassBox = passBoxReal.deepClone()
                newPassBox.put("receiveQuantity", quantity)
                newPassBox.put("boxSelect", true)
            } else {
                BmfObject passBox = basicGroovyService.findOne(BmfClassNameConst.PASS_BOX, "code", passBoxCode)
                newPassBox = passBox.deepClone()
                newPassBox.put("materialCode", materialCode)
                newPassBox.put("materialName", materialName)
                newPassBox.put("passBoxCode", passBoxCode)
                newPassBox.put("passBoxName", passBox.getString("name"))
                newPassBox.put("receiveQuantity", quantity)
                newPassBox.put("boxSelect", true)
            }

            JSONObject jsonObject = new JSONObject()
            jsonObject.put("scanDataJoin", false)
            JSONObject resultData = new JSONObject()
            resultData.put("otherSettings", jsonObject)
            //添加新的周转箱到原有列表
            resultData.put("passBoxes", Collections.singletonList(newPassBox))
            return result.success(resultData)
        }
        return result.success()
    }
}
