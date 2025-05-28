package com.chinajey.dwork.modules.standar_interface.material.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.modules.standar_interface.material.form.ExternalMaterialClassificationForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Objects;

@Service
public class ExternalMaterialClassificationService {

    public static final String BMF_CLASS = "materialClassification";

    @Resource
    private BusinessUtils businessUtils;

    @Transactional
    public BmfObject saveOrUpdate(ExternalMaterialClassificationForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        return ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject);
    }

    private JSONObject getJsonObject(ExternalMaterialClassificationForm form) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("externalDocumentCode", form.getCode());
        if(StringUtils.isNotBlank(form.getParent())) {
            if (Objects.equals(form.getCode(), form.getParent())) {
                throw new BusinessException("物料类别不能成为自己的父类");
            }
            BmfObject parent = businessUtils.getSyncBmfObject(BMF_CLASS, form.getParent());
            if(parent == null) {
                throw new BusinessException("父级物料类别不存在");
            }
            jsonObject.put("parent", parent.getPrimaryKeyValue());
        } else {
            jsonObject.put("parent", null);
        }
        jsonObject.put("name", form.getName());
        jsonObject.put("sort", 1);
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }
}
