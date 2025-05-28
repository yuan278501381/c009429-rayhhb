package com.chinajey.dwork.modules.standar_interface.warehouse.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.common.utils.ObjectRelationUtils;
import com.chinajey.dwork.common.utils.ValueUtils;
import com.chinajey.dwork.modules.standar_interface.warehouse.form.ExternalWarehouseForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class ExternalWarehouseService {

    private static final String BMF_CLASS = "warehouse";

    @Resource
    private BusinessUtils businessUtils;

    @Transactional
    public BmfObject saveOrUpdate(ExternalWarehouseForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        BmfObject bmfObject = ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject);
        ObjectRelationUtils.bind(bmfObject, ExtractUtils.commonBindRelations(form.getRelations()));
        return bmfObject;
    }

    private JSONObject getJsonObject(ExternalWarehouseForm form) {
        BmfObject warehouseCategory = this.businessUtils.getSyncBmfObject("warehouseCategory", form.getCategoryCode());
        if (warehouseCategory == null) {
            throw new BusinessException("仓库类别[" + form.getCategoryCode() + "]不存在");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("name", form.getName());
        jsonObject.put("categoryCode", warehouseCategory.getString("code"));
        jsonObject.put("category", warehouseCategory.getPrimaryKeyValue());
        if (StringUtils.isNotBlank(form.getKeeperCode())) {
            BmfObject keeper = this.businessUtils.getSyncBmfObject("user", form.getKeeperCode());
            if (keeper == null) {
                throw new BusinessException("仓管员[" + form.getKeeperCode() + "]不存在");
            }
            jsonObject.put("keeperCode", keeper.getString("code"));
            jsonObject.put("keeper", keeper.getPrimaryKeyValue());
        }
        jsonObject.put("status", ValueUtils.getBoolean(form.getStatus()));
        jsonObject.put("remark", form.getRemark());
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }
}
