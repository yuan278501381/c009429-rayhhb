package com.chinajey.dwork.modules.commonInterface.service;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.*;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.annotation.Owner;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.holder.ThreadLocalHolder;
import com.chinajey.application.common.holder.UserAuthDto;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.commonInterface.domain.DomainLedger;
import com.chinajey.dwork.modules.commonInterface.from.MaintenanceApplySyncForm;
import com.tengnat.dwork.common.constant.BmfAttributeConst;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.modules.script.service.SceneGroovyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;


@Service
public class CommonInterfaceService {

    @Resource
    BmfService bmfService;

    @Resource
    CodeGenerator codeGenerator;

    @Resource
    SceneGroovyService  sceneGroovyService;



    /**
     * @param id 主表id
     * @param page 页码
     * @param size 分页
     * @param bmfClassItemName 子表业务名称
     * @param bmfClassMainId 子表关联主表attributeName
     * @return {@link List}<{@link BmfObject}>
     */
    public Object findItemPageV1(Long id, Integer page, Integer size, String bmfClassItemName,String attributeName,String keyword, String bmfClassMainId) {
        if (StringUtils.isEmpty(bmfClassItemName)){
            throw new BusinessException("子表bmfClass不能为空");
        }
        if (StringUtils.isEmpty(bmfClassMainId)){
            throw new BusinessException("子表关联主表外键不能为空");
        }
        if (page == null || size == null){
            throw new BusinessException("页码和分页大小不能为空");
        }
        if (id == null){
            throw new BusinessException("主表id不能为空");
        }
        Pageable pageable = PageRequest.of(page, size);
        //子表分页查询
        List<Restriction> restrictions = new ArrayList<>();
        restrictions.add(Restriction.builder()
                .bmfClassName(bmfClassItemName)
                .conjunction(Conjunction.AND)
                .attributeName(bmfClassMainId)
                .operationType(OperationType.EQUAL)
                .values(Collections.singletonList(id))
                .build());
        if (!StringUtils.isEmpty(keyword)){
            if (StringUtils.isEmpty(attributeName)){
                throw new BusinessException("属性名称不能为空");
            }
            //模糊查询
            restrictions.add(Restriction.builder()
                    .bmfClassName(bmfClassItemName)
                    .conjunction(Conjunction.AND)
                    .attributeName(attributeName)
                    .columnName(attributeName)
                    .operationType(OperationType.LIKE)
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
        }
        Where where = Where.builder().restrictions(restrictions).order(Order.builder()
                .sortFields(Collections.singletonList(
                        SortField.builder()
                                .bmfClassName(bmfClassItemName)
                                .bmfAttributeName(BmfAttributeConst.ID)
                                .fieldSort(FieldSort.ASC)
                                .build()
                )).build()).build();
        Page<BmfObject> pageResult = this.bmfService.findPage(bmfClassItemName, where, pageable);
        List<BmfObject> bmfObjectItems = pageResult.getContent();
        for (BmfObject bmfObjectItem : bmfObjectItems) {
            bmfObjectItem.autoRefresh();
        }
        return pageResult;
    }



    public Object findItemPage(JSONObject jsonObject) {
        String bmfClassItemName = jsonObject.getString("bmfClassItemName");
        if (StringUtils.isEmpty(bmfClassItemName)){
            throw new BusinessException("子表bmfClass不能为空");
        }
        String bmfClassMainId = jsonObject.getString("bmfClassMainId");
        if (StringUtils.isEmpty(bmfClassMainId)){
            throw new BusinessException("子表关联主表外键不能为空");
        }
        Integer page = jsonObject.getInteger("page");
        Integer size = jsonObject.getInteger("size");
        if (page == null || size == null){
            throw new BusinessException("页码和分页大小不能为空");
        }
        Long id = jsonObject.getLong("id");
        if (id == null){
            throw new BusinessException("主表id不能为空");
        }
        String keyword = jsonObject.getString("keyword");
        String attributeName = jsonObject.getString("attributeName");
        Pageable pageable = PageRequest.of(page, size);
        //子表分页查询
        List<Restriction> restrictions = new ArrayList<>();
        restrictions.add(Restriction.builder()
                .bmfClassName(bmfClassItemName)
                .conjunction(Conjunction.AND)
                .attributeName(bmfClassMainId)
                .operationType(OperationType.EQUAL)
                .values(Collections.singletonList(id))
                .build());
        if (!StringUtils.isEmpty(keyword)){
            if (StringUtils.isEmpty(attributeName)){
                throw new BusinessException("属性名称不能为空");
            }
            //模糊查询
            restrictions.add(Restriction.builder()
                    .bmfClassName(bmfClassItemName)
                    .conjunction(Conjunction.AND)
                    .attributeName(attributeName)
                    .columnName(attributeName)
                    .operationType(OperationType.LIKE)
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
        }
        List<SortField> sortFields = new ArrayList<>();
        Where where=null;
        JSONArray sortItems = jsonObject.getJSONArray("sortItems");
        if (sortItems != null && sortItems.size() > 0){
            for (int i = 0; i < sortItems.size(); i++) {
                JSONObject item = sortItems.getJSONObject(i);
                String columnName = item.getString("sortFields");
                String fieldSortStr = item.getString("sortRule");
                FieldSort fieldSort = FieldSort.valueOf(fieldSortStr.toUpperCase());
                SortField sortField = SortField.builder()
                        .bmfClassName(bmfClassItemName)
                        .bmfAttributeName(columnName)
                        .fieldSort(fieldSort)
                        .build();
                sortFields.add(sortField);
            }
            Order order = Order.builder().sortFields(sortFields).build();
            where = Where.builder().restrictions(restrictions).order(order).build();
        }else {
            where = Where.builder().restrictions(restrictions).order(Order.builder()
                    .sortFields(Collections.singletonList(
                            SortField.builder()
                                    .bmfClassName(bmfClassItemName)
                                    .bmfAttributeName(BmfAttributeConst.ID)
                                    .fieldSort(FieldSort.ASC)
                                    .build()
                    )).build()).build();
        }
        Page<BmfObject> pageResult = this.bmfService.findPage(bmfClassItemName, where, pageable);
        List<BmfObject> bmfObjectItems = pageResult.getContent();
        for (BmfObject bmfObjectItem : bmfObjectItems) {
            bmfObjectItem.autoRefresh();
        }
        return pageResult;
    }


    @Transactional(rollbackFor = Exception.class)
    @Owner
    public void push(JSONObject json) {
        UserAuthDto.LoginInfo loginInfo = ThreadLocalHolder.getLoginInfo();
        //获取当前登录人的信息
        BmfObject user = bmfService.find(BmfClassNameConst.USER, loginInfo.getLoginId());
        if (user == null) {
            throw new BusinessException("未获取到当前登录人信息");
        }
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(json, "scrapApplication");
        if (StringUtils.isEmpty(bmfObject.getString("code"))) {
            bmfObject = this.codeGenerator.setCode(bmfObject);
            bmfObject.put("status", "notSync");
            bmfObject.put("source", "3");
            bmfObject.put("mesCreateTime", new Date());
            bmfObject.put("applicantCode", user.getString("code"));
            bmfObject.put("applicantName", user.getString("name"));
            bmfObject.remove("id");
        }
        this.bmfService.saveOrUpdate(bmfObject);
        BmfObject scrapApplication = new BmfObject("scrapApplication");
        scrapApplication.put("id", bmfObject.getPrimaryKeyValue());
        scrapApplication.put("status", "inApproval");
        this.bmfService.updateByPrimaryKeySelective(scrapApplication);

    }

    @Transactional(rollbackFor = Exception.class)
    @Owner
    public void batchPush(List<Long> ids) {
        for (Long id : ids) {
            BmfObject scrapApplication = this.bmfService.find("scrapApplication", id);
            if (scrapApplication == null) {
                throw new BusinessException("报废申请不存在,id:" + id);
            }
            BmfObject bmfObject = new BmfObject("scrapApplication");
            bmfObject.put("id", id);
            bmfObject.put("status", "inApproval");
            this.bmfService.updateByPrimaryKeySelective(bmfObject);
        }
    }







    @Transactional
    public InvokeResult maintenanceApplySync(MaintenanceApplySyncForm form) {
        form.validate();
        checkAndFillMaintenanceApply(form);

        //判断新增还是更新
        String sourceCode = form.getSourceCode();
        BmfObject maintenanceApply = this.bmfService.findByUnique("maintenanceApply", "sourceCode", sourceCode);
        JSONObject json = (JSONObject) JSON.toJSON(form);
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(json, "maintenanceApply");
        if (maintenanceApply == null) {
            //新增
            this.codeGenerator.setCode(bmfObject);
            if (com.baomidou.mybatisplus.core.toolkit.StringUtils.isBlank(bmfObject.getString("code"))) {
                throw new BusinessException("维修申请单生成编码失败，请检查编码规则");
            }
            this.bmfService.saveOrUpdate(bmfObject);
            //直接下达
        } else {
            //更新
            bmfObject.put("id", maintenanceApply.getPrimaryKeyValue());
            bmfObject.put("code", maintenanceApply.getString("code"));
            bmfObject.put("barCode", maintenanceApply.getString("barCode"));
            this.bmfService.updateByPrimaryKeySelective(bmfObject);
            //更新时维修发出任务未处理的话则直接取消，重新下达
            BmfObject gn3504 = this.bmfService.findByUnique("GN3504", "dataSourceCode", bmfObject.getString("code"));
            if (gn3504 == null) {
                throw new BusinessException("维修发出任务[" + bmfObject.getString("code") + "]信息不存在");
            }
            List<String> status = Arrays.asList("1", "2");
            if (!status.contains(gn3504.getString("logisticsStatus"))) {
                throw new BusinessException("当前单据不支持提交，维修发出任务[" + bmfObject.getString("code") + "]已处理");
            }
            gn3504.put("logisticsStatus", "4");
            this.bmfService.updateByPrimaryKeySelective(gn3504);
        }
        //下达
        toGn3504(bmfObject);
        return InvokeResult.success();
    }

    private void toGn3504(BmfObject bmfObject) {
        JSONObject gn3504 = new JSONObject();
        gn3504.put("ext_maintenance_apply_code", bmfObject.getString("code"));
        BmfObject failureCause = bmfObject.getAndRefreshBmfObject("failureCause");
        gn3504.put("ext_failure_cause", failureCause.getString("code") + "-" + failureCause.getString("name"));
        gn3504.put("ext_type", bmfObject.getString("type"));
        gn3504.put("ext_factory_code", bmfObject.getString("factoryCode"));
        gn3504.put("ext_factory_name", bmfObject.getString("factoryName"));
        gn3504.put("ext_group_code", bmfObject.getString("groupCode"));
        gn3504.put("ext_group_name", bmfObject.getString("groupName"));
        gn3504.put("ext_create_time", new Date());
        gn3504.put("ext_remark", bmfObject.getString("remark"));
        gn3504.put("ext_ledger_code", bmfObject.getString("ledgerCode"));
        gn3504.put("ext_ledger_name", bmfObject.getString("ledgerName"));
        gn3504.put("ext_specifications", bmfObject.getString("specifications"));
        gn3504.put("ext_life", bmfObject.getBigDecimal("life"));
        gn3504.put("dataSourceCode", bmfObject.getString("code"));
        JSONObject gn3504Task = new JSONObject();
        BmfObject passBoxReal = this.bmfService.findByUnique("passBoxReal", "passBoxCode", bmfObject.getString("ledgerCode"));
        BmfObject material = passBoxReal.getAndRefreshBmfObject("material");
        gn3504Task.put("materialCode", material.getString("code"));  // 物料编号
        gn3504Task.put("materialName", material.getString("name")); // 物料名称
        gn3504Task.put("quantityUnit", passBoxReal.getBmfObject("quantityUnit")); // 单位
        gn3504Task.put("submit", false);// 是否提交
        gn3504.put("tasks", Collections.singletonList(gn3504Task));
        passBoxReal.remove("id");
        passBoxReal.put("ext_material_classification_name", bmfObject.getString("materialClassificationName"));
        passBoxReal.put("ext_specifications", bmfObject.getString("specifications"));
        passBoxReal.put("ext_life", bmfObject.getBigDecimal("life"));
        gn3504.put("passBoxes", Collections.singletonList(passBoxReal)); // 周转箱
        sceneGroovyService.buzSceneStart("GN3504", gn3504);
    }

    private void checkAndFillMaintenanceApply(MaintenanceApplySyncForm form) {
        //申请人
        String applicantCode = form.getApplicantCode();
        BmfObject user = this.bmfService.findByUnique("user", "code", applicantCode);
        if (user == null) {
            throw new BusinessException("申请人[" + applicantCode + "]信息不存在");
        }
        form.setApplicantName(user.getString("name"));

        BmfObject ledger = new DomainLedger().findAndValidateLedgerByPassBoxCode(form.getPassBoxCode());
        //1周转箱必须是台账数据且台账状态不等于已锁定/已报废/维修中
        List<String> rejectStatus = Arrays.asList("locked", "scrapped", "inMaintenance");
        if (rejectStatus.contains(ledger.getString("status"))) {
            throw new BusinessException("当前台账状态不符合要求");
        }
        BmfObject passBoxReal = this.bmfService.findByUnique("passBoxReal", "passBoxCode", form.getPassBoxCode());
        BmfObject material = passBoxReal.getAndRefreshBmfObject("material");
        if (material == null){
            throw new BusinessException("周转箱[" + form.getPassBoxCode() + "]物料信息不存在");
        }
        form.setLedgerCode(ledger.getString("code"));
        form.setLedgerName(ledger.getString("name"));
        form.setLife(ledger.getBigDecimal("life"));
        form.setSpecifications(material.getString("specifications"));

        /**
         * 处理台账逻辑
         * 1周转箱必须是台账数据且台账状态不等于已锁定/已报废/维修中
         * 2.更新台账 状态 = 已锁定
         * 3.台账锁定数量 +1
         */
        ledger.put("status", "locked");
        this.bmfService.updateByPrimaryKeySelective(ledger);
        new DomainLedger().updateUtensilInventoryByReal(ledger, "lockQuantity", new BigDecimal(1));

        //班组
        String groupCode = form.getGroupCode();
        if (com.baomidou.mybatisplus.core.toolkit.StringUtils.isNotBlank(groupCode)) {
            BmfObject workGroup = this.bmfService.findByUnique("workGroup", "code", groupCode);
            if (workGroup == null) {
                throw new BusinessException("班组[" + groupCode + "]信息不存在");
            }
            if (!Boolean.TRUE.equals(workGroup.getBoolean("status"))){
                throw new BusinessException("班组[" + groupCode + "]状态已禁用");
            }
            form.setGroupName(workGroup.getString("name"));
        }

        //厂家
        String factoryCode = form.getFactoryCode();
        if (com.baomidou.mybatisplus.core.toolkit.StringUtils.isNotBlank(factoryCode)) {
            BmfObject businessPartner = this.bmfService.findByUnique("businessPartner", "code", factoryCode);
            if (businessPartner == null) {
                throw new BusinessException("厂家[" + factoryCode + "]信息不存在");
            }
            //供应商类型
            if (!"supplier".equals(businessPartner.getString("type"))){
                throw new BusinessException("厂家[" + factoryCode + "]不符合供应商类型");
            }
            if (!Boolean.TRUE.equals(businessPartner.getBoolean("status"))){
                throw new BusinessException("厂家[" + factoryCode + "]状态已禁用");
            }
            form.setFactoryName(businessPartner.getString("name"));
        }

        //部门
        List<BmfObject> departments = user.getAndRefreshList("departments");
        if (!CollectionUtils.isEmpty(departments)) {
            BmfObject department = departments.get(0).getAndRefreshBmfObject("department");
            if (department != null) {
                form.setDepartment(getCostCenterByDepartment(department, department));
            }
        }

        //故障原因
        String failureCauseCode = form.getFailureCauseCode();
        BmfObject failureCause = this.bmfService.findByUnique("failureCause", "code", failureCauseCode);
        if (failureCause == null) {
            throw new BusinessException("故障原因[" + failureCauseCode + "]信息不存在");
        }
        String toolType = ledger.getString("tool_type");
        if (!failureCause.getString("toolType").equals(toolType)){
            throw new BusinessException("故障原因[" + failureCauseCode + "]器具类型与台账类型不一致");
        }
        form.setFailureCause(failureCause);
    }

    private BmfObject getCostCenterByDepartment(BmfObject preDepartment, BmfObject department) {
        if (department == null) {
            return preDepartment;
        }
        BmfObject costCenter = null;
        costCenter = bmfService.findOne("costCenter", Collections.singletonMap("name", department.getString("name")));
        if (costCenter == null) {
            return getCostCenterByDepartment(department, department.getAndRefreshBmfObject("parent"));
        }
        return department;
    }

}
