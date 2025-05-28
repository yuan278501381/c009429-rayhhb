package com.chinajey.dwork.modules.standar_interface.file_process.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.CodeAssUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.common.utils.ValueUtils;
import com.chinajey.dwork.modules.standar_interface.file_process.form.ExternalFileProcessForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
public class ExternalFileProcessService {

    private static final String BMF_CLASS = "fileProcess";

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    @Transactional
    public BmfObject saveOrUpdate(ExternalFileProcessForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        return ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject, this::save, this::update);
    }

    public BmfObject save(JSONObject jsonObject) {
        jsonObject.put("version", 1);
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(jsonObject, BMF_CLASS);
        CodeAssUtils.setCode(bmfObject, jsonObject.getString("externalDocumentCode"));
        this.bmfService.saveOrUpdate(bmfObject);
        this.saveFileProcessItem(jsonObject, bmfObject.getPrimaryKeyValue());
        return bmfObject;
    }

    public void update(BmfObject bmfObject, JSONObject jsonObject) {
        List<BmfObject> details = bmfObject.getAndRefreshList("fileProcessItems");
        BmfObject current = details
                .stream()
                .filter(it -> Boolean.TRUE.equals(it.getBoolean("current")))
                .findFirst()
                .orElse(null);
        if (current != null && !StringUtils.equals(current.getString("fileLink"), jsonObject.getString("fileLink"))) {
            // 当前版本的文件链接发生变化，才产生新版本
            details.forEach(it -> {
                it.put("current", false);
                this.bmfService.updateByPrimaryKeySelective(it);
            });
            Integer maxVersion = details
                    .stream()
                    .map(it -> it.getInteger("version"))
                    .max(Integer::compareTo)
                    .orElse(1);
            jsonObject.put("version", maxVersion + 1);
            this.saveFileProcessItem(jsonObject, bmfObject.getPrimaryKeyValue());
        }
        String code = bmfObject.getString("code");
        BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
        bmfObject.put("code", code);
        this.bmfService.updateByPrimaryKeySelective(bmfObject);
    }

    private void saveFileProcessItem(JSONObject jsonObject, long fileProcessId) {
        JSONObject detail = new JSONObject();
        detail.put("fileId", null);
        detail.put("filename", jsonObject.getString("name"));
        detail.put("fileLink", jsonObject.getString("fileLink"));
        detail.put("version", jsonObject.getInteger("version"));
        detail.put("current", true);
        detail.put("uploadTime", new Date());
        BmfObject fileProcessItem = BmfUtils.genericFromJsonExt(detail, "fileProcessItem");
        fileProcessItem.put("fileProcess", fileProcessId);
        this.bmfService.saveOrUpdate(fileProcessItem);
    }

    private JSONObject getJsonObject(ExternalFileProcessForm form) {
        BmfObject material = this.businessUtils.getSyncBmfObject("material", form.getMaterialCode());
        if (material == null) {
            throw new BusinessException("物料[" + form.getMaterialCode() + "]不存在");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("name", form.getName());
        jsonObject.put("status", ValueUtils.getBoolean(form.getStatus()));
        jsonObject.put("fileLink", form.getFileLink());
        jsonObject.put("fileSource", "externalFile");
        jsonObject.put("fileType", form.getFileType());
        jsonObject.put("remark", form.getRemark());
        jsonObject.put("materialCode", material.getString("code"));
        jsonObject.put("materialName", material.getString("name"));
        if (StringUtils.isNotBlank(form.getProcessCode())) {
            BmfObject workProcedure = this.businessUtils.getSyncBmfObject("workProcedure", form.getProcessCode());
            if (workProcedure == null) {
                throw new BusinessException("工序[" + form.getProcessCode() + "]不存在");
            }
            jsonObject.put("processCode", workProcedure.getString("code"));
            jsonObject.put("processName", workProcedure.getString("name"));
        }
        if (StringUtils.isNotBlank(form.getCostCenterCode())) {
            BmfObject costCenter = this.businessUtils.getSyncBmfObject("costCenter", form.getCostCenterCode());
            if (costCenter == null) {
                throw new BusinessException("成本中心[" + form.getProcessCode() + "]不存在");
            }
            jsonObject.put("costCenterCode", costCenter.getString("code"));
            jsonObject.put("costCenterName", costCenter.getString("name"));
        }
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }
}
