package groovy.node_scan

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.utils.ValueUtil
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import org.apache.commons.lang3.StringUtils

import java.util.stream.Collectors

/**
 * 发起生产退料-扫描脚本
 */

class NodeGN10008Scan extends NodeGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)

    @Override
    Object runScript(BmfObject nodeData) {
        String passBoxCode = nodeData.getString("code")
        //原任务合集
        List<JSONObject> tasks = nodeData.getList("tasks")
        //返回实体
        DomainScanResult result = new DomainScanResult()
        JSONObject resultData = new JSONObject()
        //生产订单编码
        String boxOrderCode = nodeData.getString("ext_box_order_code")
        if (StringUtils.isBlank(boxOrderCode)){
            throw new BusinessException("请先选择生产箱单")
        }
        if (!StringUtils.equals("passBox", nodeData.getString("codeBmfClass"))) {
            return result.success()
        }
        //周转箱实时
        BmfObject passBoxReal = basicGroovyService.findOne(BmfClassNameConst.PASS_BOX_REAL, "passBoxCode", passBoxCode)
        if (passBoxReal == null) {
            throw new BusinessException("周转箱实时不存在,编码：" + passBoxCode)
        }
        //物料主数据
        BmfObject material = passBoxReal.getAndRefreshBmfObject("material")
        if (material == null) {
            throw new BusinessException("周转箱实时物料不存在,编码：" + passBoxCode)
        }
        //生产箱单
        BmfObject boxOrder = basicGroovyService.findOne(BmfClassNameConst.BOX_ORDER, "code", boxOrderCode)
        if (boxOrder == null) {
            throw new BusinessException("生产箱单不存在,编码：" + boxOrderCode)
        }
        //箱单物料合集
        List<String> boxOrderMaterialCodes = new ArrayList<>()
        List<BmfObject> processes=boxOrder.getAndRefreshList("processes")
        processes.forEach(process -> {
            List<BmfObject> processMaterials = process.getAndRefreshList("materials")
            boxOrderMaterialCodes.addAll(processMaterials.stream().map({ it -> ((BmfObject) it).getString("materialCode") }).collect(Collectors.toList()))
        })
        if (!boxOrderMaterialCodes.contains(material.getString("code"))){
            throw new BusinessException("周转箱物料:"+material.getString("code")+"不属于当前生产箱单")
        }
        JSONObject task = new JSONObject()
        task.put("materialCode", material.getString("code"))
        task.put("materialName", material.getString("name"))
        task.put("specifications", material.getString("specifications"))
        task.put("ext_target_warehouse_code", material.getString("defaultWarehouseCode"))
        task.put("ext_target_warehouse_name", material.getString("defaultWarehouseName"))
        tasks.add(task)
        tasks= tasks.stream().collect(Collectors.collectingAndThen(Collectors.toCollection(
                // 利用 TreeSet 的排序去重构造函数来达到去重元素的目的
                // 根据firstName去重
                () -> new TreeSet<>(Comparator.comparing(item -> ValueUtil.toStr(item.get("materialCode")) + ValueUtil.toStr(item.get("ext_target_warehouse_code"))))), ArrayList::new))

        resultData.put("tasks", tasks)
        return result.success(resultData)
    }
}
