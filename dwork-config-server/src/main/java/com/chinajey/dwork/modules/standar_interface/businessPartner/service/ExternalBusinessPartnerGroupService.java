package com.chinajey.dwork.modules.standar_interface.businessPartner.service;

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
import com.chinajey.dwork.common.utils.CodeAssUtils;
import com.chinajey.dwork.modules.standar_interface.businessPartner.form.ExternalBusinessPartnerGroupForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @description:业务伙伴组主数据
 * @author: ZSL
 * @date: 2025/4/18 16:00
 */
@Service
public class ExternalBusinessPartnerGroupService {
    private static final String BMF_CLASS = "businessPartnerGroup";

    @Resource
    private BmfService bmfService;

    @Transactional
    public BmfObject saveOrUpdate(ExternalBusinessPartnerGroupForm form) {
        form.setExternalDocumentCode(form.getCode());
        BmfObject bmfObject = this.bmfService.findByUnique(BMF_CLASS, "externalDocumentCode", form.getCode());
        if (bmfObject == null) {
            return this.groupSave(form);
        } else {
            return this.groupUpdate(form);
        }
    }


    private BmfObject groupSave(ExternalBusinessPartnerGroupForm businessPartnerGroup) {
        BmfObject item = findAndValidateExisting(businessPartnerGroup, false);
        JSONObject json = (JSONObject) JSONObject.toJSON(businessPartnerGroup);
        json.put("parent", item.get("parent"));
        json.put("sort", 1);
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(json, BMF_CLASS);
        CodeAssUtils.setCode(bmfObject, businessPartnerGroup.getCode());
        this.bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    private BmfObject groupUpdate(ExternalBusinessPartnerGroupForm businessPartnerGroup) {
        BmfObject item = findAndValidateExisting(businessPartnerGroup, true);
        JSONObject json = (JSONObject) JSONObject.toJSON(businessPartnerGroup);
        json.put("parent", item.get("parent"));
        json.put("id", item.getLong("id"));
        json.put("barCode", item.getString("barCode"));
        json.put("sort", item.getString("sort"));
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(json, BMF_CLASS);
        this.bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    private BmfObject findAndValidateExisting(ExternalBusinessPartnerGroupForm businessPartnerGroup, boolean isUpdate) {
        String name = businessPartnerGroup.getName();
        BmfObject parent = null;
        List<Restriction> restrictions = new ArrayList<>();
        restrictions.add(Restriction.builder()
                .operationType(OperationType.EQUAL)
                .conjunction(Conjunction.AND)
                .attributeName("name")
                .values(Collections.singletonList(name))
                .build());
        BmfObject item = this.bmfService.findByUnique(BMF_CLASS, "externalDocumentCode", businessPartnerGroup.getCode());
        if (!StringUtils.isBlank(businessPartnerGroup.getParent())) {
            if (Objects.equals(businessPartnerGroup.getCode(), businessPartnerGroup.getParent())) {
                throw new BusinessException("业务伙伴组不能成为自己的父类");
            }
            parent = this.bmfService.findByUnique(BMF_CLASS, "externalDocumentCode", businessPartnerGroup.getParent());
            if (parent == null) {
                throw new BusinessException("父级业务伙伴组不存在:" + businessPartnerGroup.getParent());
            }
            restrictions.add(Restriction.builder()
                    .operationType(OperationType.EQUAL)
                    .conjunction(Conjunction.AND)
                    .attributeName("parent")
                    .values(Collections.singletonList(parent.getPrimaryKeyValue()))
                    .build());
            BmfObject uniqueName = bmfService.findOne(BMF_CLASS, Where.builder().restrictions(restrictions).build());
            if (uniqueName != null) {
                throw new BusinessException("同父级[" + businessPartnerGroup.getParent() + "]下的业务伙伴组名称不能重复:" + businessPartnerGroup.getName());
            }
        }
        if (isUpdate) {
            if (item == null) {
                throw new BusinessException("业务伙伴组不存在");
            }

            if (!ObjectUtils.isEmpty(parent)) {
                //更新 父级不能成为自己的子级的子级
                if (parent.getAndRefreshBmfObject("parent") != null) {
                    //todo 后期验证下 businessPartnerGroup.getCode()
                    getParent(parent.getAndRefreshBmfObject("parent"),businessPartnerGroup.getCode());
                }
                item.put("parent", parent);
            } else {
                item.put("parent", null);
            }
        } else {
            if (item != null) {
                throw new BusinessException("业务伙伴组编码已存在");
            }

            item = new BmfObject("businessPartnerGroup");
            if (!ObjectUtils.isEmpty(parent)) {
                item.put("parent", parent);
            } else {
                item.put("parent", null);
            }
        }
        return item;
    }

    private void getParent(BmfObject bmfObject, String code) {
        if (code.equals(bmfObject.getString("code"))) {
            throw new BusinessException("业务伙伴组父级树中不能有自己");
        }
        BmfObject parent = bmfObject.getAndRefreshBmfObject("parent");
        if (parent != null) {
            getParent(parent,code);
        }
    }
}
