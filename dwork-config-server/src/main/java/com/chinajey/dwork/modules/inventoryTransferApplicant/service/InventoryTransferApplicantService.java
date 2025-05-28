package com.chinajey.dwork.modules.inventoryTransferApplicant.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.LineNumUtils;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.modules.logistics.service.LogisticsService;
import com.tengnat.dwork.modules.script.service.SceneGroovyService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存转储申请单服务
 */
@Service
public class InventoryTransferApplicantService {

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    @Resource
    private CodeGenerator codeGenerator;

    @Resource
    private SceneGroovyService sceneGroovyService;

    @Resource
    private LogisticsService logisticsService;

    private static final String GN_APP = "GN10011";


    @Transactional(rollbackFor = Exception.class)
    public BmfObject save(JSONObject jsonObject) {
        jsonObject.remove("id");
        this.check(jsonObject);
        List<JSONObject> details = jsonObject.getJSONArray("inventoryTransferApplicantDetailIdAutoMapping").toJavaList(JSONObject.class);
        if (StringUtils.equals(SourceSystemEnum.DWORK.getCode(), jsonObject.getString("sourceSystem"))) {
            new LineNumUtils().lineNumHandle(null, details);
        }
        BmfObject inventoryTransferApplicant = BmfUtils.genericFromJson(jsonObject, "inventoryTransferApplicant");
        inventoryTransferApplicant = codeGenerator.setCode(inventoryTransferApplicant);
        bmfService.saveOrUpdate(inventoryTransferApplicant);
        return inventoryTransferApplicant;
    }

