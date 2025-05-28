package com.chinajey.dwork.modules.standar_interface.product_order.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.modules.productionStockV2.service.ProductionStockV2Service;
import com.chinajey.dwork.modules.standar_interface.product_order.form.ExternalProductOrderForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import com.tengnat.dwork.modules.manufacturev2.enums.BoxOrderStatusEnum;
import com.tengnat.dwork.modules.manufacturev2.enums.TaskItemStatusEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExternalProductOrderService {

    private static final String BMF_CLASS = "productOrder";

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    @Resource
    private ProductionStockV2Service productionStockV2Service;

    @Transactional
    public BmfObject saveOrUpdate(ExternalProductOrderForm form) {
        JSONObject jsonObject = this.getJsonObject(form);
        String status = jsonObject.getString("status");
        if (StringUtils.equals(status, "cancel")) {
            return this.cancel(jsonObject);
        }
        return ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject, null, this::update);
    }

    private void update(BmfObject bmfObject, JSONObject jsonObject) {
        this.validateForUpdate(bmfObject);
        String code = bmfObject.getString("code");
        BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
        bmfObject.put("code", code);
        this.bmfService.saveOrUpdate(bmfObject);
    }

    private BmfObject cancel(JSONObject jsonObject) {
        BmfObject productOrder = bmfService.findByUnique(BMF_CLASS, "externalDocumentCode", jsonObject.getString("externalDocumentCode"));
        if (productOrder == null) {
            throw new BusinessException("生产订单不存在，外部编码：" + jsonObject.getString("externalDocumentCode"));
        }
        List<BmfObject> boxOrders = this.validateForUpdate(productOrder);
        // 取消生产订单
        productOrder.put("status", "cancel");
        this.bmfService.updateByPrimaryKeySelective(productOrder);
        // 取消工序计划单
        Map<String, Object> planParams = new HashMap<>();
        planParams.put("productOrderCode", productOrder.getString("code"));
        List<BmfObject> processPlans = this.bmfService.find("processPlan", planParams);
        for (BmfObject processPlan : processPlans) {
            processPlan.put("status", "5");
            this.bmfService.updateByPrimaryKeySelective(processPlan);
        }
        // 取消生产箱单
        for (BmfObject boxOrder : boxOrders) {
            boxOrder.put("status", BoxOrderStatusEnum.Cancel.getValue());
            this.bmfService.updateByPrimaryKeySelective(boxOrder);
            Map<String, Object> taskParams = new HashMap<>();
            taskParams.put("boxOrderCode", boxOrder.getString("code"));
            List<BmfObject> produceTasks = this.bmfService.find("boxOrderProcessProduceTask", taskParams);
            for (BmfObject produceTask : produceTasks) {
                List<BmfObject> taskItemBmfObjects = produceTask.getAndRefreshList("taskItems");
                for (BmfObject taskItemBmfObject : taskItemBmfObjects) {
                    taskItemBmfObject.put("status", TaskItemStatusEnum.Cancel.getValue());
                    this.bmfService.updateByPrimaryKeySelective(taskItemBmfObject);
                }
            }
            // 取消生产备料单
            Map<String, Object> stockParams = new HashMap<>();
            stockParams.put("boxOrderCode", boxOrder.getString("code"));
            List<BmfObject> productionStocks = this.bmfService.find("productionStock", stockParams);
            if (CollectionUtils.isNotEmpty(productionStocks)) {
                this.productionStockV2Service.cancel(productionStocks.stream().map(BmfObject::getPrimaryKeyValue).collect(Collectors.toList()));
            }
        }
        return productOrder;
    }

    private List<BmfObject> validateForUpdate(BmfObject productOrder) {
        List<String> statusList = Arrays.asList("closed", "cancel", "done");
        if (statusList.contains(productOrder.getString("status"))) {
            throw new BusinessException("生产订单状态无法编辑");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("productOrderCode", productOrder.getString("code"));
        List<BmfObject> boxOrders = this.bmfService.find("boxOrder", params);
        for (BmfObject boxOrder : boxOrders) {
            // 除了：未发布、未下达、已取消，其他状态不允许修改
            List<String> boxOrderStatus = Arrays.asList(
                    BoxOrderStatusEnum.NotPublish.getValue(),
                    BoxOrderStatusEnum.NotIssued.getValue(),
                    BoxOrderStatusEnum.Cancel.getValue()
            );
            if (!boxOrderStatus.contains(boxOrder.getString("status"))) {
                throw new BusinessException("该生产订单已生成箱单且为不能修改或取消状态");
            }
        }
        return boxOrders;
    }

    private JSONObject getJsonObject(ExternalProductOrderForm form) {
        BmfObject production = this.businessUtils.getSyncBmfObject("material", form.getProductionCode());
        if (production == null) {
            throw new BusinessException("产品[" + form.getProductionCode() + "]不存在");
        }
        BmfObject processRoute = this.businessUtils.getSyncBmfObject("processRoute", form.getProcessRouteCode());
        if (processRoute == null) {
            throw new BusinessException("工艺路线[" + form.getProcessRouteCode() + "]不存在");
        }
        BmfObject inWarehouse = this.businessUtils.getSyncBmfObject("warehouse", form.getWarehouseCode());
        if (inWarehouse == null) {
            throw new BusinessException("入库仓库[" + form.getWarehouseCode() + "]不存在");
        }
        if (StringUtils.isNotBlank(form.getMainProductOrderCode())) {
            BmfObject mainProductOrder = this.businessUtils.getSyncBmfObject(BMF_CLASS, form.getMainProductOrderCode());
            if (mainProductOrder == null) {
                throw new BusinessException("主订单[" + form.getMainProductOrderCode() + "]不存在");
            }
        }
        if (!StringUtils.equals(processRoute.getString("materialCode"), form.getProductionCode())) {
            throw new BusinessException("产品[" + form.getProductionCode() + "]与工艺路线[" + form.getProcessRouteCode() + "]不匹配");
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("sourceSys", form.getSourceSystem());
        jsonObject.put("externalDocumentType", form.getExternalDocumentType());
        jsonObject.put("externalDocumentCode", form.getCode());
        jsonObject.put("sourceCode", form.getCode());
        jsonObject.put("sourceName", "sapSynchronous");
        jsonObject.put("sourceOrder", form.getCode());
        jsonObject.put("type", form.getType());
        String status = form.getStatus();
        jsonObject.put("status", StringUtils.equals(status, "cancel") ? status : "unconfirmed");
        jsonObject.put("mainProductOrderCode", form.getMainProductOrderCode());
        jsonObject.put("salesOrderCode", form.getSalesOrderCode());
        jsonObject.put("salesOrderLineNum", form.getSalesOrderLineNum());
        jsonObject.put("priority", form.getPriority());
        jsonObject.put("priorityOrder", form.getPriority());
        jsonObject.put("productionCode", production.getString("code"));
        jsonObject.put("productionName", production.getString("name"));
        jsonObject.put("specifications", production.getString("specifications"));
        jsonObject.put("planQuantity", form.getPlanQuantity());
        jsonObject.put("planQuantityUnit", production.getBmfObject("flowUnit"));
        jsonObject.put("planStartTime", form.getPlanStartTime());
        jsonObject.put("planEndTime", form.getPlanEndTime());
        jsonObject.put("createDate", new Date());
        jsonObject.put("inboundWarehouseCode", inWarehouse.getString("code"));
        jsonObject.put("inboundWarehouseName", inWarehouse.getString("name"));
        BmfObject packScheme = this.businessUtils.getPackSchemeBmfObject(production);
        if (packScheme != null) {
            jsonObject.put("schemeCode", packScheme.getString("code"));
            jsonObject.put("schemeName", packScheme.getString("name"));
        }
        jsonObject.put("processRoute", processRoute.getPrimaryKeyValue());
        jsonObject.put("processRouteCode", processRoute.getString("code"));
        jsonObject.put("processRouteName", processRoute.getString("name"));
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);

        List<BmfObject> routeDetails = processRoute.getAndRefreshList("processRouteDetails");

        List<JSONObject> materials = new ArrayList<>();
        if(CollectionUtils.isNotEmpty(form.getMaterials())) {
            for (ExternalProductOrderForm.Material m : form.getMaterials()) {
                BmfObject routeDetail = routeDetails
                        .stream()
                        .filter(it -> Objects.equals(it.getInteger("processNo"), m.getProcessNo()))
                        .findFirst()
                        .orElse(null);
                if (routeDetail == null) {
                    throw new BusinessException("工艺路线[" + form.getProcessRouteCode() + "]不存在工序号：" + m.getProcessNo());
                }
                BmfObject mo = this.businessUtils.getSyncBmfObject("material", m.getMaterialCode());
                if (mo == null) {
                    throw new BusinessException("物料[" + m.getMaterialCode() + "]不存在");
                }
                BmfObject outWarehouse = this.businessUtils.getSyncBmfObject("warehouse", m.getWarehouseCode());
                if (outWarehouse == null) {
                    throw new BusinessException("发出仓库[" + form.getWarehouseCode() + "]不存在");
                }
                JSONObject material = new JSONObject();
                material.put("lineNum", m.getLineNum());
                material.put("materialCode", mo.getString("code"));
                material.put("materialName", mo.getString("name"));
                material.put("specifications", mo.getString("specifications"));
                material.put("basicUsage", m.getBasicUsage());
                material.put("lossRate", m.getLossRate());
                material.put("planQuantity", m.getPlanQuantity());
                material.put("warehouseCode", outWarehouse.getString("code"));
                material.put("warehouseName", outWarehouse.getString("name"));
                material.put("processNo", routeDetail.getInteger("processNo"));
                material.put("processCode", routeDetail.getString("processCode"));
                material.put("processName", routeDetail.getString("processName"));
                JsonUtils.jsonMergeExtFiled(m.getExtFields(), material);
                materials.add(material);
            }
        }
        // 校验行号
        ExtractUtils.validateLineNum(materials, "materialCode");
        jsonObject.put("productOrderAutoMapping", materials);
        return jsonObject;
    }
}
