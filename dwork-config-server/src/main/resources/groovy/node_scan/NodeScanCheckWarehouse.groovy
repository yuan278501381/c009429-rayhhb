package groovy.node_scan

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.common.enums.WlStyleModel
import com.tengnat.dwork.modules.manufacturev2.domain.DomainScanResult
import com.tengnat.dwork.modules.script.abstracts.NodeScanGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.lang3.ObjectUtils

/**
 * 位置(周转箱)校验发出/目标仓库
 */
class NodeScanCheckWarehouse extends NodeScanGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)

    @Override
    JSONObject runScript(BmfObject nodeData) {
        DomainScanResult result = new DomainScanResult()
        //发出仓库
        String outputWarehouseCode = nodeData.getString("ext_source_warehouse_code")
        //目标仓库
        String targetWarehouseCode = nodeData.getString("ext_target_warehouse_code")
        //模块
        String model = nodeData.getString("model")
        //扫描编码
        String code = nodeData.getString("code")
        //位置编码
        String locationCode
        //周转箱
        boolean thisPassBox = false

        //校验的仓库
        String warehouseCode

        if (WlStyleModel.SourceLocation.getCode() == model) {
            warehouseCode = outputWarehouseCode
        } else if (WlStyleModel.TargetLocation.getCode() == model){
            warehouseCode = targetWarehouseCode
        }else {
            //扫描的周转箱,一般是发出仓库的周转箱
            warehouseCode=outputWarehouseCode
        }
        if (ObjectUtils.isEmpty(warehouseCode)) {
            return result.success()
        }

        //校验位置的模块
        List<String> checkLocationModels = Arrays.asList(WlStyleModel.TargetLocation.getCode(), WlStyleModel.SourceLocation.getCode())
        //校验周转箱的模块
        List<String> checkPassBoxModels = Arrays.asList(WlStyleModel.MaterialRelevancePassBox.getCode(), WlStyleModel.LocationRelevancePassBox.getCode(),
                WlStyleModel.StorageLocationRelevancePassBox.getCode(),WlStyleModel.StorageLocationRelevanceMaterialRelevancePassBox.getCode(),
                WlStyleModel.palletRelevanceMaterialPassBox.getCode(),WlStyleModel.materialRelevancePalletPassBox.getCode(),
                WlStyleModel.palletRelevancePassBox.getCode(),WlStyleModel.materialRelevancePassBoxSerialNumber.getCode(),
                WlStyleModel.goodsAllocationRelevancePassBoxSerialNumber.getCode(),WlStyleModel.goodsAllocationMaterialsRelevancePassBoxSerialNumber.getCode(),
                WlStyleModel.PassBox.getCode())
        if (checkLocationModels.contains(model)) {
            locationCode = code
        } else if (checkPassBoxModels.contains(model)) {
            thisPassBox = true
            BmfObject passBox = basicGroovyService.getByCode(BmfClassNameConst.PASS_BOX, code)
            locationCode = passBox.getString("locationCode")
            if (ObjectUtils.isEmpty(locationCode)) {
                return result.fail("周转箱:" + code + ",位置为空")
            }
        } else {
            return result.success()
        }

        //仓库主数据
        BmfObject warehouse = basicGroovyService.getByCode(BmfClassNameConst.WAREHOUSE, warehouseCode)
        if (warehouse == null) {
            return result.fail("仓库不存在,编码:" + warehouseCode)
        }
        //根据位置获取仓库信息
        Map<String, Object> warehouseInfo = sceneGroovyService.getWarehouseByLocation(locationCode)
        if (ObjectUtils.isEmpty(warehouseInfo) || ObjectUtils.notEqual(warehouseInfo.get("warehouseCode"), warehouseCode)) {
            return result.fail("扫描的" + (thisPassBox ? "周转箱" : "位置") + "不属于" + warehouse.getString("name") + "仓库")
        }
        return result.success()
    }

}
