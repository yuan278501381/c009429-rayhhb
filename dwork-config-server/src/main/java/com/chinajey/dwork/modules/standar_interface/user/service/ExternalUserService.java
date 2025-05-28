package com.chinajey.dwork.modules.standar_interface.user.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.AssistantUtils;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.common.utils.ObjectRelationUtils;
import com.chinajey.dwork.common.utils.ValueUtils;
import com.chinajey.dwork.modules.standar_interface.user.form.ExternalUserForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

@Service
public class ExternalUserService {

    private static final String BMF_CLASS = "user";

    @Resource
    private BusinessUtils businessUtils;

    @Transactional
    public BmfObject saveOrUpdate(ExternalUserForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        BmfObject bmfObject = ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject);
        ExtractUtils.saveOrUpdateMiddleTable("userRole", form.getRoles(), bmfObject.getPrimaryKeyValue(), "user", "role");
        ExtractUtils.saveOrUpdateMiddleTable("departmentUser", form.getDepartments(), bmfObject.getPrimaryKeyValue(), "user", "department");
        ObjectRelationUtils.bind(bmfObject, ExtractUtils.commonBindRelations(form.getRelations(), Collections.singletonList("workProcedure")));
        return bmfObject;
    }

    private JSONObject getJsonObject(ExternalUserForm form) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("name", form.getName());
        jsonObject.put("mobile", form.getMobile());
        jsonObject.put("jobNumber", form.getJobNumber());
        jsonObject.put("gender", form.getGender());
        jsonObject.put("onSeat", ValueUtils.getBoolean(form.getOnSeat()));
        jsonObject.put("status", ValueUtils.getBoolean(form.getStatus()) ? "ENABLE" : "DISABLE");
        String managerCode = form.getManagerCode();
        if (StringUtils.isNotBlank(managerCode)) {
            BmfObject manager = this.businessUtils.getSyncBmfObject("user", managerCode);
            if (manager == null) {
                throw new BusinessException("直接主管[" + managerCode + "]不存在");
            }
            jsonObject.put("directManager", manager.getPrimaryKeyValue());
        }
        jsonObject.put("post", AssistantUtils.getSplitStrValues(form.getPosts(), ","));
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }
}
