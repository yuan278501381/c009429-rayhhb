package com.chinajey.dwork.modules.standar_interface.warehouse.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.common.utils.ValueUtils;
import com.chinajey.dwork.modules.standar_interface.warehouse.form.ExternalWarehouseCategoryForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalWarehouseCategoryService {

    public static final String BMF_CLASS = "warehouseCategory";

    @Transactional
    public BmfObject saveOrUpdate(ExternalWarehouseCategoryForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        return ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject);
    }

    private JSONObject getJsonObject(ExternalWarehouseCategoryForm form) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("name", form.getName());
        jsonObject.put("status", ValueUtils.getBoolean(form.getStatus()));
        jsonObject.put("remark", form.getRemark());
        jsonObject.put("isSys", false);
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }
}
