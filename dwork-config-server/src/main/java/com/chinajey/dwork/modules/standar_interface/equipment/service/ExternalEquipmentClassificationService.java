package com.chinajey.dwork.modules.standar_interface.equipment.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.Conjunction;
import com.chinajay.virgo.bmf.sql.OperationType;
import com.chinajay.virgo.bmf.sql.Restriction;
import com.chinajay.virgo.bmf.sql.Where;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.CodeAssUtils;
import com.chinajey.dwork.modules.standar_interface.equipment.form.ExternalEquipmentClassificationForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @description:设备类别
 * @author: ZSL
 * @date: 2025/4/18 16:00
 */
@Service
public class ExternalEquipmentClassificationService {
    private static final String BMF_CLASS = "equipmentClassification";

    @Resource
    private BmfService bmfService;

    @Resource
    BusinessUtils businessUtils;

    @Transactional
    public BmfObject saveOrUpdate(ExternalEquipmentClassificationForm form) {
        form.setExternalDocumentCode(form.getCode());
        BmfObject bmfObject = this.businessUtils.getSyncBmfObject(BMF_CLASS, form.getCode());
        if (bmfObject == null) {
            return this.groupSave(form);
        } else {
            return this.groupUpdate(form);
        }
    }


    private BmfObject groupSave(ExternalEquipmentClassificationForm form) {
        BmfObject item = findAndValidateExisting(form, false);
        JSONObject json = (JSONObject) JSONObject.toJSON(form);
        json.put("parent", item.get("parent"));
        json.put("parentCode",item.getString("parentCode"));
        json.put("parentName", item.getString("parentName"));
        json.put("sort", 1);
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(json, BMF_CLASS);
        CodeAssUtils.setCode(bmfObject, form.getCode());
        this.bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    private BmfObject groupUpdate(ExternalEquipmentClassificationForm form) {
        BmfObject item = findAndValidateExisting(form, true);
        JSONObject json = (JSONObject) JSONObject.toJSON(form);
        json.put("parent", item.get("parent"));
        json.put("parentCode",item.getString("parentCode"));
        json.put("parentName", item.getString("parentName"));
        json.put("id", item.getLong("id"));
        json.put("barCode", item.getString("barCode"));
        json.put("sort", item.getString("sort"));
        json.put("code",item.getString("code"));
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(json, BMF_CLASS);
        this.bmfService.updateByPrimaryKeySelective(bmfObject);
        return bmfObject;
    }

    private BmfObject findAndValidateExisting(ExternalEquipmentClassificationForm form, boolean isUpdate) {
        BmfObject parent = null;
        List<Restriction> restrictions = new ArrayList<>();

        BmfObject item = this.businessUtils.getSyncBmfObject(BMF_CLASS, form.getCode());
        if (!StringUtils.isBlank(form.getParent())) {
            if (Objects.equals(form.getCode(), form.getParent())) {
                throw new BusinessException("设备类别不能成为自己的父类");
            }
            parent = this.businessUtils.getSyncBmfObject(BMF_CLASS, form.getParent());
            if (parent == null) {
                throw new BusinessException("父级设备类别不存在:" + form.getParent());
            }
            restrictions.add(Restriction.builder()
                    .operationType(OperationType.EQUAL)
                    .conjunction(Conjunction.AND)
                    .attributeName("parent")
                    .values(Collections.singletonList(parent.getPrimaryKeyValue()))
                    .build());
            restrictions.add(Restriction.builder()
                    .operationType(OperationType.EQUAL)
                    .conjunction(Conjunction.AND)
                    .attributeName("name")
                    .values(Collections.singletonList(form.getName()))
                    .build());
            if (isUpdate) {
                restrictions.add(Restriction.builder()
                        .operationType(OperationType.NOT_EQUAL)
                        .conjunction(Conjunction.AND)
                        .attributeName("id")
                        .values(Collections.singletonList(item.getPrimaryKeyValue()))
                        .build());
            }
            BmfObject uniqueName = bmfService.findOne(BMF_CLASS, Where.builder().restrictions(restrictions).build());
            if (uniqueName != null) {
                throw new BusinessException("同父级[" + form.getParent() + "]下的设备类别名称不能重复:" + form.getName());
            }
        }
        if (isUpdate) {
            if (item == null) {
                throw new BusinessException("设备类别不存在");
            }

            if (!ObjectUtils.isEmpty(parent)) {
                //更新 父级不能成为自己的子级的子级
                if (parent.getAndRefreshBmfObject("parent") != null) {
                    //todo 后期验证下 businessPartnerGroup.getCode()
                    getParent(parent.getAndRefreshBmfObject("parent"),form.getCode());
                }
                item.put("parent", parent);
                item.put("parentCode", parent.getString("code"));
                item.put("parentName", parent.getString("name"));
            } else {
                item.put("parent", null);
            }
        } else {
            if (item != null) {
                throw new BusinessException("设备类别编码已存在");
            }

            item = new BmfObject("businessPartnerGroup");
            if (!ObjectUtils.isEmpty(parent)) {
                item.put("parent", parent);
                item.put("parentCode", parent.getString("code"));
                item.put("parentName", parent.getString("name"));
            } else {
                item.put("parent", null);
            }
        }
        return item;
    }

    private void getParent(BmfObject bmfObject, String code) {
        if (code.equals(bmfObject.getString("code"))) {
            throw new BusinessException("设备类别父级树中不能有自己");
        }
        BmfObject parent = bmfObject.getAndRefreshBmfObject("parent");
        if (parent != null) {
            getParent(parent,code);
        }
    }
}