    @Transactional(rollbackFor = Exception.class)
    public BmfObject update(JSONObject jsonObject) {
        Long id = jsonObject.getLong("id");
        if (jsonObject.getLong("id") == null) {
            throw new BusinessException("库存转储申请单id不存在");
        }
        BmfObject inventoryTransferApplicant = bmfService.find("inventoryTransferApplicant", id);
        if (inventoryTransferApplicant == null) {
            throw new BusinessException("库存转储申请单不存在,id:" + id);
        }
        List<BmfObject> oldDetails = inventoryTransferApplicant.getAndRefreshList("inventoryTransferApplicantDetailIdAutoMapping");

        if (!DocumentStatusEnum.whetherUpdate(inventoryTransferApplicant.getString("documentStatus"))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(inventoryTransferApplicant.getString("documentStatus")).getName() + "],不能更新");
        }
        if (StringUtils.equals(SourceSystemEnum.DWORK.getCode(), jsonObject.getString("sourceSystem"))) {
            List<JSONObject> details = jsonObject.getJSONArray("inventoryTransferApplicantDetailIdAutoMapping").toJavaList(JSONObject.class);
            new LineNumUtils().lineNumHandle(oldDetails, details);
        }
        this.check(jsonObject);
        BmfUtils.bindingAttributeForEXT(inventoryTransferApplicant, jsonObject);
        //更新状态为 未处理
        inventoryTransferApplicant.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        bmfService.saveOrUpdate(inventoryTransferApplicant);
        return inventoryTransferApplicant;
    }


    private void check(JSONObject jsonObject) {
        if (StringUtils.isBlank(jsonObject.getString("documentStatus"))) {
            jsonObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        }
        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            jsonObject.put("sourceSystem", SourceSystemEnum.DWORK.getCode());
        }

        JSONArray jsonArray = jsonObject.getJSONArray("inventoryTransferApplicantDetailIdAutoMapping");
        if (CollectionUtil.isEmpty(jsonArray)) {
            throw new BusinessException("库存转储申请单明细[inventoryTransferApplicantDetailIdAutoMapping]不能为空");
        }
        jsonArray.toJavaList(JSONObject.class).forEach(detail -> {
            BigDecimal quantity = detail.getBigDecimal("quantity");
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("明细数量[quantity]不能为空或小于等于0");
            }
            detail.put("transferQuantity", BigDecimal.ZERO);
            String materialCode = detail.getString("materialCode");
            if (StringUtils.isBlank(materialCode)) {
                throw new BusinessException("出库申请单明细物料编码不能为空");
            }
            BmfObject material = bmfService.findByUnique(BmfClassNameConst.MATERIAL, "code", materialCode);
            if (material == null) {
                throw new BusinessException("明细物料主数据[" + materialCode + "]不存在");
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void issued(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("库存转储申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject inventoryTransferApplicant = bmfService.find("inventoryTransferApplicant", id);
            if (inventoryTransferApplicant == null) {
                throw new BusinessException("出库存转储申请单不存在,ID:" + id);
            }
            issuedInventoryTransferApplicant(inventoryTransferApplicant);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void issuedInventoryTransferApplicant(BmfObject inventoryTransferApplicant) {

        if (!DocumentStatusEnum.whetherIssued(inventoryTransferApplicant.getString("documentStatus"))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(inventoryTransferApplicant.getString("documentStatus")).getName() + "],不能下达");
        }
        //下达更新单据状态-待处理
        businessUtils.updateStatus(inventoryTransferApplicant, DocumentStatusEnum.PENDING.getCode());
        List<BmfObject> details = inventoryTransferApplicant.getAndRefreshList("inventoryTransferApplicantDetailIdAutoMapping");
        for (BmfObject detail : details) {
            inventoryTransferApplicant.put("inventoryTransferApplicantDetailIdAutoMapping", Collections.singletonList(detail));
            logisticsService.assign(inventoryTransferApplicant, "issuedInventoryTransferApplicant");
        }
        /*for (List<BmfObject> values : groupDetails.values()) {
            BigDecimal sumQuantity = values.stream().map(item -> item.getBigDecimal("quantity")).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sumTransferQuantity = values.stream().map(item -> item.getBigDecimal("transferQuantity")).reduce(BigDecimal.ZERO, BigDecimal::add);
            BmfObject detail = values.get(0);
            BmfObject task = new BmfObject("GN10011Tasks");
            BmfObject material = bmfService.findByUnique(BmfClassNameConst.MATERIAL, "code", detail.getString("materialCode"));
            task.put("materialCode", detail.getString("materialCode"));
            task.put("materialName", detail.getString("materialName"));
            task.put("material", material);
            task.put("quantityUnit", material.get("flowUnit"));
            task.put("ext_wait_outbound_count", sumQuantity.subtract(sumTransferQuantity));
            tasks.add(task);
        }
        String materialCodes = String.join(",", groupDetails.keySet());
        String materialNames = inventoryTransferApplicant.getAndRefreshList("inventoryTransferApplicantDetailIdAutoMapping").stream()
                .map(object -> object.getString("materialName"))
                .distinct()
                .collect(Collectors.joining(","));

        BmfObject gn10011 = new BmfObject(GN_APP);
        gn10011.put("dataSourceCode", inventoryTransferApplicant.getString("code"));
        gn10011.put("ext_transfer_order_code", inventoryTransferApplicant.getString("code"));
        gn10011.put("preDocumentCode", inventoryTransferApplicant.getString("code"));
        gn10011.put("preDocumentType", inventoryTransferApplicant.getBmfClassName());
        if (inventoryTransferApplicant.getString("sourceDocumentType") == null) {
            gn10011.put("sourceDocumentType", inventoryTransferApplicant.getString("preDocumentCode"));
            gn10011.put("sourceDocumentCode", inventoryTransferApplicant.getString("preDocumentType"));
        }
        gn10011.put("ext_source_warehouse_name", inventoryTransferApplicant.getString("sourceWarehouseName"));
        gn10011.put("ext_source_warehouse_code", inventoryTransferApplicant.getString("sourceWarehouseCode"));
        gn10011.put("ext_target_warehouse_name", inventoryTransferApplicant.getString("targetWarehouseName"));
        gn10011.put("ext_material_code", materialCodes);
        gn10011.put("ext_material_name", materialNames);
        gn10011.put("ext_target_warehouse_code", inventoryTransferApplicant.getString("targetWarehouseCode"));
        gn10011.put("tasks", tasks);
        sceneGroovyService.buzSceneStart(GN_APP, gn10011);*/
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("库存转储申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject inventoryTransferApplicant = this.bmfService.find("inventoryTransferApplicant", id);
            if (inventoryTransferApplicant == null) {
                throw new BusinessException("库存转储申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherClose(inventoryTransferApplicant.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(inventoryTransferApplicant.getString("documentStatus")).getName() + "],不能关闭");
            }
            businessUtils.updateStatus(inventoryTransferApplicant, DocumentStatusEnum.CLOSED.getCode());
            this.businessUtils.closeCurrentTaskByDataSourceCode(GN_APP, inventoryTransferApplicant.getString("code"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("库存转储申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject inventoryTransferApplicant = this.bmfService.find("inventoryTransferApplicant", id);
            if (inventoryTransferApplicant == null) {
                throw new BusinessException("库存转储申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherCancel(inventoryTransferApplicant.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(inventoryTransferApplicant.getString("documentStatus")).getName() + "],不能取消");
            }
            businessUtils.updateStatus(inventoryTransferApplicant, DocumentStatusEnum.CANCEL.getCode());
            this.businessUtils.closeCurrentTaskByDataSourceCode(GN_APP, inventoryTransferApplicant.getString("code"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("库存转储申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject inventoryTransferApplicant = this.bmfService.find("inventoryTransferApplicant", id);
            if (inventoryTransferApplicant == null) {
                throw new BusinessException("库存转储申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherComplete(inventoryTransferApplicant.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(inventoryTransferApplicant.getString("documentStatus")).getName() + "],不能完成");
            }
            businessUtils.updateStatus(inventoryTransferApplicant, DocumentStatusEnum.COMPLETED.getCode());
            this.businessUtils.closeCurrentTaskByDataSourceCode(GN_APP, inventoryTransferApplicant.getString("code"));
        }
    }
}