package com.chinajey.dwork.modules.standar_interface.process_route.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.common.utils.ValueUtils;
import com.chinajey.dwork.modules.standar_interface.process_route.form.ExternalProcessRouteForm;
import com.tengnat.dwork.common.cache.CacheBusiness;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExternalProcessRouteService {

    private static final String BMF_CLASS = "processRoute";

    @Resource
    private BmfService bmfService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private BusinessUtils businessUtils;

    @Transactional
    public BmfObject saveOrUpdate(ExternalProcessRouteForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        return ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject, null, this::update);
    }

    private void update(BmfObject bmfObject, JSONObject jsonObject) {
        // 删除相关联的数据
        this.deleteProcessRouteRelationData(bmfObject);
        String code = bmfObject.getString("code");
        BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
        bmfObject.put("code", code);
        this.bmfService.saveOrUpdate(bmfObject);
    }

    private JSONObject getJsonObject(ExternalProcessRouteForm form) {
        JSONObject jsonObject = JsonUtils.simpleObjectToBmfJsonObject(form, "code", "details", "routeFiles");
        BmfObject production = this.businessUtils.getSyncBmfObject("material", form.getMaterialCode());
        if (production == null) {
            throw new BusinessException("产品[" + form.getMaterialCode() + "]不存在");
        }
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("materialCode", production.getString("code"));
        jsonObject.put("materialName", production.getString("name"));
        jsonObject.put("specifications", production.getString("specifications"));
        BmfObject packScheme = this.businessUtils.getPackSchemeBmfObject(production);
        if (packScheme != null) {
            jsonObject.put("schemeCode", packScheme.getString("code"));
            jsonObject.put("schemeName", packScheme.getString("name"));
        }
        jsonObject.put("status", ValueUtils.getBoolean(form.getStatus()));
        jsonObject.put("isDefault", ValueUtils.getBoolean(form.getIsDefault(), false));
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        if (jsonObject.getBoolean("isDefault")) {
            // 将同种物料的其他工艺路线设置为非默认
            this.jdbcTemplate.update("update dwk_process_route set is_default = 0 where material_code = ? and is_delete = 0", production.getString("code"));
        }
        this.handleRouteDetails(jsonObject, production, form.getDetails());
        this.handleRouteFiles(jsonObject, form.getRouteFiles());
        return jsonObject;
    }

    private void handleRouteDetails(JSONObject jsonObject, BmfObject production, List<ExternalProcessRouteForm.Detail> details) {
        Set<Integer> distinctProcessNos = details
                .stream()
                .map(ExternalProcessRouteForm.Detail::getProcessNo)
                .collect(Collectors.toSet());
        if (distinctProcessNos.size() != details.size()) {
            throw new BusinessException("工序号不能重复");
        }
        List<ExternalProcessRouteForm.Detail> sortedDetails = details
                .stream()
                .sorted(Comparator.comparing(ExternalProcessRouteForm.Detail::getProcessNo))
                .collect(Collectors.toList());
        List<JSONObject> routeDetails = new ArrayList<>();
        for (int i = 0; i < sortedDetails.size(); i++) {
            ExternalProcessRouteForm.Detail detail = sortedDetails.get(i);
            BmfObject workProcedure = this.businessUtils.getSyncBmfObject("workProcedure", detail.getProcessCode());
            if (workProcedure == null) {
                throw new BusinessException("工序[" + detail.getProcessCode() + "]不存在");
            }
            JSONObject routeDetail = JsonUtils.simpleObjectToBmfJsonObject(detail, "similarProcesses", "materials", "resources", "referrals", "sideProducts", "processResources", "massSettings");
            routeDetail.put("sort", i + 1);
            routeDetail.put("processCode", workProcedure.getString("code"));
            routeDetail.put("processName", workProcedure.getString("name"));
            routeDetail.put("description", workProcedure.getString("description"));
            BmfObject flowUnit = this.businessUtils.getMeasurementUnitBmfObject(detail.getFlowUnitName());
            if (flowUnit == null) {
                throw new BusinessException("流转单位[" + detail.getFlowUnitName() + "]不存在");
            }
            routeDetail.put("flowUnit", flowUnit);
            if (StringUtils.isNotBlank(detail.getWeightUnitName())) {
                BmfObject weightUnit = this.businessUtils.getMeasurementUnitBmfObject(detail.getWeightUnitName());
                if (weightUnit == null) {
                    throw new BusinessException("单重单位[" + detail.getWeightUnitName() + "]不存在");
                }
                routeDetail.put("pieceWeightUnit", weightUnit);
            }
            if (StringUtils.isNotBlank(detail.getDeliveryTargetType())) {
                if (StringUtils.isBlank(detail.getDeliveryTargetCode())) {
                    throw new BusinessException("物料具体配送目标编码不能为空");
                }
                BmfObject deliveryTarget = this.businessUtils.getSyncBmfObject(detail.getDeliveryTargetType(), detail.getDeliveryTargetCode());
                if (deliveryTarget == null) {
                    throw new BusinessException("物料具体配送目标[" + detail.getDeliveryTargetCode() + "]不存在");
                }
                routeDetail.put("deliveryTargetCode", deliveryTarget.getString("code"));
                routeDetail.put("deliveryTargetName", deliveryTarget.getString("name"));
            }
            if (StringUtils.isNotBlank(detail.getSceneCode())) {
                BmfObject produceScene = this.bmfService.findByUnique("produceScene", "code", detail.getSceneCode());
                if (produceScene == null) {
                    throw new BusinessException("生产场景[" + detail.getSceneCode() + "]不存在");
                }
                routeDetail.put("sceneCode", produceScene.getString("code"));
                routeDetail.put("sceneName", produceScene.getString("name"));
            }
            routeDetail.put("isPack", ValueUtils.getBoolean(detail.getIsPack(), false));
            routeDetail.put("prepareMaterial", ValueUtils.getBoolean(detail.getIsPack()));
            routeDetail.put("isOutsource", ValueUtils.getBoolean(detail.getIsPack(), false));
            if (i == sortedDetails.size() - 1) {
                // 末道工序的产出物就是产品
                routeDetail.put("wipCode", production.getString("code"));
                routeDetail.put("wipName", production.getString("name"));
                if (detail.getStandardCapacity() == null || detail.getStandardCapacity().compareTo(BigDecimal.ZERO) == 0) {
                    routeDetail.put("standardCapacity", BigDecimalUtils.get(production.getBigDecimal("standardCapacity")));
                }
            } else {
                routeDetail.put("wipCode", production.getString("code") + "_" + workProcedure.getString("code") + "_" + detail.getProcessNo());
                routeDetail.put("wipName", production.getString("name") + "_" + workProcedure.getString("name") + "_" + detail.getProcessNo());
            }
            routeDetail.put("thisSerialNumber", ValueUtils.getBoolean(detail.getThisSerialNumber(), false));
            routeDetail.put("thisMainSerialNumber", ValueUtils.getBoolean(detail.getThisMainSerialNumber(), false));
            // 处理工艺路线详情的关联关系
            this.handleRouteDetailRelation(routeDetail, detail);
            JsonUtils.jsonMergeExtFiled(detail.getExtFields(), routeDetail);
            routeDetails.add(routeDetail);
        }
        jsonObject.put("processRouteDetails", routeDetails);
    }

    private void handleRouteDetailRelation(JSONObject routeDetail, ExternalProcessRouteForm.Detail detail) {
        // 相似工序
        List<JSONObject> similarProcesses = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(detail.getSimilarProcesses())) {
            for (String processCode : detail.getSimilarProcesses()) {
                BmfObject workProcedure = this.businessUtils.getSyncBmfObject("workProcedure", processCode);
                if (workProcedure == null) {
                    throw new BusinessException("相似工序[" + processCode + "]不存在");
                }
                JSONObject similarProcess = new JSONObject();
                similarProcess.put("resourceCode", workProcedure.getString("code"));
                similarProcess.put("resourceName", workProcedure.getString("name"));
                similarProcesses.add(similarProcess);
            }
        }
        routeDetail.put("similarProcesses", similarProcesses);

        // 原材料信息
        List<JSONObject> materials = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(detail.getMaterials())) {
            for (ExternalProcessRouteForm.Material mForm : detail.getMaterials()) {
                BmfObject m = this.businessUtils.getSyncBmfObject("material", mForm.getMaterialCode());
                if (m == null) {
                    throw new BusinessException("原材料[" + mForm.getMaterialCode() + "]不存在");
                }
                JSONObject material = JsonUtils.simpleObjectToBmfJsonObject(mForm);
                material.put("materialCode", m.getString("code"));
                material.put("materialName", m.getString("name"));
                material.put("specifications", m.getString("specifications"));
                material.put("thisSerialNumber", ValueUtils.getBoolean(mForm.getThisSerialNumber(), false));
                material.put("thisMainSerialNumber", ValueUtils.getBoolean(mForm.getThisMainSerialNumber(), false));
                material.put("unit", m.get("flowUnit"));
                materials.add(material);
            }
        }
        routeDetail.put("processRouteMaterials", materials);

        // 可用资源
        List<JSONObject> resources = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(detail.getResources())) {
            for (ExternalProcessRouteForm.Resource rForm : detail.getResources()) {
                Long resourceId = CacheBusiness.getCacheResourceId(rForm.getResourceType());
                if (resourceId == null) {
                    throw new BusinessException("可用资源类型[" + rForm.getResourceType() + "]不存在");
                }
                BmfObject resourceBmfObject = this.businessUtils.getSyncBmfObject(rForm.getResourceType(), rForm.getResourceCode());
                if (resourceBmfObject == null) {
                    throw new BusinessException("可用资源[" + rForm.getResourceCode() + "]不存在");
                }
                JSONObject resource = new JSONObject();
                resource.put("resource", resourceId);
                resource.put("resourceCode", resourceBmfObject.getString("code"));
                resource.put("resourceName", resourceBmfObject.getString("name"));
                resources.add(resource);
            }
        }
        routeDetail.put("processRouteResources", resources);

        // 推荐人数
        List<JSONObject> referrals = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(detail.getReferrals())) {
            for (ExternalProcessRouteForm.Referral rForm : detail.getReferrals()) {
                JSONObject referral = JsonUtils.simpleObjectToBmfJsonObject(rForm);
                referrals.add(referral);
            }
        }
        routeDetail.put("referrals", referrals);

        // 副产品
        List<JSONObject> sideProducts = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(detail.getSideProducts())) {
            for (String materialCode : detail.getSideProducts()) {
                BmfObject m = this.businessUtils.getSyncBmfObject("material", materialCode);
                if (m == null) {
                    throw new BusinessException("副产品[" + materialCode + "]不存在");
                }
                JSONObject sideProduct = new JSONObject();
                sideProduct.put("materialCode", m.getString("code"));
                sideProduct.put("materialName", m.getString("name"));
                sideProducts.add(sideProduct);
            }
        }
        routeDetail.put("sideProducts", sideProducts);

        // 工艺资源 - 生产资源
        List<JSONObject> processResources = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(detail.getProcessResources())) {
            for (ExternalProcessRouteForm.ProcessResource rForm : detail.getProcessResources()) {
                BmfObject b = this.businessUtils.getSyncBmfObject(rForm.getType(), rForm.getResourceCode());
                if (b == null) {
                    throw new BusinessException("工艺资源[" + rForm.getResourceCode() + "]不存在");
                }
                JSONObject processResource = JsonUtils.simpleObjectToBmfJsonObject(rForm);
                processResource.put("resourceCode", b.getString("code"));
                processResource.put("resourceName", b.getString("name"));
                // 标准产能 = 3600 / 标准工时
                processResource.put("capacity", BigDecimalUtils.divide(BigDecimal.valueOf(3600), rForm.getWorkHour(), 3, RoundingMode.HALF_UP));
                processResources.add(processResource);
            }
        }
        routeDetail.put("processResources", processResources);

        // 量产设置
        List<JSONObject> massSettings = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(detail.getMassSettings())) {
            for (ExternalProcessRouteForm.MassSetting sForm : detail.getMassSettings()) {
                BmfObject inspectionType = this.businessUtils.getSyncBmfObject("inspectionType", sForm.getResourceCode());
                if (inspectionType == null) {
                    throw new BusinessException("检验类型[" + sForm.getResourceCode() + "]不存在");
                }
                JSONObject massSetting = new JSONObject();
                massSetting.put("resourceCode", inspectionType.getString("code"));
                massSetting.put("resourceName", inspectionType.getString("name"));
                massSetting.put("firstInspectStatus", sForm.getFirstInspectStatus());
                massSettings.add(massSetting);
            }
        }
        routeDetail.put("massSettings", massSettings);
    }

    private void handleRouteFiles(JSONObject jsonObject, List<String> routeFiles) {
        if (CollectionUtils.isEmpty(routeFiles)) {
            jsonObject.put("processRouteFiles", new ArrayList<>());
            return;
        }
        List<JSONObject> files = new ArrayList<>();
        for (String fileCode : routeFiles) {
            BmfObject fileProcess = this.businessUtils.getSyncBmfObject("fileProcess", fileCode);
            if (fileProcess == null) {
                throw new BusinessException("工艺文件[" + fileCode + "]不存在");
            }
            JSONObject file = new JSONObject();
            file.put("fileProcess", fileProcess.getPrimaryKeyValue());
            files.add(file);
        }
        jsonObject.put("processRouteFiles", files);
    }

    private void deleteProcessRouteRelationData(BmfObject bmfObject) {
        List<Long> detailIds = this.jdbcTemplate.queryForList("select id from dwk_process_route_detail where route_id = ? and is_delete = 0", Long.class, bmfObject.getPrimaryKeyValue());
        if (CollectionUtils.isEmpty(detailIds)) {
            return;
        }
        this.jdbcTemplate.update("delete from dwk_process_route_similar_process where process_route_detail_id in (" + String.join(",", Collections.nCopies(detailIds.size(), "?")) + ")", detailIds.toArray());
        this.jdbcTemplate.update("delete from dwk_process_route_material where detail_id in (" + String.join(",", Collections.nCopies(detailIds.size(), "?")) + ")", detailIds.toArray());
        this.jdbcTemplate.update("delete from dwk_process_route_resource where detail_id in (" + String.join(",", Collections.nCopies(detailIds.size(), "?")) + ")", detailIds.toArray());
        this.jdbcTemplate.update("delete from dwk_process_route_referral where process_route_detail_id in (" + String.join(",", Collections.nCopies(detailIds.size(), "?")) + ")", detailIds.toArray());
        this.jdbcTemplate.update("delete from dwk_process_route_side_product where process_route_detail_id in (" + String.join(",", Collections.nCopies(detailIds.size(), "?")) + ")", detailIds.toArray());
        this.jdbcTemplate.update("delete from dwk_process_route_process_resource where process_route_detail_id in (" + String.join(",", Collections.nCopies(detailIds.size(), "?")) + ")", detailIds.toArray());
        this.jdbcTemplate.update("delete from dwk_process_route_detail where id in (" + String.join(",", Collections.nCopies(detailIds.size(), "?")) + ")", detailIds.toArray());
    }
}
