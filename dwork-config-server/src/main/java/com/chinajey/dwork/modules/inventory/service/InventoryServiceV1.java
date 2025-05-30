package com.chinajey.dwork.modules.inventory.service;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.enums.BmfEnum;
import com.chinajay.virgo.bmf.enums.BmfEnumCache;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.*;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.BmfServiceEnhance;
import com.chinajey.dwork.modules.inventory.mapper.InventoryMapper;
import com.tengnat.dwork.common.constant.BmfAttributeConst;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.modules.logistics.service.BusinessSceneService;
import com.tengnat.dwork.modules.logistics.service.LogisticsService;
import com.tengnat.dwork.modules.script.service.SceneGroovyService;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class InventoryServiceV1 {
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private BmfService bmfService;

    @Resource
    private BmfServiceEnhance bmfServiceEnhance;

    @Resource
    private LogisticsService logisticsService;

    @Resource
    BusinessSceneService businessSceneService;

    @Resource
    private CodeGenerator codeGenerator;

    @Resource
    InventoryMapper inventoryMapper;

    @Resource
    SceneGroovyService sceneGroovyService;


    public static final String BMFCLASS = "inventorySheet";

    @Transactional(rollbackFor = Exception.class)
    public InvokeResult save(JSONObject jsonObject) {
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(jsonObject, BMFCLASS);
        checkJsonObject(bmfObject, true);
        assembleDetail(bmfObject);
        BmfObject inventorySheet = BmfUtils.genericFromJsonExt(bmfObject, BMFCLASS);
        this.codeGenerator.setCode(inventorySheet);
        this.bmfService.saveOrUpdate(inventorySheet);
        return InvokeResult.success();
    }


    private void assembleDetail(BmfObject BmfObject) {
        List<BmfObject> plan = BmfObject.getAndRefreshList("inventorySheetPIdAutoMapping");
        //校验明细盘点周转箱是否重复
        this.checkInventory(plan);
        for (int i = 0; i < plan.size(); i++) {
            List<BmfObject> details = new ArrayList<>();
            plan.get(i).remove("id");
            // 解析区域、仓库、位置、物料等条件
            JSONObject detail1 = plan.get(i);
            List<Object> areaCodes = returnCodes(detail1.getString("inventoryAreaCode"));
            List<Object> warehouseCodes = returnCodes(detail1.getString("inventoryWarehouseCode"));
            List<Object> positionCodes = returnCodes(detail1.getString("inventoryPositionCode"));
            List<Object> materialCodes = returnCodes(detail1.getString("inventoryMaterialCode"));
            Set<String> passBoxCodes = this.queryTurnoverBoxes(areaCodes, warehouseCodes, positionCodes, materialCodes);
            if (CollectionUtils.isEmpty(passBoxCodes)) {
                continue;
            }
            //校验检验单下的周转箱是否重复
            checkItem(passBoxCodes, null);
            List<BmfObject> passBoxes = this.bmfServiceEnhance.findIn("passBoxReal", "passBoxCode", new ArrayList<>(passBoxCodes));
            if (!CollectionUtils.isEmpty(passBoxes)) {
                //组装盘点明细
                for (BmfObject passBox : passBoxes) {
                    if ("unLock".equals(passBox.getString("status"))) {
                        BmfObject detail = new BmfObject("inventorySheetDetail");
                        detail.put("passBoxName", passBox.getString("passBoxName"));
                        detail.put("passBoxCode", passBox.getString("passBoxCode"));
                        detail.put("processName", passBox.getString("processName"));
                        detail.put("processCode", passBox.getString("processCode"));
                        detail.put("processNo", passBox.getString("processNo"));
                        detail.put("materialName", passBox.getString("materialName"));
                        detail.put("materialCode", passBox.getString("materialCode"));
                        detail.put("quantity", passBox.getBigDecimal("quantity"));
                        detail.put("unit", passBox.get("quantityUnit"));
                        detail.put("locationName", passBox.getString("locationName"));
                        detail.put("locationCode", passBox.getString("locationCode"));
                        detail.put("storageLocationCode", passBox.getString("storageLocationCode"));
                        detail.put("storageLocationName", passBox.getString("storageLocationName"));
                        detail.put("location", passBox.get("location"));
                        detail.put("storageLocation", passBox.get("storageLocation"));
                        detail.put("passBoxInventoryStatus", "unInventoried");
                        detail.put("passBoxRealCode", passBox.getString("code"));
                        detail.put("isLedger", passBox.getBoolean("isLedger")!= null ? passBox.getBoolean("isLedger") : false);
                        if (passBox.getAndRefreshBmfObject("material")!=null){
                            detail.put("materialClassName", passBox.getAndRefreshBmfObject("material").getAndRefreshBmfObject("materialClassification").getString("name"));
                            detail.put("materialType", passBox.getAndRefreshBmfObject("material").getString("type"));
                        }
                        details.add(detail);
                    }
                }
            }
            plan.get(i).put("status", "NotInventoried");
            //盘点计划明细
            plan.get(i).put("inventorySheetDIdAutoMapping", details);
            if (plan.get(i).getBoolean("replayStatus")) {
                //盘点复盘明细
                plan.get(i).put("inventorySheetRIdAutoMapping", details);
            }
        }
    }


    private void updateDetail(BmfObject BmfObject) {
        List<BmfObject> plan = BmfObject.getAndRefreshList("inventorySheetPIdAutoMapping");
        //校验明细盘点周转箱是否重复
        this.checkInventory(plan);
        for (int i = 0; i < plan.size(); i++) {
            List<BmfObject> details = new ArrayList<>();
            // 解析区域、仓库、位置、物料等条件
            JSONObject detail1 = plan.get(i);
            List<Object> areaCodes = returnCodes(detail1.getString("inventoryAreaCode"));
            List<Object> warehouseCodes = returnCodes(detail1.getString("inventoryWarehouseCode"));
            List<Object> positionCodes = returnCodes(detail1.getString("inventoryPositionCode"));
            List<Object> materialCodes = returnCodes(detail1.getString("inventoryMaterialCode"));
            Set<String> passBoxCodes = this.queryTurnoverBoxes(areaCodes, warehouseCodes, positionCodes, materialCodes);
            if (CollectionUtils.isEmpty(passBoxCodes)) {
                continue;
            }
            //校验盘点单下的周转箱是否重复
            checkItem(passBoxCodes, BmfObject.getString("code"));
            List<BmfObject> passBoxes = this.bmfServiceEnhance.findIn("passBoxReal", "passBoxCode", new ArrayList<>(passBoxCodes));
            if (!CollectionUtils.isEmpty(passBoxes)) {
                //组装盘点明细
                for (BmfObject passBox : passBoxes) {
                    if ("unLock".equals(passBox.getString("status"))) {
                        BmfObject detail = new BmfObject("inventorySheetDetail");
                        detail.put("passBoxName", passBox.getString("passBoxName"));
                        detail.put("passBoxCode", passBox.getString("passBoxCode"));
                        detail.put("processName", passBox.getString("processName"));
                        detail.put("processCode", passBox.getString("processCode"));
                        detail.put("processNo", passBox.getString("processNo"));
                        detail.put("materialName", passBox.getString("materialName"));
                        detail.put("materialCode", passBox.getString("materialCode"));
                        detail.put("quantity", passBox.getBigDecimal("quantity"));
                        detail.put("unit", passBox.get("quantityUnit"));
                        detail.put("locationName", passBox.getString("locationName"));
                        detail.put("locationCode", passBox.getString("locationCode"));
                        detail.put("storageLocationCode", passBox.getString("storageLocationCode"));
                        detail.put("storageLocationName", passBox.getString("storageLocationName"));
                        detail.put("location", passBox.get("location"));
                        detail.put("storageLocation", passBox.get("storageLocation"));
                        detail.put("passBoxInventoryStatus", "unInventoried");
                        detail.put("passBoxRealCode", passBox.getString("code"));
                        detail.put("isLedger", passBox.getBoolean("isLedger")!= null ? passBox.getBoolean("isLedger") : false);
                        detail.put("materialClassName", passBox.getAndRefreshBmfObject("material").getAndRefreshBmfObject("materialClassification").getString("name"));
                        detail.put("materialType", passBox.getAndRefreshBmfObject("material").getString("type"));
                        details.add(detail);
                    }
                }
            }
            plan.get(i).put("status", "NotInventoried");
            //盘点计划明细
            plan.get(i).put("inventorySheetDIdAutoMapping", details);
            if (plan.get(i).getBoolean("replayStatus")) {
                //盘点复盘明细
                plan.get(i).put("inventorySheetRIdAutoMapping", details);
            }
        }
    }

    //校验多个盘点单的盘点计划明细是否重复
    private void checkItem(Set<String> passBoxCodes, String inventoryCode) {
        JSONObject inventoryJson = inventoryMapper.findInventoryCodes(passBoxCodes, inventoryCode);
        if (inventoryJson != null) {
            //按客户需求给的提示
            throw new BusinessException("盘点计划下的周转箱:" + inventoryJson.getString("passBoxCode") + "与盘点单:" + inventoryJson.getString("code") + "盘点范围有交叉,不能重复创建盘点单");
        }
    }

    private void checkJsonObject(BmfObject bmfObject, boolean flag) {
        if (bmfObject.getDate("inventoryStartTime") == null) {
            throw new BusinessException("盘点开始时间不能为空!");
        }
        JSONArray jsonArray = bmfObject.getBmfArray("inventorySheetPIdAutoMapping");
        if (!CollectionUtils.isEmpty(jsonArray)) {
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject detail = jsonArray.getJSONObject(i);
                //将字段切割put对应字段李
                this.putItem(detail);
                if (StringUtils.isEmpty(detail.getString("inventoryUserCode")) && StringUtils.isEmpty(detail.getString("inventoryUserName"))) {
                    throw new BusinessException("盘点人不能为空!");
                }
                if (StringUtils.isEmpty(detail.getString("monitorCode")) && StringUtils.isEmpty(detail.getString("monitorName"))) {
                    throw new BusinessException("监盘人不能为空!");
                }
                detail.put("isAudit", false);
                if (flag) {
                    detail.put("auditResult", null);
                }
            }
        } else {
            throw new BusinessException("盘点计划不能为空!");
        }
    }

    private void putItem(JSONObject detail) {
        //除了盘点人和监盘人都是编码在前名称在后
        if (!StringUtils.isEmpty(detail.getString("monitorNameCode"))) {
            JSONObject monitorNameCode = splitText(detail.getString("monitorNameCode"));
            detail.put("monitorName", monitorNameCode.getString("before"));
            detail.put("monitorCode", monitorNameCode.getString("after"));
        } else {
            detail.put("monitorName", null);
            detail.put("monitorCode", null);
        }
        if (!StringUtils.isEmpty(detail.getString("inventoryUserNameCode"))) {
            JSONObject monitorNameCode = splitText(detail.getString("inventoryUserNameCode"));
            detail.put("inventoryUserName", monitorNameCode.getString("before"));
            detail.put("inventoryUserCode", monitorNameCode.getString("after"));
        } else {
            detail.put("inventoryUserName", null);
            detail.put("inventoryUserCode", null);
        }
        if (!StringUtils.isEmpty(detail.getString("inventoryAreaNameCode"))) {
            JSONObject monitorNameCode = splitText(detail.getString("inventoryAreaNameCode"));
            detail.put("inventoryAreaCode", monitorNameCode.getString("before"));
            detail.put("inventoryArea", monitorNameCode.getString("after"));
        } else {
            detail.put("inventoryAreaCode", null);
            detail.put("inventoryArea", null);
        }
        if (!StringUtils.isEmpty(detail.getString("inventoryWarehouseNameCode"))) {
            JSONObject monitorNameCode = splitText(detail.getString("inventoryWarehouseNameCode"));
            detail.put("inventoryWarehouseCode", monitorNameCode.getString("before"));
            detail.put("inventoryWarehouse", monitorNameCode.getString("after"));
        } else {
            detail.put("inventoryWarehouseCode", null);
            detail.put("inventoryWarehouse", null);
        }
        if (!StringUtils.isEmpty(detail.getString("inventoryPositionNameCode"))) {
            JSONObject monitorNameCode = splitText(detail.getString("inventoryPositionNameCode"));
            detail.put("inventoryPositionCode", monitorNameCode.getString("before"));
            detail.put("inventoryPosition", monitorNameCode.getString("after"));
        } else {
            detail.put("inventoryPositionCode", null);
            detail.put("inventoryPosition", null);
        }
        if (!StringUtils.isEmpty(detail.getString("inventoryMaterialNameCode"))) {
            JSONObject monitorNameCode = splitText(detail.getString("inventoryMaterialNameCode"));
            detail.put("inventoryMaterialCode", monitorNameCode.getString("before"));
            detail.put("inventoryMaterial", monitorNameCode.getString("after"));
        } else {
            detail.put("inventoryMaterialCode", null);
            detail.put("inventoryMaterial", null);
        }

    }

    private JSONObject splitText(String text) {
        JSONObject json = new JSONObject();
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        String[] segments = text.split(",");
        for (String segment : segments) {
            String[] parts = segment.split("~");
            before.append(parts[0]).append(",");
            after.append(parts[1]).append(",");
        }
        // 删除最后一个逗号
        before.deleteCharAt(before.length() - 1);
        after.deleteCharAt(after.length() - 1);
        json.put("before", before);
        json.put("after", after);
        return json;
    }


    @Transactional(rollbackFor = Exception.class)
    public InvokeResult update(JSONObject jsonObject) {
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(jsonObject, BMFCLASS);
        if (ObjectUtil.isEmpty(jsonObject.get("id"))) {
            throw new BusinessException("id不能为空!");
        }
        checkJsonObject(bmfObject, false);
        BmfObject inventorySheet = this.bmfService.find(BMFCLASS, jsonObject.getLong("id"));
        List<BmfObject> inventorySheetPlanItems = inventorySheet.getAndRefreshList("inventorySheetPIdAutoMapping");
        for (BmfObject inventorySheetPlanItem : inventorySheetPlanItems) {
            //先删除明细表
            jdbcTemplate.update("delete from `u_inventory_sheet_detail` WHERE  inventory_sheet_d_id = ?", inventorySheetPlanItem.getLong("id"));
            jdbcTemplate.update("delete from `u_inventory_sheet_review` WHERE  inventory_sheet_r_id = ?", inventorySheetPlanItem.getLong("id"));
        }
        //组装子表和明细表
        updateDetail(bmfObject);
        BmfObject inventorySheet1 = BmfUtils.genericFromJsonExt(bmfObject, BMFCLASS);
        this.bmfService.saveOrUpdateBmfObject(inventorySheet1, BMFCLASS);
        return InvokeResult.success();
    }

    @Transactional
    public InvokeResult delete(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new BusinessException("id为空");
        }
        for (Long id : ids) {
            BmfObject inventorySheet = this.bmfService.find("inventorySheet", id);
            String status = inventorySheet.getString("status");
            if (StringUtils.isBlank(status) || !"unIssued".equals(status)) {
                throw new BusinessException("未下达状态时才允许删除,id:" + id);
            }
            this.bmfService.delete(inventorySheet);
        }
        return InvokeResult.success();
    }

    /**
     * 关闭
     */
    @Transactional
    public InvokeResult closeInventory(Long id) {
        if (id == null) {
            throw new BusinessException("id不存在");
        }
        BmfObject inventorySheet = this.bmfService.find(BMFCLASS, id);
        //关闭对应盘点任务 盘点业务code
        String code = inventorySheet.getString("code");
        businessSceneService.close("IssuedInventorySheet", code);
        inventorySheet.put("status", "closed");
        inventorySheet.put("inventoryEndTime", new Date());
        bmfService.updateByPrimaryKeySelective(inventorySheet);
        List<BmfObject> inventorySheetPlan = inventorySheet.getAndRefreshList("inventorySheetPIdAutoMapping");
        List<BmfObject> closePassBoxList = new ArrayList<>();
        for (BmfObject bmfObject : inventorySheetPlan) {
            String status = bmfObject.getString("status");
            if ("Audited".equals(status)&&StringUtils.isNotBlank(status)){
                continue;
            }
            List<BmfObject> passBoxList = bmfObject.getAndRefreshList("inventorySheetDIdAutoMapping");
            for (BmfObject passBox : passBoxList) {
                BmfObject passBoxReal = this.bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL,BmfAttributeConst.CODE,passBox.getString(BmfAttributeConst.PASS_BOX_REAL_CODE));
                if (passBoxReal !=null && !StringUtils.isBlank(passBoxReal.getString("ext_real_material_code"))){
                    //制空盘点的实盘信息
                    passBoxReal.put("ext_real_material_name", null);
                    passBoxReal.put("ext_real_material_code", null);
                    passBoxReal.put("ext_real_location_name", null);
                    passBoxReal.put("ext_real_location_code", null);
                    passBoxReal.put("ext_real_quantity", null);
                    passBoxReal.put("ext_inventory_user", null);
                    passBoxReal.put("ext_real_process_code", null);
                    passBoxReal.put("ext_real_process_name", null);
                    passBoxReal.put("ext_real_process_no", null);
                    passBoxReal.put("ext_plan_id", null);
                    passBoxReal.put("inventoryLocking", false);
                    closePassBoxList.add(passBoxReal);
                }
            }
        }
        if (closePassBoxList.size()>0){
            sceneGroovyService.batchSynchronizePassBoxInfo(closePassBoxList, "PC","盘点关闭");
        }
        //同步周转箱实时信息
        return InvokeResult.success();
    }

    /**
     * 下达
     */
    @Transactional
    public InvokeResult issued(JSONObject jsonObject) {
        Long id = jsonObject.getLong("id");
        if (id == null) {
            throw new BusinessException("下达失败: id不能为空!");
        }
        //获取盘点单详情
        BmfObject inventorySheet = this.bmfService.find(BMFCLASS, id);
        //先关闭
        String code = inventorySheet.getString("code");
        businessSceneService.close("IssuedInventorySheet", code);
        //盘点单盘点计划
        List<BmfObject> inventorySheetPlans = inventorySheet.getAndRefreshList("inventorySheetPIdAutoMapping");
        //下达
        for (BmfObject inventorySheetPlan : inventorySheetPlans) {
            inventorySheetPlan.getAndRefreshBmfObject("inventorySheetPId");
            inventorySheetPlan.getAndRefreshBmfObject("monitor");
            this.logisticsService.assign(inventorySheetPlan, "IssuedInventorySheet");
        }
        // 下达成功修改单据状态
        inventorySheet.put("status", "unClear");
        this.bmfService.updateByPrimaryKeySelective(inventorySheet);
        // 锁定盘点单下的周转箱
        List<String> passBoxRealCodes = inventoryMapper.findInventorySheetPassBoxRealCode(id);
        if (!CollectionUtils.isEmpty(passBoxRealCodes)){
            inventoryMapper.updateInventoryLocking(passBoxRealCodes,true);
        }
        return InvokeResult.success();
    }


    public InvokeResult detail(Long id) {
        if (id == null) {
            throw new BusinessException("id不存在");
        }
        BmfObject bmfObject = this.bmfService.find(BMFCLASS, id);
        List<BmfObject> planArray = bmfObject.getAndRefreshList("inventorySheetPIdAutoMapping");
        if (!CollectionUtils.isEmpty(planArray)) {
            bmfObject.put("inventorySheetPIdAutoMapping", planArray);
        }
        return InvokeResult.success(bmfObject);
    }


    public InvokeResult getSheetDetail(Long planId) {
        if (planId == null) {
            throw new BusinessException("盘点计划id不能为空!");
        }
        BmfObject inventorySheetPlan = this.bmfService.find("inventorySheetPlan", planId);
        if (inventorySheetPlan == null) {
            throw new BusinessException("盘点计划不存在,id:" + planId);
        }
        //调二开详情分页,不用刷子表
//        List<BmfObject> passBoxDList = inventorySheetPlan.getAndRefreshList("inventorySheetDIdAutoMapping");
//        List<BmfObject> passBoxRList = inventorySheetPlan.getAndRefreshList("inventorySheetRIdAutoMapping");
//        passBoxDList.forEach(passBox -> {
//            passBox.getAndRefreshBmfObject("unit");
//            passBox.getAndRefreshBmfObject("inventoryUsers");
//        });
//        passBoxRList.forEach(passBox -> {
//            passBox.getAndRefreshBmfObject("unit");
//            passBox.getAndRefreshBmfObject("inventoryUsers");
//        });
//        inventorySheetPlan.put("inventorySheetDIdAutoMapping", passBoxDList);
//        inventorySheetPlan.put("inventorySheetRIdAutoMapping", passBoxRList);
        return InvokeResult.success(inventorySheetPlan);
    }

    /**
     * 审核(叶勇)
     */
    @Transactional(rollbackFor = Exception.class)
    public InvokeResult approve(Long planId) {
        BmfObject bmfObject = bmfService.find("inventorySheetPlan", planId);
        //权限控制
        String status = bmfObject.getString("status");
        String auditResult = bmfObject.getString("auditResult");
        if ("Audited".equals(status)) {
            throw new BusinessException("已审核,请勿重复审核!");
        }
        if (!"audit".equals(status) || !"pass".equals(auditResult)) {
            throw new BusinessException("只有已稽核且稽核结果通过的数据可以审核!");
        }
        List<BmfObject> passBoxList = null;
        //盘点明细周转箱
        if (bmfObject.getBoolean("replayStatus")) {
            passBoxList = bmfObject.getAndRefreshList("inventorySheetRIdAutoMapping");
        } else {
            passBoxList = bmfObject.getAndRefreshList("inventorySheetDIdAutoMapping");
        }
        //需要审批的周转箱
        List<BmfObject> approvePassBoxList = new ArrayList<>();
        for (BmfObject jsonObject : passBoxList) {
            String passBoxInventoryStatus = jsonObject.getString("passBoxInventoryStatus");
            String auditResultItem = jsonObject.getString("auditResult");
            if ("unInventoried".equals(passBoxInventoryStatus)) {
                if (bmfObject.getBoolean("replayStatus")) {
                    throw new BusinessException("还有未复盘的周转箱:" + jsonObject.getString("passBoxCode") + "无法审核");
                } else {
                    throw new BusinessException("还有未初盘的周转箱:" + jsonObject.getString("passBoxCode") + "无法审核");
                }
            }
            if (StringUtils.isNotBlank(auditResultItem) && !"pass".equals(auditResultItem)) {
                if (bmfObject.getBoolean("replayStatus")) {
                    throw new BusinessException("当前复盘周转箱:" + jsonObject.getString("passBoxCode") + "未稽核通过,不允许审核");
                } else {
                    throw new BusinessException("当前初盘周转箱:" + jsonObject.getString("passBoxCode") + "未稽核通过,不允许审核");
                }
            }
        }
        List<JSONObject> jsonList = new ArrayList<>();
        List<BmfObject> updateInventoryStatusList=new ArrayList<>();
        for (BmfObject passBox : passBoxList) {
            BmfObject passBoxReal = this.bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL,BmfAttributeConst.CODE,passBox.getString(BmfAttributeConst.PASS_BOX_REAL_CODE));
            if (passBoxReal==null){
                throw new BusinessException("周转箱实时信息不存在,编码:" + passBox.getString(BmfAttributeConst.PASS_BOX_REAL_CODE));
            }
            BmfObject updateInventoryStatus = new BmfObject(passBox.getBmfClassName());
            updateInventoryStatus.put("passBoxInventoryStatus", "Audited");
            updateInventoryStatus.put("id", passBox.getPrimaryKeyValue());
            updateInventoryStatusList.add(updateInventoryStatus);
            //BmfObject updatePassBox = new BmfObject(BmfClassNameConst.PASS_BOX_REAL);
            //制空盘点的实盘信息
            passBoxReal.put("ext_real_material_name", null);
            passBoxReal.put("ext_real_material_code", null);
            passBoxReal.put("ext_real_location_name", null);
            passBoxReal.put("ext_real_location_code", null);
            passBoxReal.put("ext_real_quantity", null);
            passBoxReal.put("ext_inventory_user", null);
            passBoxReal.put("ext_real_process_code", null);
            passBoxReal.put("ext_real_process_name", null);
            passBoxReal.put("ext_real_process_no", null);
            passBoxReal.put("ext_plan_id", null);
            //盘点锁定解除
            passBoxReal.put("inventoryLocking",false);
            //发生仓库变更的数据
            if (!passBox.get("locationCode").equals(passBox.get("realLocationCode"))) {
                String fromWarehouse = inventoryMapper.locationFindWarehouse(passBox.getString("locationCode"));
                if (StringUtils.isEmpty(fromWarehouse)) {
                    throw new BusinessException("盘点周转箱:" + passBox.getString("passBoxCode") + "未找到位置:" + passBox.getString("locationCode") + "对应的仓库信息");
                }
                String toWarehouse = inventoryMapper.locationFindWarehouse(passBox.getString("realLocationCode"));
                if (StringUtils.isEmpty(toWarehouse)) {
                    throw new BusinessException("盘点周转箱:" + passBox.getString("passBoxCode") + "未找到实盘位置:" + passBox.getString("realLocationCode") + "对应的仓库信息");
                }
                if (!fromWarehouse.equals(toWarehouse)) {
                    //发生仓库变动的数据集合
                    JSONObject locationTransfer = new JSONObject();
                    locationTransfer.put("fromWarehouse", fromWarehouse);
                    locationTransfer.put("toWarehouse", toWarehouse);
                    locationTransfer.put("materialCode", passBox.get("realMaterialCode"));
                    locationTransfer.put("quantity", passBox.get("realQuantity"));
                    locationTransfer.put("U_WTRType", "MES库位转移");
                    BmfObject inventoryUsers = passBox.getAndRefreshBmfObject("inventoryUsers");
                    locationTransfer.put("U_MesName", inventoryUsers.getString("name"));
                    locationTransfer.put("U_MesCode", inventoryUsers.getString("code"));
                    locationTransfer.put("U_MesType", "盘点审核");
                    jsonList.add(locationTransfer);
                }
            }
            //修改周转箱实时的数据
            passBoxReal.put("processCode", passBox.get("realProcessCode"));
            passBoxReal.put("processName", passBox.get("realProcessName"));
            passBoxReal.put("processNo", passBox.get("realProcessNo"));
            passBoxReal.put("quantity", passBox.get("realQuantity"));
            if (!passBox.getString("materialCode").equals(passBox.get("realMaterialCode"))){
                BmfObject material = this.bmfService.findByUnique(BmfClassNameConst.MATERIAL, "code", passBox.get("realMaterialCode"));
                passBoxReal.put("materialCode", passBox.get("realMaterialCode"));
                passBoxReal.put("materialName", passBox.get("realMaterialName"));
                passBoxReal.put("material", material);
            }
            if (!passBox.getString("locationCode").equals(passBox.get("realLocationCode"))){
                BmfObject location = this.bmfService.findByUnique("location", "code", passBox.get("realLocationCode"));
                passBoxReal.put("locationCode", passBox.get("realLocationCode"));
                passBoxReal.put("locationName", passBox.get("realLocationName"));
                passBoxReal.put("location", location);
            }
            BmfObject user = this.bmfService.find("user", passBox.getJSONObject("inventoryUsers").getLong("id"));
            if (user != null) {
                passBoxReal.put("resourceCode", user.get("code"));
                passBoxReal.put("resourceName", user.get("name"));
                passBoxReal.put("resourceTypeCode", "user");
            }
            approvePassBoxList.add(passBoxReal);
        }
        if (approvePassBoxList.size() == 0) {
            throw new BusinessException("当前盘点对象的无周转箱审核");
        }
        //调整盘点周转箱明细
        this.bmfService.updateByPrimaryKeySelective(updateInventoryStatusList);
        //更新盘点计划状态
        BmfObject update = new BmfObject("inventorySheetPlan");
        update.put("id", planId);
        update.put("status", "Audited");
        this.bmfService.updateByPrimaryKeySelective(update);

        BmfObject inventorySheet = this.bmfService.find("inventorySheet", bmfObject.getJSONObject("inventorySheetPId").getLong("id"));
        inventorySheet.put("status", "completed");
        this.bmfService.updateByPrimaryKeySelective(inventorySheet);

        sceneGroovyService.batchSynchronizePassBoxInfo(approvePassBoxList, "PC","盘点审核");
        if (jsonList.size() > 0) {
            //库位转移同步SAP
            BmfObject inventoryReceipt = new BmfObject("inventoryReceipt");
            inventoryReceipt.put("params", JSONObject.toJSONString(jsonList));
            //单据类型 19 转储单
            inventoryReceipt.put("type", "19");
            inventoryReceipt.put("createDate", new Date());
            inventoryReceipt.put("updateDate", new Date());
            inventoryReceipt.put("status", "0");
            inventoryReceipt.put("description", "盘点审核同步到SAP库存转储单");
            //位置转移同步到SAP库存转储单
            bmfService.saveOrUpdate(inventoryReceipt);
        }
        //盘点审核完关闭对应的PDA盘点任务
        closeGNTask(bmfObject, inventorySheet);
        return InvokeResult.success();
    }

    private void closeGNTask(BmfObject bmfObject, BmfObject inventorySheet) {
        if (bmfObject.getBoolean("replayStatus")) {
            JSONObject result1= inventoryMapper.findGN1414Task(bmfObject.getString("inventoryAreaCode"),
                    bmfObject.getString("inventoryWarehouseCode"),
                    bmfObject.getString("inventoryPositionCode"),
                    bmfObject.getString("inventoryMaterialCode"),
                    inventorySheet.getString("code"));
            JSONObject result2= inventoryMapper.findGN3233Task(bmfObject.getString("inventoryAreaCode"),
                    bmfObject.getString("inventoryWarehouseCode"),
                    bmfObject.getString("inventoryPositionCode"),
                    bmfObject.getString("inventoryMaterialCode"),
                    inventorySheet.getString("code"));
            if (result1.getLong("logistics_status") !=3 && result1.getLong("logistics_status")  != 4) {
                BmfObject updateGN1414 = new BmfObject("GN1414");
                updateGN1414.put("id", result1.getLong("id"));
                updateGN1414.put("logisticsStatus", "3");
                BmfObject updateGN3233 = new BmfObject("GN3233");
                updateGN3233.put("id",  result2.getLong("id"));
                updateGN3233.put("logisticsStatus", "3");
                this.bmfService.updateByPrimaryKeySelective(updateGN1414);
                this.bmfService.updateByPrimaryKeySelective(updateGN3233);
            }
        }else {
            JSONObject result= inventoryMapper.findGN1414Task(bmfObject.getString("inventoryAreaCode"),
                    bmfObject.getString("inventoryWarehouseCode"),
                    bmfObject.getString("inventoryPositionCode"),
                    bmfObject.getString("inventoryMaterialCode"),
                    inventorySheet.getString("code"));
            if (result.getLong("logistics_status") !=3 && result.getLong("logistics_status")  != 4) {
                BmfObject updateGN1414 = new BmfObject("GN1414");
                updateGN1414.put("id", result.getLong("id"));
                updateGN1414.put("logisticsStatus", "3");
                this.bmfService.updateByPrimaryKeySelective(updateGN1414);
            }
        }
    }

    public ResponseEntity<byte[]> export(Long planId) throws IOException {
        BmfObject inventorySheetPlan = this.bmfService.find("inventorySheetPlan", planId);
        //明细
        List<BmfObject> passBoxList = inventorySheetPlan.getAndRefreshList("inventorySheetDIdAutoMapping");
        Workbook wb = new SXSSFWorkbook();
        Sheet sheet1 = wb.createSheet("盘点计划");
        Sheet sheet2 = wb.createSheet("盘点明细");
        Sheet sheet3 = wb.createSheet("复盘明细");
        Row planRow0 = sheet1.createRow(0);
        planRow0.createCell(0).setCellValue("盘点区域编码");
        planRow0.createCell(1).setCellValue("盘点区域名称");
        planRow0.createCell(2).setCellValue("盘点仓库编码");
        planRow0.createCell(3).setCellValue("盘点仓库名称");
        planRow0.createCell(4).setCellValue("盘点位置编码");
        planRow0.createCell(5).setCellValue("盘点位置名称");
        planRow0.createCell(6).setCellValue("盘点物料编码");
        planRow0.createCell(7).setCellValue("盘点物料名称");
        planRow0.createCell(8).setCellValue("盘点人");
        planRow0.createCell(9).setCellValue("监盘人");
        planRow0.createCell(10).setCellValue("是否复盘");
        //盘点计划
        Row planRow1 = sheet1.createRow(1);
        planRow1.createCell(0).setCellValue(inventorySheetPlan.getString("inventoryAreaCode"));
        planRow1.createCell(1).setCellValue(inventorySheetPlan.getString("inventoryArea"));
        planRow1.createCell(2).setCellValue(inventorySheetPlan.getString("inventoryWarehouseCode"));
        planRow1.createCell(3).setCellValue(inventorySheetPlan.getString("inventoryWarehouse"));
        planRow1.createCell(4).setCellValue(inventorySheetPlan.getString("inventoryPositionCode"));
        planRow1.createCell(5).setCellValue(inventorySheetPlan.getString("inventoryPosition"));
        planRow1.createCell(6).setCellValue(inventorySheetPlan.getString("inventoryMaterialCode"));
        planRow1.createCell(7).setCellValue(inventorySheetPlan.getString("inventoryMaterial"));
        //盘点人
        planRow1.createCell(8).setCellValue(inventorySheetPlan.getString("inventoryUserName"));
        //监盘人
        planRow1.createCell(9).setCellValue(inventorySheetPlan.getString("monitorName"));
        if (inventorySheetPlan.getBoolean("replayStatus")) {
            planRow1.createCell(10).setCellValue("是");
        } else {
            planRow1.createCell(10).setCellValue("否");
        }
        extracted(passBoxList, sheet2);
        if (inventorySheetPlan.getBoolean("replayStatus")) {
            //复盘
            List<BmfObject> passBoxItem = inventorySheetPlan.getAndRefreshList("inventorySheetRIdAutoMapping");
            extracted(passBoxItem, sheet3);
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        wb.write(byteArrayOutputStream);

        String fileName = "盘点计划明细";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF-8") + ".xlsx")
                .body(byteArrayOutputStream.toByteArray());
    }

    private void extracted(List<BmfObject> passBoxList, Sheet sheet) {
        Row detailRow0 = sheet.createRow(0);
        detailRow0.createCell(0).setCellValue("周转箱编码");
        detailRow0.createCell(1).setCellValue("周转箱名称");
        detailRow0.createCell(2).setCellValue("是否台账周转箱");
        detailRow0.createCell(3).setCellValue("工序名称");
        detailRow0.createCell(4).setCellValue("工序编码");
        detailRow0.createCell(5).setCellValue("物料编码");
        detailRow0.createCell(6).setCellValue("物料名称");
        detailRow0.createCell(7).setCellValue("物料类型");
        detailRow0.createCell(8).setCellValue("数量");
        detailRow0.createCell(9).setCellValue("单位");
        detailRow0.createCell(10).setCellValue("位置名称");
        detailRow0.createCell(11).setCellValue("位置编码");
        detailRow0.createCell(12).setCellValue("实盘工序名称");
        detailRow0.createCell(13).setCellValue("实盘工序编码");
        detailRow0.createCell(14).setCellValue("实盘物料名称");
        detailRow0.createCell(15).setCellValue("实盘物料编码");
        detailRow0.createCell(16).setCellValue("实盘位置名称");
        detailRow0.createCell(17).setCellValue("实盘位置编码");
        detailRow0.createCell(18).setCellValue("实盘数量");
        detailRow0.createCell(19).setCellValue("周转箱盘点状态");
        detailRow0.createCell(20).setCellValue("稽核结果");
        detailRow0.createCell(21).setCellValue("盘点组号");
        detailRow0.createCell(22).setCellValue("盘点人");
        detailRow0.createCell(23).setCellValue("监盘人");
        detailRow0.createCell(24).setCellValue("盘点差异数量");
        if (!CollectionUtils.isEmpty(passBoxList)) {
            for (int i = 0; i < passBoxList.size(); i++) {
                BmfObject passBox = passBoxList.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(passBox.getString("passBoxCode"));
                row.createCell(1).setCellValue(passBox.getString("passBoxName"));
                Boolean isLedger = passBox.getBoolean("isLedger");
                if (isLedger != null && passBox.getBoolean("isLedger")){
                    row.createCell(2).setCellValue("是");
                } else {
                    row.createCell(2).setCellValue("否");
                }
                row.createCell(3).setCellValue(passBox.getString("processName"));
                row.createCell(4).setCellValue(passBox.getString("processCode"));
                row.createCell(5).setCellValue(passBox.getString("materialCode"));
                row.createCell(6).setCellValue(passBox.getString("materialName"));
                row.createCell(7).setCellValue(getEnumName(passBox.getString("materialType"),"materialTypeV2"));
                row.createCell(8).setCellValue(ValueUtil.toBigDecimal(passBox.getBigDecimal("quantity"), BigDecimal.ZERO).toString());
                JSONObject unit = passBox.getJSONObject("unit");
                if (unit != null) {
                    row.createCell(9).setCellValue(passBox.getAndRefreshBmfObject("unit").getString("name"));
                }
                row.createCell(10).setCellValue(passBox.getString("locationName"));
                row.createCell(11).setCellValue(passBox.getString("locationCode"));
                row.createCell(12).setCellValue(passBox.getString("realProcessName"));
                row.createCell(13).setCellValue(passBox.getString("realProcessCode"));
                row.createCell(14).setCellValue(passBox.getString("realMaterialName"));
                row.createCell(15).setCellValue(passBox.getString("realMaterialCode"));
                row.createCell(16).setCellValue(passBox.getString("realLocationName"));
                row.createCell(17).setCellValue(passBox.getString("realLocationCode"));
                row.createCell(18).setCellValue(ValueUtil.toBigDecimal(passBox.getBigDecimal("realQuantity"), BigDecimal.ZERO).toString());
                String passBoxInventoryStatus = passBox.getString("passBoxInventoryStatus");
                if ("unInventoried".equals(passBoxInventoryStatus)) {
                    passBoxInventoryStatus = "待盘点";
                } else if ("Inventoried".equals(passBoxInventoryStatus)) {
                    passBoxInventoryStatus = "已盘点";
                } else if ("Audited".equals(passBoxInventoryStatus)) {
                    passBoxInventoryStatus = "已审核";
                } else if ("replay".equals(passBoxInventoryStatus)) {
                    passBoxInventoryStatus = "已复盘";
                } else if ("audit".equals(passBoxInventoryStatus)) {
                    passBoxInventoryStatus = "已稽核";
                } else {
                    passBoxInventoryStatus = "";
                }
                String auditResult = passBox.getString("auditResult");
                if ("pass".equals(auditResult)) {
                    auditResult = "通过";
                } else if ("noPass".equals(auditResult)) {
                    auditResult = "不通过";
                } else {
                    auditResult = "";
                }
                row.createCell(19).setCellValue(passBoxInventoryStatus);
                row.createCell(20).setCellValue(auditResult);
                row.createCell(21).setCellValue(passBox.getString("inventoryGroup"));
                if (passBox.getJSONObject("inventoryUsers") != null) {
                    row.createCell(22).setCellValue(passBox.getAndRefreshBmfObject("inventoryUsers").getString("name"));
                }
                row.createCell(23).setCellValue(passBox.getString("monitorName"));
                row.createCell(24).setCellValue(ValueUtil.toBigDecimal(passBox.getBigDecimal("inventoryDifferenceNum"), BigDecimal.ZERO).toString());
            }
        }
    }


    //校验一张盘点单的计划表明细是否重复
    private void checkInventory(List<BmfObject> details) {
        Set<String> turnoverBoxSet = new HashSet<>();
        for (int i = 0; i < details.size(); i++) {
            JSONObject detail = details.get(i);
            // 解析区域、仓库、位置、物料等条件
            List<Object> areaCodes = returnCodes(detail.getString("inventoryAreaCode"));
            List<Object> warehouseCodes = returnCodes(detail.getString("inventoryWarehouseCode"));
            List<Object> positionCodes = returnCodes(detail.getString("inventoryPositionCode"));
            List<Object> materialCodes = returnCodes(detail.getString("inventoryMaterialCode"));
            if (CollectionUtils.isEmpty(areaCodes) && CollectionUtils.isEmpty(warehouseCodes) && CollectionUtils.isEmpty(positionCodes)) {
                throw new BusinessException("盘点区域、盘点仓库、盘点位置至少有一个必填");
            }
            Set<String> queriedTurnoverBoxes = queryTurnoverBoxes(areaCodes, warehouseCodes, positionCodes, materialCodes);
            if (CollectionUtils.isEmpty(queriedTurnoverBoxes)) {
                continue;
            }
            //校验周转箱的唯一性
            for (String passBoxCode : queriedTurnoverBoxes) {
                if (!turnoverBoxSet.add(passBoxCode)) {
                    //添加失败,说明该周转箱已存在,抛出异常
                    throw new BusinessException("计划明细周转箱重复,请检查盘点计划: " + passBoxCode);
                }
            }
        }
    }

    //根据区域、仓库、位置、物料联动条件查询库存下的周转箱
    private Set<String> queryTurnoverBoxes(List<Object> areaCodes, List<Object> warehouseCodes, List<Object> positionCodes, List<Object> materialCodes) {
        //位置不为空
        if (!CollectionUtils.isEmpty(positionCodes)) {
            Set<String> passBoxCodes = inventoryMapper.findPassBoxCodes(positionCodes, materialCodes);
            return passBoxCodes;
        }
        //仓库不为空
        if (!CollectionUtils.isEmpty(warehouseCodes)) {
            List<Object> locationCodes = inventoryMapper.findLocationCodes(warehouseCodes);
            if (CollectionUtils.isEmpty(locationCodes)) {
                return null;
            }
            //查询位置是否启动
            locationCodes = this.selectLocationCodes(locationCodes);
            if (CollectionUtils.isEmpty(locationCodes)) {
                return null;
            }
            Set<String> passBoxCodes = inventoryMapper.findPassBoxCodes(locationCodes, materialCodes);
            return passBoxCodes;
        }
        //区域不为空
        if (!CollectionUtils.isEmpty(areaCodes)) {
            //po确认只查一层(区域和仓库都是直接关联位置)
            List<Object> locationCodes = inventoryMapper.areaFindLocation(areaCodes);
            if (CollectionUtils.isEmpty(locationCodes)) {
                return null;
            }
            //查询位置是否启动
            locationCodes = this.selectLocationCodes(locationCodes);
            if (CollectionUtils.isEmpty(locationCodes)) {
                return null;
            }
            Set<String> passBoxCodes = inventoryMapper.findPassBoxCodes(locationCodes, materialCodes);
            return passBoxCodes;
        }
        return null;
    }


    /**
     * @param bmfClassName 查询对象
     * @param areas        区域编码
     * @param warehouse    仓库编码
     * @param locations    位置编码
     * @return {@link List}<{@link BmfObject}>
     */
    public Object getLinkageQuery(String bmfClassName, String areas, String warehouse, String locations) {
        String[] emptyArray = new String[0];
        //选择区域
        if (BmfClassNameConst.AREA.equals(bmfClassName)) {
            return this.bmfService.find(BmfClassNameConst.AREA);
        }
        //选择仓库
        if (BmfClassNameConst.WAREHOUSE.equals(bmfClassName)) {
            if (StringUtils.isNotBlank(areas)) {
                List<Object> areaCodes = returnCodes(areas);
                if (CollectionUtils.isEmpty(areaCodes)) {
                    throw new BusinessException("盘点区域主数据不存在");
                }
                List<Object> warehouseCodes = inventoryMapper.findWarehouseCodes(areaCodes);
                if (CollectionUtils.isEmpty(warehouseCodes)) {
                    return emptyArray;
                }
                return this.warehouse(warehouseCodes);
            } else {
                return this.bmfService.find(BmfClassNameConst.WAREHOUSE, Arrays.asList(
                        Restriction.builder().conjunction(Conjunction.AND)
                                .attributeName("status")
                                .operationType(OperationType.EQUAL)
                                .values(Collections.singletonList(1))
                                .build()));
            }
        }
        //选择位置
        if (BmfClassNameConst.LOCATION.equals(bmfClassName)) {
            if (StringUtils.isEmpty(warehouse) && StringUtils.isEmpty(areas)) {
                throw new BusinessException("请先选择仓库或者区域");
            }
            if (StringUtils.isNotBlank(warehouse)) {
                List<Object> warehouseCodes = returnCodes(warehouse);
                //仓库不为空
                List<Object> locationCodes = inventoryMapper.findLocationCodes(warehouseCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyArray;
                }
                return this.location(locationCodes);
            } else if (StringUtils.isNotBlank(areas)) {
                List<Object> areaCodes = returnCodes(areas);
                //po确定只查一层
                List<Object> locationCodes = inventoryMapper.areaFindLocation(areaCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyArray;
                }
                return this.location(locationCodes);
            } else if (StringUtils.isBlank(areas)) {
                //区域、仓库为空
                return this.bmfService.find(BmfClassNameConst.LOCATION, Arrays.asList(
                        Restriction.builder().conjunction(Conjunction.AND)
                                .attributeName(BmfAttributeConst.STATUS)
                                .operationType(OperationType.EQUAL)
                                .values(Collections.singletonList(1))
                                .build()));
            }
        }
        if (StringUtils.isEmpty(areas) && StringUtils.isEmpty(warehouse) && StringUtils.isEmpty(locations)) {
            throw new BusinessException("盘点区域、盘点仓库、盘点位置至少有一个必填");
        }
        //选择物料
        if (BmfClassNameConst.MATERIAL.equals(bmfClassName)) {
            if (StringUtils.isNotBlank(locations)) {
                //位置不为空
                List<Object> locationCodes = returnCodes(locations);
                List<Object> materialCodes = inventoryMapper.findMaterialCodes(locationCodes);
                if (CollectionUtils.isEmpty(materialCodes)) {
                    return emptyArray;
                }
                return materials(materialCodes);
            }
            if (StringUtils.isNotBlank(warehouse)) {
                //仓库不为空
                List<Object> warehouseCodes = returnCodes(warehouse);
                //仓库关联位置编码
                List<Object> locationCodes = inventoryMapper.findLocationCodes(warehouseCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyArray;
                }
                //查询位置是否启动
                locationCodes = this.selectLocationCodes(locationCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyArray;
                }
                List<Object> materialCodes = inventoryMapper.findMaterialCodes(locationCodes);
                if (CollectionUtils.isEmpty(materialCodes)) {
                    return emptyArray;
                }
                return materials(materialCodes);
            }
            if (StringUtils.isNotBlank(areas)) {
                //区域不为空
                List<Object> areaCodes = returnCodes(areas);
                //po确定只查一层
                List<Object> locationCodes = inventoryMapper.areaFindLocation(areaCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyArray;
                }
                //查询位置是否启动
                locationCodes = this.selectLocationCodes(locationCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyArray;
                }
                List<Object> materialCodes = inventoryMapper.findMaterialCodes(locationCodes);
                if (CollectionUtils.isEmpty(materialCodes)) {
                    return emptyArray;
                }
                return materials(materialCodes);
            }
        }
        return emptyArray;
    }


    //根据codes查询启用位置
    private List<Object> selectLocationCodes(List<Object> codes) {
        List<BmfObject> bmfObjects = this.bmfService.find(BmfClassNameConst.LOCATION, Arrays.asList(
                Restriction.builder()
                        .conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.STATUS)
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(1))
                        .build(),
                Restriction.builder().conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.CODE)
                        .operationType(OperationType.IN)
                        .values(codes).
                        build()));
        List<Object> locationCodes = bmfObjects.stream().map(t -> t.getString("code")).collect(Collectors.toList());
        return locationCodes;
    }

    //根据id查询启用仓库
    private List<Object> selectWarehouseCodes(List<Object> codes) {
        List<BmfObject> bmfObjects = this.bmfService.find(BmfClassNameConst.WAREHOUSE, Arrays.asList(
                Restriction.builder()
                        .conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.STATUS)
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(1))
                        .build(),
                Restriction.builder().conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.CODE)
                        .operationType(OperationType.IN)
                        .values(codes).
                        build()));
        List<Object> warehouseCodes = bmfObjects.stream().map(t -> t.getString("code")).collect(Collectors.toList());
        return warehouseCodes;
    }


    //根据id查询启用区域
    private List<Object> selectAreaCodes(List<Object> ids) {
        List<BmfObject> bmfObjects = this.bmfService.find(BmfClassNameConst.AREA, Arrays.asList(
                Restriction.builder().conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.ID)
                        .operationType(OperationType.IN)
                        .values(ids).
                        build()));
        List<Object> codes = bmfObjects.stream().map(t -> t.getString("code")).collect(Collectors.toList());
        return codes;
    }


    //根据位置编码查询启用位置
    private List<BmfObject> location(List<Object> locationCodes) {
        return this.bmfService.find(BmfClassNameConst.LOCATION, Arrays.asList(
                Restriction.builder()
                        .conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.STATUS)
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(1))
                        .build(),
                Restriction.builder().conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.CODE)
                        .operationType(OperationType.IN)
                        .values(locationCodes).build()));
    }

    //分页查询启用仓库
    private Page<BmfObject> pageFindWarehouse(List<Object> warehouseCodes, String keyword, Pageable pageable) {
        List<Restriction> restrictions = new ArrayList<>();
        List<CombRestriction> combRestrictions = new ArrayList<>();
        CombRestriction keywordCombRestriction1 = CombRestriction.builder().conjunction(Conjunction.AND).build();
        restrictions.add(Restriction.builder()
                .bmfClassName(BmfClassNameConst.WAREHOUSE)
                .conjunction(Conjunction.AND)
                .attributeName("status")
                .operationType(OperationType.EQUAL)
                .values(Collections.singletonList(1))
                .build());
        if (!CollectionUtils.isEmpty(warehouseCodes)) {
            restrictions.add(Restriction.builder()
                    .bmfClassName(BmfClassNameConst.WAREHOUSE)
                    .conjunction(Conjunction.AND)
                    .attributeName(BmfAttributeConst.CODE)
                    .operationType(OperationType.IN)
                    .values(warehouseCodes)
                    .build());
        }
        keywordCombRestriction1.setRestrictions(restrictions);
        combRestrictions.add(keywordCombRestriction1);
        if (StringUtils.isNotBlank(keyword)) {
            CombRestriction keywordCombRestriction = CombRestriction.builder().conjunction(Conjunction.AND).build();
            //模糊查询
            keywordCombRestriction.setRestrictions(Arrays.asList(
                    Restriction.builder()
                            .bmfClassName(BmfClassNameConst.WAREHOUSE)
                            .conjunction(Conjunction.AND)
                            .attributeName(BmfAttributeConst.NAME)
                            .columnName(BmfAttributeConst.NAME)
                            .operationType(OperationType.LIKE)
                            .values(Collections.singletonList("%" + keyword + "%"))
                            .build(),
                    Restriction.builder()
                            .bmfClassName(BmfClassNameConst.WAREHOUSE)
                            .conjunction(Conjunction.OR)
                            .attributeName(BmfAttributeConst.CODE)
                            .columnName(BmfAttributeConst.CODE)
                            .operationType(OperationType.LIKE)
                            .values(Collections.singletonList("%" + keyword + "%"))
                            .build()

            ));
            combRestrictions.add(keywordCombRestriction);
        }
        Where where = Where.builder().combRestrictions(combRestrictions).build();
        return this.bmfService.findPage(BmfClassNameConst.WAREHOUSE, where, pageable);
    }

    //分页查询启用物料
    private Page<BmfObject> pageFindMaterials(List<Object> materialCodes, String keyword, Pageable pageable) {
        List<Restriction> restrictions = new ArrayList<>();
        List<CombRestriction> combRestrictions = new ArrayList<>();
        CombRestriction keywordCombRestriction1 = CombRestriction.builder().conjunction(Conjunction.AND).build();
        restrictions.add(Restriction.builder()
                .bmfClassName(BmfClassNameConst.MATERIAL)
                .conjunction(Conjunction.AND)
                .attributeName("status")
                .operationType(OperationType.EQUAL)
                .values(Collections.singletonList(1))
                .build());
        if (!CollectionUtils.isEmpty(materialCodes)) {
            restrictions.add(Restriction.builder()
                    .bmfClassName(BmfClassNameConst.MATERIAL)
                    .conjunction(Conjunction.AND)
                    .attributeName(BmfAttributeConst.CODE)
                    .operationType(OperationType.IN)
                    .values(materialCodes)
                    .build());
        }
        keywordCombRestriction1.setRestrictions(restrictions);
        combRestrictions.add(keywordCombRestriction1);
        if (StringUtils.isNotBlank(keyword)) {
            CombRestriction keywordCombRestriction = CombRestriction.builder().conjunction(Conjunction.AND).build();
            //模糊查询
            keywordCombRestriction.setRestrictions(Arrays.asList(
                    Restriction.builder()
                            .bmfClassName(BmfClassNameConst.MATERIAL)
                            .conjunction(Conjunction.AND)
                            .attributeName(BmfAttributeConst.NAME)
                            .columnName(BmfAttributeConst.NAME)
                            .operationType(OperationType.LIKE)
                            .values(Collections.singletonList("%" + keyword + "%"))
                            .build(),
                    Restriction.builder()
                            .bmfClassName(BmfClassNameConst.MATERIAL)
                            .conjunction(Conjunction.OR)
                            .attributeName(BmfAttributeConst.CODE)
                            .columnName(BmfAttributeConst.CODE)
                            .operationType(OperationType.LIKE)
                            .values(Collections.singletonList("%" + keyword + "%"))
                            .build()

            ));
            combRestrictions.add(keywordCombRestriction);
        }

        Where where = Where.builder().combRestrictions(combRestrictions).build();
        return this.bmfService.findPage(BmfClassNameConst.MATERIAL, where, pageable);
    }

    //分页查询启用物料
    private Page<BmfObject> pageFindLocation(List<Object> locationCodes, String keyword, Pageable pageable) {
        List<Restriction> restrictions = new ArrayList<>();
        List<CombRestriction> combRestrictions = new ArrayList<>();
        CombRestriction keywordCombRestriction1 = CombRestriction.builder().conjunction(Conjunction.AND).build();
        restrictions.add(Restriction.builder()
                .bmfClassName(BmfClassNameConst.LOCATION)
                .conjunction(Conjunction.AND)
                .attributeName("status")
                .operationType(OperationType.EQUAL)
                .values(Collections.singletonList(1))
                .build());
        if (!CollectionUtils.isEmpty(locationCodes)) {
            restrictions.add(Restriction.builder()
                    .bmfClassName(BmfClassNameConst.LOCATION)
                    .conjunction(Conjunction.AND)
                    .attributeName(BmfAttributeConst.CODE)
                    .operationType(OperationType.IN)
                    .values(locationCodes)
                    .build());
        }
        keywordCombRestriction1.setRestrictions(restrictions);
        combRestrictions.add(keywordCombRestriction1);
        if (StringUtils.isNotBlank(keyword)) {
            CombRestriction keywordCombRestriction = CombRestriction.builder().conjunction(Conjunction.AND).build();
            //模糊查询
            keywordCombRestriction.setRestrictions(Arrays.asList(
                    Restriction.builder()
                            .bmfClassName(BmfClassNameConst.LOCATION)
                            .conjunction(Conjunction.AND)
                            .attributeName(BmfAttributeConst.NAME)
                            .columnName(BmfAttributeConst.NAME)
                            .operationType(OperationType.LIKE)
                            .values(Collections.singletonList("%" + keyword + "%"))
                            .build(),
                    Restriction.builder()
                            .bmfClassName(BmfClassNameConst.LOCATION)
                            .conjunction(Conjunction.OR)
                            .attributeName(BmfAttributeConst.CODE)
                            .columnName(BmfAttributeConst.CODE)
                            .operationType(OperationType.LIKE)
                            .values(Collections.singletonList("%" + keyword + "%"))
                            .build()

            ));
            combRestrictions.add(keywordCombRestriction);
        }
        Where where = Where.builder().combRestrictions(combRestrictions).build();
        return this.bmfService.findPage(BmfClassNameConst.LOCATION, where, pageable);
    }

    private List<BmfObject> warehouse(List<Object> warehouseCodes) {
        return this.bmfService.find(BmfClassNameConst.WAREHOUSE, Arrays.asList(
                Restriction.builder()
                        .conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.STATUS)
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(1))
                        .build(),
                Restriction.builder().conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.CODE)
                        .operationType(OperationType.IN)
                        .values(warehouseCodes).
                        build()));
    }

    //根据物料编码查询启用物料
    private List<BmfObject> materials(List<Object> materialCodes) {
        return this.bmfService.find(BmfClassNameConst.MATERIAL, Arrays.asList(
                Restriction.builder()
                        .conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.STATUS)
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(1))
                        .build(),
                Restriction.builder().conjunction(Conjunction.AND)
                        .attributeName(BmfAttributeConst.CODE)
                        .operationType(OperationType.IN)
                        .values(materialCodes)
                        .build()));
    }

    private List<Object> returnIds(String string) {
        String[] stringArray = string.split(",");
        List<Object> ids = Arrays.asList(stringArray);
        return ids;
    }

    private List<Object> returnCodes(String string) {
        if (StringUtils.isEmpty(string)) {
            return null;
        }
        String[] stringArray = string.split(",");
        List<Object> codes = Arrays.asList(stringArray);
        return codes;
    }

    public Page<BmfObject> getPageLinkageQuery(String bmfClassName, Integer page, Integer size, String keyword, String areas, String warehouse, String locations) {
        if (page == null || size == null) {
            throw new BusinessException("页码和分页大小不能为空");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<BmfObject> emptyPage = new PageImpl<>(Collections.emptyList());
        //选择仓库
        if (BmfClassNameConst.WAREHOUSE.equals(bmfClassName)) {
            if (StringUtils.isNotBlank(areas)) {
                List<Object> areaCodes = returnCodes(areas);
                if (CollectionUtils.isEmpty(areaCodes)) {
                    throw new BusinessException("盘点区域主数据不存在");
                }
                List<Object> warehouseCodes = inventoryMapper.findWarehouseCodes(areaCodes);
                if (CollectionUtils.isEmpty(warehouseCodes)) {
                    return emptyPage;
                }
                return this.pageFindWarehouse(warehouseCodes, keyword, pageable);
            } else {
                return this.pageFindWarehouse(null, keyword, pageable);
            }
        }
        //选择位置
        if (BmfClassNameConst.LOCATION.equals(bmfClassName)) {
            if (StringUtils.isEmpty(warehouse) && StringUtils.isEmpty(areas)) {
                throw new BusinessException("请先选择仓库或者区域");
            }
            if (StringUtils.isNotBlank(warehouse)) {
                List<Object> warehouseCodes = returnCodes(warehouse);
                //仓库不为空
                List<Object> locationCodes = inventoryMapper.findLocationCodes(warehouseCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyPage;
                }
                return this.pageFindLocation(locationCodes, keyword, pageable);
            } else if (StringUtils.isNotBlank(areas)) {
                List<Object> areaCodes = returnCodes(areas);
                //po确定只查一层
                List<Object> locationCodes = inventoryMapper.areaFindLocation(areaCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyPage;
                }
                return this.pageFindLocation(locationCodes, keyword, pageable);
            } else if (StringUtils.isBlank(areas)) {
                //区域、仓库为空
                return this.pageFindLocation(null, keyword, pageable);
            }
        }
        if (StringUtils.isEmpty(areas) && StringUtils.isEmpty(warehouse) && StringUtils.isEmpty(locations)) {
            throw new BusinessException("盘点区域、盘点仓库、盘点位置至少有一个必填");
        }
        //选择物料
        if (BmfClassNameConst.MATERIAL.equals(bmfClassName)) {
            if (StringUtils.isNotBlank(locations)) {
                //位置不为空
                List<Object> locationCodes = returnCodes(locations);
                List<Object> materialCodes = inventoryMapper.findMaterialCodes(locationCodes);
                if (CollectionUtils.isEmpty(materialCodes)) {
                    return emptyPage;
                }
                return this.pageFindMaterials(materialCodes, keyword, pageable);
            }
            if (StringUtils.isNotBlank(warehouse)) {
                //仓库不为空
                List<Object> warehouseCodes = returnCodes(warehouse);
                //仓库关联位置编码
                List<Object> locationCodes = inventoryMapper.findLocationCodes(warehouseCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyPage;
                }
                //查询位置是否启动
                locationCodes = this.selectLocationCodes(locationCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyPage;
                }
                List<Object> materialCodes = inventoryMapper.findMaterialCodes(locationCodes);
                if (CollectionUtils.isEmpty(materialCodes)) {
                    return emptyPage;
                }
                return this.pageFindMaterials(materialCodes, keyword, pageable);
            }
            if (StringUtils.isNotBlank(areas)) {
                //区域不为空
                List<Object> areaCodes = returnCodes(areas);
                //po确定只查一层
                List<Object> locationCodes = inventoryMapper.areaFindLocation(areaCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyPage;
                }
                //查询位置是否启动
                locationCodes = this.selectLocationCodes(locationCodes);
                if (CollectionUtils.isEmpty(locationCodes)) {
                    return emptyPage;
                }
                List<Object> materialCodes = inventoryMapper.findMaterialCodes(locationCodes);
                if (CollectionUtils.isEmpty(materialCodes)) {
                    return emptyPage;
                }
                return this.pageFindMaterials(materialCodes, keyword, pageable);
            }
        }
        return emptyPage;
    }


    /**
     * 根据周转箱实时编码获取对应的台账信息
     */
    public BmfObject findLedgerByPassBoxRealCode(String realCode){
        BmfObject passBoxReal = this.bmfService.findByUnique("passBoxReal", "code", realCode);
        if (passBoxReal == null){
            throw new BusinessException("周转箱实时[" + realCode + "]信息不存在");
        }
        List<String> values = Arrays.asList("3", "4", "5", "6", "7", "8");
        String loadMaterialType = passBoxReal.getString("loadMaterialType");
        if (org.apache.commons.lang3.StringUtils.isBlank(loadMaterialType) || !values.contains(loadMaterialType)){
            throw new BusinessException("台账[" + passBoxReal.getString("passBoxCode") + "]信息不存在");
        }
        String bmfClassName = null;
        if ("3".equals(loadMaterialType)){
            bmfClassName = "knife";
        }else if ("4".equals(loadMaterialType)){
            bmfClassName = "mold";
        }else if ("5".equals(loadMaterialType)){
            bmfClassName = "fixture";
        }else if ("6".equals(loadMaterialType)){
            bmfClassName = "measuringTool";
        }else if ("7".equals(loadMaterialType)){
            bmfClassName = "jig";
        }else if ("8".equals(loadMaterialType)){
            bmfClassName = "rack";
        }
        BmfObject bmfObject = this.bmfService.findByUnique(bmfClassName, "code", passBoxReal.getString("passBoxCode"));
        if (bmfObject == null){
            throw new BusinessException("台账[" + passBoxReal.getString("passBoxCode") + "]信息不存在");
        }
        bmfObject.putUncheck("tool_type", bmfClassName);
        return bmfObject;
    }
    public String getEnumName(String code, String enumName) {
        if (StringUtils.isBlank(code)){
            return null;
        }
        BmfEnum bmfEnum = BmfEnumCache.getBmfEnum(enumName);
        return bmfEnum.getBmfEnumItemMap().get(code);
    }

}
