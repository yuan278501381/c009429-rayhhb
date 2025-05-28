package com.chinajey.dwork.modules.standar_interface.businessPartner.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.form.Relation;
import com.chinajey.dwork.common.utils.*;
import com.chinajey.dwork.modules.standar_interface.businessPartner.form.ExternalBusinessPartnerForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * @description:业务伙伴主数据
 * @author: ZSL
 * @date: 2025/4/18 17:55
 */
@Service
public class ExternalBusinessPartnerService {
    private static final String BMF_CLASS = "businessPartner";

    @Resource
    private BusinessUtils businessUtils;

    @Transactional
    public BmfObject saveOrUpdate(ExternalBusinessPartnerForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        BmfObject bmfObject = ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject);
        ObjectRelationUtils.bind(bmfObject, ExtractUtils.commonBindRelations(form.getRelations()));
        return bmfObject;
    }

    private JSONObject getJsonObject(ExternalBusinessPartnerForm form) {
        BmfObject businessPartnerGroup = this.businessUtils.getSyncBmfObject("businessPartnerGroup", form.getBusinessPartnerGroupCode());
        if (businessPartnerGroup == null) {
            throw new BusinessException("业务伙伴组类别[" + form.getBusinessPartnerGroupCode() + "]不存在");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("name", form.getName());
        jsonObject.put("type", form.getType());
        jsonObject.put("status", ValueUtils.getBoolean(form.getStatus()));
        jsonObject.put("remark", form.getRemark());
        jsonObject.put("contact", form.getContact());
        jsonObject.put("contactPhone", form.getContactPhone());
        jsonObject.put("businessPartnerGroup", businessPartnerGroup.getPrimaryKeyValue());

        for (Relation relation : form.getRelations()) {
            if (!"workProcedure".equals(relation.getType())) {
                throw new BusinessException("关联资源[" + relation.getType() + "]不支持");
            }
            //判断工序是否存在
            BmfObject workProcedure = this.businessUtils.getSyncBmfObject("workProcedure", relation.getCode());
            if (workProcedure == null) {
                throw new BusinessException("工序[" + relation.getCode() + "]不存在");
            }
        }
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }
}
