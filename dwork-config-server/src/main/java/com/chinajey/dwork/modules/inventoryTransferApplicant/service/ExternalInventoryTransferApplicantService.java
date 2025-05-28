package com.chinajey.dwork.modules.inventoryTransferApplicant.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;


/**
 * 库存转储申请单-外部同步service
 */
@Service
public class ExternalInventoryTransferApplicantService {

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;
    @Resource
    private InventoryTransferApplicantService inventoryTransferApplicantService;


    @Transactional(rollbackFor = Exception.class)
    public BmfObject saveOrUpdate(JSONObject jsonObject) {
        //校验基础数据
        checkSaveOrUpdate(jsonObject);
        //查询单据
        Map<String, Object> params = new HashMap<>(3);
        params.put("externalDocumentCode", jsonObject.getString("externalDocumentCode"));
        params.put("externalDocumentType", jsonObject.getString("orderBusinessType"));
        params.put("sourceSystem", jsonObject.getString("sourceSystem"));
        BmfObject inventoryTransferApplicant = this.businessUtils.findOrder(params, "inventoryTransferApplicant");
        if (inventoryTransferApplicant == null) {
            inventoryTransferApplicant = inventoryTransferApplicantService.save(jsonObject);
            inventoryTransferApplicantService.issued(Collections.singletonList(inventoryTransferApplicant.getPrimaryKeyValue()));
        }else {
            jsonObject.put("id", inventoryTransferApplicant.getPrimaryKeyValue());
            jsonObject.put("code", inventoryTransferApplicant.getString("code"));
            inventoryTransferApplicant = inventoryTransferApplicantService.update(jsonObject);
            inventoryTransferApplicantService.issued(Collections.singletonList(inventoryTransferApplicant.getPrimaryKeyValue()));
        }
        return inventoryTransferApplicant;
    }

    private void checkSaveOrUpdate(JSONObject jsonObject) {
        if (jsonObject == null) {
            throw new BusinessException("参数不能为空");
        }
        String code = jsonObject.getString("code");
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("来源编码不能为空");
        }

        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            throw new BusinessException("来源单据系统不能为空");
        }

        if (StringUtils.isBlank(jsonObject.getString("orderBusinessType"))) {
            throw new BusinessException(" 单据业务类型不能为空");
        }

        jsonObject.put("externalDocumentCode", code);
        jsonObject.put("externalDocumentType", jsonObject.getString("orderBusinessType"));
        jsonObject.remove("code");
        jsonObject.put("status", DocumentStatusEnum.UNTREATED.getCode());
        //校验来源仓库
        String sourceWarehouseCode = jsonObject.getString("sourceWarehouseCode");
        if (StringUtils.isBlank(sourceWarehouseCode)) {
            throw new BusinessException("来源仓库编码不能为空");
        }
        BmfObject sourceWarehouse = bmfService.findByUnique("warehouse", "code", sourceWarehouseCode);
        if (sourceWarehouse == null) {
            throw new BusinessException("未找到来源仓库主数据" + sourceWarehouseCode);
        }
        jsonObject.put("sourceWarehouseCode", sourceWarehouse.getString("code"));
        jsonObject.put("sourceWarehouseName", sourceWarehouse.getString("name"));
        //校验目标仓库
        String targetWarehouseCode = jsonObject.getString("targetWarehouseCode");
        if (StringUtils.isBlank(targetWarehouseCode)) {
            throw new BusinessException("目标仓库编码不能为空");
        }
        BmfObject targetWarehouse = bmfService.findByUnique("warehouse", "code", targetWarehouseCode);
        if (targetWarehouse == null) {
            throw new BusinessException("未找到目标仓库主数据" + targetWarehouseCode);
        }
        jsonObject.put("targetWarehouseCode", targetWarehouse.getString("code"));
        jsonObject.put("targetWarehouseName", targetWarehouse.getString("name"));


        //校验子表
        JSONArray jsonArray = jsonObject.getJSONArray("inventoryTransferApplicantDetailIdAutoMapping");
        if (CollectionUtil.isEmpty(jsonArray)) {
            throw new BusinessException("库存转储申请单明细[inventoryTransferApplicantDetailIdAutoMapping]不能为空");
        }
        List<JSONObject> details = jsonArray.toJavaList(JSONObject.class);
        for (JSONObject detail : details) {
            if (StringUtils.isBlank(detail.getString("lineNum"))) {
                throw new BusinessException("申请单明细行号不能为空");
            }
            //物料主数据
            String materialCode = detail.getString("materialCode");
            if (StringUtils.isBlank(materialCode)) {
            throw new BusinessException("明细物料编码[materialCode]不能为空");
            }
            BmfObject material = bmfService.findByUnique(BmfClassNameConst.MATERIAL, "code", materialCode);
            if (material == null) {
            throw new BusinessException("明细物料主数据[" + materialCode + "]不存在");
            }
            detail.put("materialName", material.getString("name"));
            detail.put("specifications", material.getString("specifications"));

            detail.put("unit", material.get("flowUnit"));

            BigDecimal quantity = detail.getBigDecimal("quantity");
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("明细数量[quantity]不能为空或小于等于0");
            }
            detail.put("transferQuantity", BigDecimal.ZERO);
        }
    }
}