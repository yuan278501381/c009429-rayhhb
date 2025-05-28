package com.chinajey.dwork.modules.standar_interface.role.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.dwork.modules.standar_interface.role.form.ExternalRoleForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class ExternalRoleService {

    private static final String BMF_CLASS = "role";

    @Resource
    private BmfService bmfService;

    @Transactional
    public BmfObject saveOrUpdate(ExternalRoleForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        BmfObject bmfObject = this.bmfService.findByUnique(BMF_CLASS, "code", form.getCode());
        if (bmfObject == null) {
            bmfObject = BmfUtils.genericFromJsonExt(jsonObject, BMF_CLASS);
            this.bmfService.saveOrUpdate(bmfObject);
        } else {
            BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
            this.bmfService.updateByPrimaryKeySelective(bmfObject);
        }
        return bmfObject;
    }

    private JSONObject getJsonObject(ExternalRoleForm form) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", form.getCode());
        jsonObject.put("name", form.getName());
        jsonObject.put("description", form.getDescription());
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }
}
