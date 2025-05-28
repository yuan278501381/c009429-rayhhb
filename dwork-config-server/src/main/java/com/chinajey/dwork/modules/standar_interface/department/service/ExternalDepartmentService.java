package com.chinajey.dwork.modules.standar_interface.department.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.*;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajay.virgo.utils.BusinessUtil;
import com.chinajay.virgo.utils.SpringUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.modules.standar_interface.department.form.ExternalDepartmentForm;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * @description: 部门
 * @author: ZSL
 * @date: 2025/4/21 15:31
 */
@Service
public class ExternalDepartmentService {
    public static final String BMF_CLASS = "department";

    @Resource
    private BusinessUtils businessUtils;

    @Resource
    private BmfService bmfService;

    @Transactional
    public BmfObject saveOrUpdate(ExternalDepartmentForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        BmfObject bmfObject = commonSaveOrUpdate(BMF_CLASS, jsonObject);
        treeLevelCheck();
        return bmfObject;
    }

    public BmfObject commonSaveOrUpdate(String bmfClass, JSONObject jsonObject) {

        String externalDocumentCode = jsonObject.getString("externalDocumentCode");
        BmfObject bmfObject = bmfService.findByUnique(bmfClass, "externalDocumentCode", externalDocumentCode);
        if (bmfObject == null) {
            bmfObject = BmfUtils.genericFromJsonExt(jsonObject, bmfClass);
            bmfService.saveOrUpdate(bmfObject);
        } else {
            BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
            bmfService.updateByPrimaryKeySelective(bmfObject);
        }
        return bmfObject;
    }

    private JSONObject getJsonObject(ExternalDepartmentForm form) {
        BmfObject manager = this.businessUtils.getSyncBmfObject("user", form.getManagerCode());
        if (manager == null) {
            throw new BusinessException("部门负责人[" + form.getManagerCode() + "]不存在");
        }
        BmfObject deputyManager = this.businessUtils.getSyncBmfObject("user", form.getDeputyManagerCode());
        if (deputyManager == null) {
            throw new BusinessException("部门副负责人[" + form.getDeputyManagerCode() + "]不存在");
        }

        JSONObject jsonObject = new JSONObject();
        BmfObject parent = null;
        if (StringUtils.isNotBlank(form.getParentCode())) {
            parent = this.businessUtils.getSyncBmfObject(BMF_CLASS, form.getParentCode());
            if (parent != null) {
                jsonObject.put("parent", parent.getPrimaryKeyValue());
                getParent(parent, form.getCode());
            }
        }

        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("name", form.getName());
        jsonObject.put("phone", form.getPhone());
        jsonObject.put("facsimile", form.getFacsimile());
        setSort(parent, jsonObject);
        jsonObject.put("remark", form.getRemark());
        jsonObject.put("manager", manager.getPrimaryKeyValue());
        jsonObject.put("deputyManager", deputyManager.getPrimaryKeyValue());
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }

    private static void getParent(BmfObject bmfObject, String code) {
        if (code.equals(bmfObject.getString("externalDocumentCode"))) {
            throw new BusinessException("部门父级树中不能有自己");
        }
        BmfObject parent = bmfObject.getAndRefreshBmfObject("parent");
        if (parent != null) {
            getParent(parent, code);
        }
    }

    @Transactional
    public void treeLevelCheck() {
        // 判断部门层级是否超出限制
        List<BmfObject> treeList = this.getTree();
        int level = BusinessUtil.checkTreeLevel(treeList, 0);
        if (level > 10) {
            throw new BusinessException("部门层级不能超过10层");
        }
    }

    @Transactional
    public List<BmfObject> getTree() {
        // 根据sort排序
        Order order = BusinessUtil.getOrderBy("sort", FieldSort.ASC);
        Where where = Where.builder().order(order).restrictions(Collections.singletonList(Restriction.builder()
                .conjunction(Conjunction.AND)
                .operationType(OperationType.GRANT_EQUAL)
                .attributeName("id")
                .values(Collections.singletonList(0))
                .build())).build();
        List<BmfObject> list = bmfService.find("department", where);
        if (BusinessUtil.listIsNotEmpty(list)) {
            for (BmfObject bmfObject : list) {
                bmfObject.getAndRefreshBmfObject("manager");
                bmfObject.getAndRefreshBmfObject("deputyManager");
            }
        }
        return BusinessUtil.createTree(list);
    }

    private void setSort(BmfObject parent, JSONObject jsonObject) {
        Restriction restriction;
        // 设置sort值-开始
        if (parent != null) {
            restriction = Restriction.builder()
                    .conjunction(Conjunction.AND)
                    .operationType(OperationType.EQUAL)
                    .attributeName("parent")
                    .values(Collections.singletonList(parent.getPrimaryKeyValue()))
                    .build();
        } else {
            restriction = Restriction.builder()
                    .conjunction(Conjunction.AND)
                    .operationType(OperationType.IS_NULL)
                    .attributeName("parent")
                    .columnName("parentId")
                    .values(Collections.emptyList())
                    .build();
        }
        Order order = BusinessUtil.getOrderBy("sort", FieldSort.DESC);
        Where where = Where.builder().order(order).restrictions(Collections.singletonList(restriction)).build();
        List<BmfObject> list = this.bmfService.find(BmfClassNameConst.DEPARTMENT, where);
        if (list != null && !list.isEmpty()) {
            jsonObject.put("sort", list.get(0).getInteger("sort") != null ? list.get(0).getInteger("sort") + 1 : 1);
        } else {
            jsonObject.put("sort", 0);
        }
        // 设置sort值-结束
    }
}
