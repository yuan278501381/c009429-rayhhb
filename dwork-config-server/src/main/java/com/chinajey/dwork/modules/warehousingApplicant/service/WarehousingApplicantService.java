package com.chinajey.dwork.modules.warehousingApplicant.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.AssistantUtils;
import com.chinajey.dwork.common.FillUtils;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.LineNumUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.modules.logistics.domain.LogisticsSubmitWrapper;
import com.tengnat.dwork.modules.logistics.service.LogisticsService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class WarehousingApplicantService {

    private static final String BMF_CLASS = "warehousingApplicant";

    public static final String DETAIL_ATTR = "warehousingApplicantIdAutoMapping";

    @Resource
    private BmfService bmfService;

    @Resource
    private CodeGenerator codeGenerator;

    @Resource
    private BusinessUtils businessUtils;

    @Resource
    private LogisticsService logisticsService;

    /**
     * 添加入库申请单
     */
    @Transactional
    public BmfObject save(JSONObject jsonObject) {
        jsonObject.remove("id");
        List<JSONObject> details = jsonObject.getJSONArray(DETAIL_ATTR).toJavaList(JSONObject.class);
        if (CollectionUtils.isEmpty(details)) {
            throw new BusinessException("入库申请单明细不能为空");
        }
        this.formatDetailQuantity(details);
        // 填充行号
        new LineNumUtils().lineNumHandle(null, details);
        ExtractUtils.validateLineNum(details, "preDocumentCode");
        BmfObject bmfObject = BmfUtils.genericFromJsonExt(jsonObject, BMF_CLASS);
        bmfObject.put("sourceSystem", Optional.ofNullable(bmfObject.getString("sourceSystem")).orElse(SourceSystemEnum.DWORK.getCode()));
        bmfObject.put("documentStatus", Optional.ofNullable(bmfObject.getString("documentStatus")).orElse(DocumentStatusEnum.UNTREATED.getCode()));
        FillUtils.fillOperator(bmfObject);
        // 来源就是自己
        this.codeGenerator.setCode(bmfObject);
        if (ObjectUtils.isEmpty(bmfObject.getString("sourceDocumentCode"))){
            bmfObject.put("sourceDocumentType", bmfObject.getBmfClassName());
            bmfObject.put("sourceDocumentCode", bmfObject.getString("code"));
        }
        this.bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    /**
     * 更新入库申请单
     */
    @Transactional
    public BmfObject update(JSONObject jsonObject) {
        if (!jsonObject.containsKey("id") || jsonObject.getLong("id") == null) {
            throw new BusinessException("入库申请单ID不能为空");
        }
        Long id = jsonObject.getLong("id");
        BmfObject bmfObject = this.bmfService.find(BMF_CLASS, id);
        if (bmfObject == null) {
            throw new BusinessException("入库申请单[" + id + "]不存在");
        }
        return ExtractUtils.commonOrderUpdate(bmfObject, jsonObject, DETAIL_ATTR, this::formatDetailQuantity);
    }

    /**
     * 下达入库申请单
     */
    @Transactional
    public void issued(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("入库申请单id不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find("warehousingApplicant", id);
            if (bmfObject == null) {
                throw new BusinessException("入库申请单不存在,id:" + id);
            }
            if (!DocumentStatusEnum.whetherIssued(bmfObject.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(bmfObject.getString("documentStatus")).getName() + "],不能下达");
            }
            bmfObject.put("documentStatus", DocumentStatusEnum.PENDING.getCode());
            this.bmfService.updateByPrimaryKeySelective(bmfObject);
            List<BmfObject> details = bmfObject.getAndRefreshList(DETAIL_ATTR);
            // 合并：物料+仓库
            Map<String, List<BmfObject>> groupDetails = details
                    .stream()
                    .collect(Collectors.groupingBy(it -> it.getString("materialCode") + AssistantUtils.SPLIT + it.getString("targetWarehouseCode")));
            for (List<BmfObject> values : groupDetails.values()) {
                BmfObject issuedData = values.get(0);
                // 总计划数量
                BigDecimal sumPlanQuantity = values
                        .stream()
                        .map(item -> item.getBigDecimal("planQuantity"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                issuedData.put("planQuantity", sumPlanQuantity);
                issuedData.getAndRefreshBmfObject("unit");
                issuedData.put("lineNum", AssistantUtils.getSplitValues(values, "lineNum"));
                issuedData.put("preDocumentCode", AssistantUtils.getSplitValues(values, "preDocumentCode"));
                issuedData.put("sourceDocumentCode", AssistantUtils.getSplitValues(values, "sourceDocumentCode"));
                issuedData.put("externalDocumentCode", AssistantUtils.getSplitValues(values, "externalDocumentCode"));
                bmfObject.put("warehousingApplicantIdAutoMapping", issuedData);
                BmfObject nodeData = this.logisticsService.assign(bmfObject, "issuedWarehousingApplicant");
                // 如果有周转箱，需要自动提交第一个节点
                this.autoSubmitFirstNode(bmfObject, values, nodeData);
            }
        }
    }

    @Transactional
    public void close(List<Long> ids) {
        this.handleWarehousingApplicants(ids, DocumentStatusEnum.CLOSED, warehousingApplicant -> {
            String documentStatus = warehousingApplicant.getString("documentStatus");
            if (!DocumentStatusEnum.whetherClose(documentStatus)) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(documentStatus).getName() + "],不能关闭");
            }
        });
    }

    @Transactional
    public void cancel(List<Long> ids) {
        this.handleWarehousingApplicants(ids, DocumentStatusEnum.CANCEL, warehousingApplicant -> {
            String documentStatus = warehousingApplicant.getString("documentStatus");
            if (!DocumentStatusEnum.whetherCancel(documentStatus)) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(documentStatus).getName() + "],不能取消");
            }
        });
    }

    @Transactional
    public void finish(List<Long> ids) {
        this.handleWarehousingApplicants(ids, DocumentStatusEnum.COMPLETED, warehousingApplicant -> {
            String documentStatus = warehousingApplicant.getString("documentStatus");
            if (!DocumentStatusEnum.whetherComplete(documentStatus)) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(documentStatus).getName() + "],不能完成");
            }
        });
    }

    public void formatDetailQuantity(List<JSONObject> details) {
        for (JSONObject detail : details) {
            detail.put("planQuantity", BigDecimalUtils.get(detail.getBigDecimal("planQuantity")));
            // 待确认
            detail.put("waitConfirmQuantity", BigDecimalUtils.get(detail.getBigDecimal("planQuantity")));
            // 待入库
            detail.put("waitQuantity", BigDecimal.ZERO);
            // 已入库
            detail.put("warehousingQuantity", BigDecimal.ZERO);
            // 已确认
            detail.put("confirmedQuantity", BigDecimal.ZERO);
        }
    }

    private void handleWarehousingApplicants(List<Long> ids, DocumentStatusEnum documentStatus, Consumer<BmfObject> consumer) {
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("入库申请单[" + id + "]不存在");
            }
            consumer.accept(bmfObject);

            bmfObject.put("documentStatus", documentStatus.getCode());
            this.bmfService.updateByPrimaryKeySelective(bmfObject);

            this.businessUtils.closeCurrentTask("GN10003", "preDocumentCode", bmfObject.getString("code"));
            this.businessUtils.closeCurrentTask("GN10004", "preDocumentCode", bmfObject.getString("code"));

            Map<String, Object> params = new HashMap<>();
            params.put("preDocumentCode", bmfObject.getString("code"));
            List<BmfObject> warehousingTasks = this.bmfService.find("warehousingTask", params);
            List<String> statusList = Arrays.asList(DocumentStatusEnum.COMPLETED.getCode(), DocumentStatusEnum.CANCEL.getCode(), DocumentStatusEnum.CLOSED.getCode());
            for (BmfObject warehousingTask : warehousingTasks) {
                if (statusList.contains(warehousingTask.getString("documentStatus"))) {
                    continue;
                }
                warehousingTask.put("documentStatus", documentStatus.getCode());
                this.bmfService.updateByPrimaryKeySelective(warehousingTask);
            }
        }
    }

    /**
     * 如果有周转箱，提交第一个节点[nodeData]的数据
     *
     * @param bmfObject 入库申请单
     * @param values    跟据物料+仓库分组后的明细
     * @param nodeData  第一个节点的数据
     */
    private void autoSubmitFirstNode(BmfObject bmfObject, List<BmfObject> values, BmfObject nodeData) {
        BmfObject firstDetail = values.get(0);
        String materialCode = firstDetail.getString("materialCode");
        String warehouseCode = firstDetail.getString("targetWarehouseCode");
        List<BmfObject> allPassBoxes = bmfObject.getAndRefreshList("warehousingApplicantPassBoxIdAutoMapping");
        List<BmfObject> passBoxes = allPassBoxes
                .stream()
                .filter(it -> StringUtils.equals(it.getString("materialCode"), materialCode) && StringUtils.equals(it.getString("warehouseCode"), warehouseCode))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(passBoxes)) {
            return;
        }
        // 构建移动应用的周转箱
        List<BmfObject> passBoxReals = new ArrayList<>();
        for (BmfObject passBox : passBoxes) {
            BmfObject passBoxReal = this.bmfService.findByUnique("passBoxReal", "code", passBox.getString("passBoxRealCode"));
            if (passBoxReal == null) {
                continue;
            }
            passBoxReal.remove("id");
            passBoxReal.setBmfClassName(nodeData.getBmfClassName() + "PassBoxes");
            passBoxReal.putUncheck("submit", true);
            BmfObject mainData = new BmfObject(nodeData.getBmfClassName());
            mainData.put("id", nodeData.getPrimaryKeyValue());
            passBoxReal.putUncheck("mainData", mainData);
            passBoxReals.add(passBoxReal);
        }
        if (CollectionUtils.isEmpty(passBoxReals)) {
            return;
        }
        nodeData.put("targetLocationCode", passBoxReals.get(0).getString("locationCode"));
        nodeData.put("targetLocationName", passBoxReals.get(0).getString("locationName"));
        nodeData.put("passBoxes", passBoxReals);
        this.logisticsService.submit(new LogisticsSubmitWrapper(nodeData.getBmfClassName(), nodeData, null, false));
    }
}
