package com.chinajey.dwork.modules.salesDelivery.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfArray;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.FillUtils;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.utils.LineNumUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.modules.outboundApplicant.service.OutboundApplicantService;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.common.utils.CodeGenerator;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class SalesDeliveryPlanService {

    private static final String BMF_CLASS = "salesDeliveryPlan";

    public static final String DETAIL_ATTR = "salesDeliveryPlanDetailIdAutoMapping";

    @Resource
    private BmfService bmfService;

    @Resource
    private CodeGenerator codeGenerator;

    @Resource
    private OutboundApplicantService outboundApplicantService;

    /**
     * 手动创建
     */
    @Transactional
    public BmfObject create(JSONObject jsonObject) {
        jsonObject.remove("id");
        List<JSONObject> details = jsonObject.getJSONArray(DETAIL_ATTR).toJavaList(JSONObject.class);
        if (CollectionUtils.isEmpty(details)) {
            throw new BusinessException("销售发货明细不能为空");
        }
        this.formatDetailQuantity(details);
        // 填充行号
        new LineNumUtils().lineNumHandle(null, details);
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(jsonObject, BMF_CLASS);
        bmfObject.put("documentStatus", Optional.ofNullable(bmfObject.getString("documentStatus")).orElse(DocumentStatusEnum.UNTREATED.getCode()));
        bmfObject.put("sourceSystem", Optional.ofNullable(bmfObject.getString("sourceSystem")).orElse(SourceSystemEnum.DWORK.getCode()));
        FillUtils.fillOperator(bmfObject);
        this.codeGenerator.setCode(bmfObject);
        // 来源就是自己
        bmfObject.put("sourceDocumentType", "salesDelivery");
        bmfObject.put("sourceDocumentCode", bmfObject.getString("code"));
        this.bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    /**
     * 手动修改
     */
    @Transactional
    public BmfObject update(JSONObject jsonObject) {
        if (!jsonObject.containsKey("id") || jsonObject.getLong("id") == null) {
            throw new BusinessException("销售发货计划单ID不能为空");
        }
        Long id = jsonObject.getLong("id");
        BmfObject bmfObject = this.bmfService.find(BMF_CLASS, id);
        if (bmfObject == null) {
            throw new BusinessException("销售发货计划单[" + id + "]不存在");
        }
        return ExtractUtils.commonOrderUpdate(bmfObject, jsonObject, DETAIL_ATTR, this::formatDetailQuantity);
    }

    /**
     * 下达
     */
    @Transactional
    public void issued(List<Long> ids) {
        List<BmfObject> outboundApplicants = new ArrayList<>();
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("销售发货计划单[" + id + "]不存在");
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherIssued(status)) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(status).getName() + "],不能下达");
            }
            bmfObject.getAndRefreshList(DETAIL_ATTR);
            BmfObject outboundApplicant = this.createOutboundApplicant(bmfObject);
            bmfObject.put("documentStatus", DocumentStatusEnum.PENDING.getCode());
            this.bmfService.updateByPrimaryKeySelective(bmfObject);
            outboundApplicants.add(outboundApplicant);
        }
        // 下达出库申请单
        this.outboundApplicantService.issued(outboundApplicants.stream().map(BmfObject::getPrimaryKeyValue).collect(Collectors.toList()));
    }

    public void formatDetailQuantity(List<JSONObject> details) {
        for (JSONObject detail : details) {
            // 总数量
            detail.put("quantity", BigDecimalUtils.get(detail.getBigDecimal("quantity")));
            // 待出库数量
            detail.put("waitOutboundQuantity", detail.getBigDecimal("quantity"));
            // 已出库数量
            detail.put("outboundQuantity", BigDecimal.ZERO);
            // 已发货数量
            detail.put("shippedQuantity", BigDecimal.ZERO);
            // 待发货数量
            detail.put("waitShippedQuantity", BigDecimal.ZERO);
        }
    }

    /**
     * 根据销售发货计划创建出库申请单
     *
     * @param deliveryPlan 销售发货计划单
     */
    private BmfObject createOutboundApplicant(BmfObject deliveryPlan) {
        List<BmfObject> deliveryPlanDetails = deliveryPlan.getList(DETAIL_ATTR);
        BmfObject bmfObject = new BmfObject("outboundApplicant");
        FillUtils.fillOperator(bmfObject);
        bmfObject.put("sourceOrderCode", deliveryPlan.getString("code"));
        bmfObject.put("sourceSystem", deliveryPlan.getString("sourceSystem"));
        bmfObject.put("externalDocumentType", deliveryPlan.getString("externalDocumentType"));
        bmfObject.put("externalDocumentCode", deliveryPlan.getString("externalDocumentCode"));
        bmfObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        // 源头内部单据类型
        bmfObject.put("sourceDocumentType", "salesDelivery");
        // 源头内部单据编码
        bmfObject.put("sourceDocumentCode", deliveryPlan.getString("code"));
        // 上级内部单据类型
        bmfObject.put("preDocumentType", "salesDelivery");
        // 上级内部单据编码
        bmfObject.put("preDocumentCode", deliveryPlan.getString("code"));
        // 单据业务类型
        bmfObject.put("orderBusinessType", "salesDelivery");
        BmfArray details = new BmfArray();
        for (BmfObject deliveryPlanDetail : deliveryPlanDetails) {
            BmfObject detail = new BmfObject("outboundApplicantDetail");
            detail.put("materialCode", deliveryPlanDetail.getString("materialCode"));
            detail.put("materialName", deliveryPlanDetail.getString("materialName"));
            detail.put("specifications", deliveryPlanDetail.getString("specifications"));
            detail.put("unit", deliveryPlanDetail.getBmfObject("unit"));
            detail.put("planQuantity", deliveryPlanDetail.getBigDecimal("waitOutboundQuantity"));
            detail.put("outboundQuantity", BigDecimal.ZERO);
            detail.put("waitQuantity", deliveryPlanDetail.getBigDecimal("waitOutboundQuantity"));
            detail.put("lineNum", deliveryPlanDetail.getString("lineNum"));
            detail.put("sourceWarehouseCode", deliveryPlanDetail.getString("sourceWarehouseCode"));
            detail.put("sourceWarehouseName", deliveryPlanDetail.getString("sourceWarehouseName"));
            detail.put("targetWarehouseCode", deliveryPlanDetail.getString("targetWarehouseCode"));
            detail.put("targetWarehouseName", deliveryPlanDetail.getString("targetWarehouseName"));
            details.add(detail);
        }
        bmfObject.put("outboundApplicantIdAutoMapping", details);
        this.codeGenerator.setCode(bmfObject);
        this.bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    @Transactional
    public void close(List<Long> ids) {
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("销售发货计划单[" + id + "]不存在");
            }
            if (!Arrays.asList(DocumentStatusEnum.PENDING.getCode(), DocumentStatusEnum.PARTIAL.getCode()).contains(bmfObject.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(bmfObject.getString("documentStatus")).getName() + "]，不能关闭");
            }
            bmfObject.put("documentStatus", DocumentStatusEnum.CLOSED.getCode());
            this.bmfService.updateByPrimaryKeySelective(bmfObject);
            this.handleOutboundApplicants(bmfObject.getString("code"), osIds -> this.outboundApplicantService.close(osIds));
        }
    }

    @Transactional
    public void cancel(List<Long> ids) {
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("销售发货计划单[" + id + "]不存在");
            }
            if (!DocumentStatusEnum.PENDING.getCode().equals(bmfObject.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(bmfObject.getString("documentStatus")).getName() + "]，不能取消");
            }
            bmfObject.put("documentStatus", DocumentStatusEnum.CANCEL.getCode());
            this.bmfService.updateByPrimaryKeySelective(bmfObject);
            this.handleOutboundApplicants(bmfObject.getString("code"), osIds -> this.outboundApplicantService.cancel(osIds));
        }
    }

    @Transactional
    public void finish(List<Long> ids) {
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("销售发货计划单[" + id + "]不存在");
            }
            if (!DocumentStatusEnum.PARTIAL.getCode().equals(bmfObject.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(bmfObject.getString("documentStatus")).getName() + "]，不能完成");
            }
            bmfObject.put("documentStatus", DocumentStatusEnum.COMPLETED.getCode());
            this.bmfService.updateByPrimaryKeySelective(bmfObject);
            this.handleOutboundApplicants(bmfObject.getString("code"), osIds -> this.outboundApplicantService.finish(osIds));
        }
    }

    private void handleOutboundApplicants(String deliveryPlanCode, Consumer<List<Long>> consumer) {
        Map<String, Object> params = new HashMap<>();
        params.put("preDocumentCode", deliveryPlanCode);
        List<BmfObject> outboundApplicants = this.bmfService.find("outboundApplicant", params);
        if (CollectionUtils.isEmpty(outboundApplicants)) {
            return;
        }
        List<Long> ids = outboundApplicants.stream().map(BmfObject::getPrimaryKeyValue).collect(Collectors.toList());
        consumer.accept(ids);
    }
}
