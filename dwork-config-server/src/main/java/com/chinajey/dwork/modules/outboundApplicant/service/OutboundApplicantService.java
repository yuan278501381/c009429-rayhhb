package com.chinajey.dwork.modules.outboundApplicant.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.Conjunction;
import com.chinajay.virgo.bmf.sql.OperationType;
import com.chinajay.virgo.bmf.sql.Restriction;
import com.chinajay.virgo.bmf.sql.Where;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.FillUtils;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.LineNumUtils;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.modules.logistics.service.LogisticsService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OutboundApplicantService {
    @Resource
    private BmfService bmfService;

    @Resource
    private CodeGenerator codeGenerator;

    @Resource
    private LogisticsService logisticsService;

    @Resource
    private BusinessUtils businessUtils;

    /**
     * 添加出库申请单
     *
     * @param jsonObject
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public BmfObject save(JSONObject jsonObject) {
        jsonObject.remove("id");
        List<JSONObject> details = jsonObject.getJSONArray("outboundApplicantIdAutoMapping").toJavaList(JSONObject.class);
        if (CollectionUtils.isEmpty(details)) {
            throw new BusinessException("出库申请单明细不能为空");
        }
        new LineNumUtils().lineNumHandle(null, details);
        details.forEach(item -> item.remove("id"));
        this.check(jsonObject);
        BmfObject outboundApplicant = BmfUtils.genericFromJson(jsonObject, "outboundApplicant");
        FillUtils.fillOperator(outboundApplicant);
        codeGenerator.setCode(outboundApplicant);
        if (StringUtils.isBlank(outboundApplicant.getString("sourceDocumentType"))) {
            outboundApplicant.put("sourceDocumentType", outboundApplicant.getBmfClassName());
            outboundApplicant.put("sourceDocumentCode", outboundApplicant.getString("code"));
        }
        bmfService.saveOrUpdate(outboundApplicant);
        issued(Collections.singletonList(outboundApplicant.getPrimaryKeyValue()));
        return outboundApplicant;
    }
    private void check(JSONObject jsonObject) {
        //校验必填项
        validateExist(jsonObject);
        //校验合法性
        validateLegal(jsonObject);
        //默认值
        setDefault(jsonObject);
    }


    /**
     * 更新出库申请单
     *
     * @param jsonObject
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public BmfObject update(JSONObject jsonObject) {
        if (jsonObject.getLong("id") == null) {
            throw new BusinessException("出库申请单id不存在");
        }
        Long id = jsonObject.getLong("id");
        BmfObject bmfObject = this.bmfService.find("outboundApplicant", id);
        if (bmfObject == null) {
            throw new BusinessException("出库申请单不存在,id:" + id);
        }
        List<BmfObject> oldDetails = bmfObject.getAndRefreshList("outboundApplicantIdAutoMapping");

        if (StringUtils.equals(SourceSystemEnum.DWORK.getCode(), jsonObject.getString("sourceSystem"))) {
            List<JSONObject> details = jsonObject.getJSONArray("outboundApplicantIdAutoMapping").toJavaList(JSONObject.class);
            new LineNumUtils().lineNumHandle(oldDetails, details);
        }
        jsonObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        this.check(jsonObject);
        //状态判断
        String status = bmfObject.getString("documentStatus");
        if (!(Arrays.asList(DocumentStatusEnum.CANCEL.getCode(), DocumentStatusEnum.UNTREATED.getCode()).contains(bmfObject.getString("documentStatus")) && SourceSystemEnum.DWORK.getCode().equals(bmfObject.getString("sourceSystem")))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(status).getName() + "],不能修改");
        }
        BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
        this.bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }


    /**
     * 下达出库申请单
     *
     * @param ids
     */
    @Transactional(rollbackFor = Exception.class)
    public void issued(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("出库申请单id不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find("outboundApplicant", id);
            if (bmfObject == null) {
                throw new BusinessException("出库申请单不存在,id:" + id);
            }
            if (!DocumentStatusEnum.UNTREATED.getCode().equals(bmfObject.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(bmfObject.getString("documentStatus")).getName() + "],不能下达");
            }
            //更新转态
            BmfObject update = new BmfObject("outboundApplicant");
            update.put("id", id);
            update.put("documentStatus", DocumentStatusEnum.PENDING.getCode());
            bmfService.updateByPrimaryKeySelective(update);
            //明细
            List<BmfObject> details = bmfObject.getAndRefreshList("outboundApplicantIdAutoMapping");
            //根据物料出库仓合并
            Map<String, List<BmfObject>> groupDetails = details.stream()
                    .collect(Collectors.groupingBy(object -> object.getString("materialCode") + "-" + object.getString("sourceWarehouseCode")));
            for (List<BmfObject> values : groupDetails.values()) {
                //计划出库数量
                BigDecimal sumPlanQuantity = values.stream().map(item -> item.getBigDecimal("planQuantity")).reduce(BigDecimal.ZERO, BigDecimal::add);
                //已出库数量
                BigDecimal sumOutboundQuantity = values.stream().map(item -> item.getBigDecimal("outboundQuantity")).reduce(BigDecimal.ZERO, BigDecimal::add);
                //下达的数据
                BmfObject issuedData = values.get(0);
                issuedData.put("planQuantity", sumPlanQuantity);
                issuedData.put("outboundQuantity", sumOutboundQuantity);
                issuedData.getAndRefreshBmfObject("unit");
                bmfObject.put("outboundApplicantIdAutoMapping", issuedData);
                logisticsService.assign(bmfObject, "issuedOutboundApplicant");
            }
        }
    }

    /**
     * 校验必填项
     *
     * @param jsonObject jsonObject
     */
    private void validateLegal(JSONObject jsonObject) {
        if (StringUtils.isBlank(jsonObject.getString("orderBusinessType"))) {
            throw new BusinessException("业务类型不能为空");
        }
    }

    /**
     * 校验合法性
     *
     * @param jsonObject jsonObject
     */
    private void validateExist(JSONObject jsonObject) {
        JSONArray outboundApplicantIdAutoMapping = jsonObject.getJSONArray("outboundApplicantIdAutoMapping");
        if (CollectionUtil.isEmpty(outboundApplicantIdAutoMapping)) {
            throw new BusinessException("出库申请单明细不能为空");
        }
        outboundApplicantIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            if (ObjectUtils.isEmpty(detail.getBigDecimal("planQuantity"))) {
                throw new BusinessException("出库申请单明细计划出库数量不能为空");
            }
            detail.put("waitQuantity", detail.getString("planQuantity"));
            detail.put("outboundQuantity", BigDecimal.ZERO);
            String materialCode = detail.getString("materialCode");
            if (StringUtils.isBlank(materialCode)) {
                throw new BusinessException("出库申请单明细物料编码不能为空");
            }
            BmfObject material = bmfService.findByUnique("material", "code", materialCode);
            if (material == null) {
                throw new BusinessException("出库申请单明细未找到物料主数据" + materialCode);
            }
        });
    }

    /**
     * 设置默认值
     *
     * @param jsonObject jsonObject
     */

    private void setDefault(JSONObject jsonObject) {
        if (StringUtils.isBlank(jsonObject.getString("documentStatus"))) {
            jsonObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        }
        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            jsonObject.put("sourceSystem", SourceSystemEnum.DWORK.getCode());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(List<Long> ids) {
        process(ids, DocumentStatusEnum.PENDING.getCode(), DocumentStatusEnum.PARTIAL.getCode(), DocumentStatusEnum.CLOSED.getCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(List<Long> ids) {
        process(ids, DocumentStatusEnum.PENDING.getCode(), null, DocumentStatusEnum.CANCEL.getCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(List<Long> ids) {
        process(ids, DocumentStatusEnum.PARTIAL.getCode(), null, DocumentStatusEnum.COMPLETED.getCode());
    }

    private void process(List<Long> ids, String allowedStatus1, String allowedStatus2, String targetStatus) {
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("参数缺失或为空[ids]");
        }
        List<Object> idsObj = new ArrayList<>(ids);
        // 批量查询所有对象
        List<BmfObject> bmfObjects = bmfService.find("outboundApplicant", Where.builder().restrictions(
                Collections.singletonList(
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .attributeName("id")
                                .operationType(OperationType.IN)
                                .values(idsObj)
                                .build()
                )
        ).build());
        Map<Long, BmfObject> idToObjectMap = bmfObjects.stream()
                .collect(Collectors.toMap(bmfObject -> bmfObject.getLong("id"), bmfObject -> bmfObject));

        for (Long id : ids) {
            BmfObject bmfObject = idToObjectMap.get(id);
            if (bmfObject == null) {
                throw new BusinessException("出库申请单不存在,id:" + id);
            }

            String documentStatus = bmfObject.getString("documentStatus");
            if (!(allowedStatus1.equals(documentStatus) || (allowedStatus2 != null && allowedStatus2.equals(documentStatus)))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(documentStatus).getName() + "],不能执行此操作");
            }

            // 更新状态
            BmfObject update = new BmfObject("outboundApplicant");
            update.put("id", id);
            update.put("documentStatus", targetStatus);
            bmfService.updateByPrimaryKeySelective(update);

            // 关闭业务
            this.businessUtils.closeCurrentTask("GN10001", "preDocumentCode", bmfObject.getString("code"));

            // 模糊查询
            List<BmfObject> list = bmfService.find("GN10002", Where.builder().restrictions(Arrays.asList(
                    Restriction.builder()
                            .bmfClassName("GN10002")
                            .conjunction(Conjunction.AND)
                            .attributeName("preDocumentCode")
                            .operationType(OperationType.LIKE)
                            .values(Collections.singletonList(bmfObject.getString("code")))
                            .build(),
                    Restriction.builder()
                            .bmfClassName("GN10002")
                            .conjunction(Conjunction.AND)
                            .attributeName("logisticsStatus")
                            .operationType(OperationType.IN)
                            .values(Arrays.asList("1", "2"))
                            .build()
            )).build());

            if (CollectionUtils.isNotEmpty(list)) {
                List<BmfObject> collect = list.stream().filter(l -> !l.getString("preDocumentCode").equals(bmfObject.getString("code"))).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(collect)) {
                    throw new BusinessException("任务单已合并无法完成");
                }else {
                    this.businessUtils.closeCurrentTask("GN10002","preDocumentCode", bmfObject.getString("code"));
                }
            }
        }
    }
}
