package com.chinajey.dwork.modules.salesReturn.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.LineNumUtils;
import com.chinajey.dwork.common.utils.UpdateDataUtils;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.modules.logistics.service.LogisticsService;
import com.tengnat.dwork.modules.openapi.domain.form.BatchLoHandle;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalesReturnApplicationService {

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    @Resource
    private CodeGenerator codeGenerator;

    @Resource
    private LogisticsService logisticsService;

    private static final String GN_APP = "GN10010";

    private static final String SALES_RETURN_APPLICANT = "salesReturnApplicant";

    private static final String BUSINESS = "issuedSalesReturnApplication";


    @Transactional(rollbackFor = Exception.class)
    public BmfObject save(JSONObject jsonObject) {
        jsonObject.remove("id");
        this.check(jsonObject);
        BmfObject salesReturnApplication = BmfUtils.genericFromJson(jsonObject, SALES_RETURN_APPLICANT);
        salesReturnApplication = codeGenerator.setCode(salesReturnApplication);
        if (StringUtils.isEmpty(salesReturnApplication.getString("sourceDocumentType"))) {
            salesReturnApplication.put("sourceDocumentType", SALES_RETURN_APPLICANT);
            salesReturnApplication.put("sourceDocumentCode", salesReturnApplication.getString("code"));
        }
        bmfService.saveOrUpdate(salesReturnApplication);
        return salesReturnApplication;
    }

    @Transactional(rollbackFor = Exception.class)
    public BmfObject update(JSONObject jsonObject) {
        if (!DocumentStatusEnum.whetherUpdate(jsonObject.getString("documentStatus"))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(jsonObject.getString("documentStatus")).getName() + "],不能更新");
        }
        Long id = jsonObject.getLong("id");
        BmfObject salesReturnApplication = bmfService.find(SALES_RETURN_APPLICANT, id);
        if (salesReturnApplication == null) {
            throw new BusinessException("销售退货申请单不存在,id:" + id);
        }

        this.check(jsonObject);
        BmfUtils.bindingAttributeForEXT(salesReturnApplication, jsonObject);
        if (StringUtils.isEmpty(salesReturnApplication.getString("sourceDocumentType"))) {
            salesReturnApplication.put("sourceDocumentType", SALES_RETURN_APPLICANT);
            salesReturnApplication.put("sourceDocumentCode", salesReturnApplication.getString("code"));
        }
        bmfService.saveOrUpdate(salesReturnApplication);
        return salesReturnApplication;
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
     * 校验必填项
     *
     * @param jsonObject jsonObject
     */
    private void validateExist(JSONObject jsonObject) {
        String customerCode = jsonObject.getString("customerCode");
        if (StringUtils.isBlank(customerCode)) {
            throw new BusinessException("客户编码[customerCode]不能为空");
        }
        JSONArray salesReturnApplicantDetailIdAutoMapping = jsonObject.getJSONArray("salesReturnApplicantDetailIdAutoMapping");
        if (CollectionUtil.isEmpty(salesReturnApplicantDetailIdAutoMapping)) {
            throw new BusinessException("销售退货申请单明细不能为空");
        }
        salesReturnApplicantDetailIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            if (StringUtils.isBlank(detail.getString("materialCode"))) {
                throw new BusinessException("销售退货申请单明细物料编码[materialCode]不能为空");
            }
            if (ObjectUtils.isEmpty(detail.getBigDecimal("returnQuantity")) || detail.getBigDecimal("returnQuantity").compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("销售退货申请单明细退货数量不能为空");
            }
        });
    }

    /**
     * 校验合法性
     *
     * @param jsonObject jsonObject
     */
    private void validateLegal(JSONObject jsonObject) {
        UpdateDataUtils.updateOperateInfo(jsonObject);
        JSONArray salesReturnApplicantDetailIdAutoMapping = jsonObject.getJSONArray("salesReturnApplicantDetailIdAutoMapping");
        salesReturnApplicantDetailIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            //计划退货数量
            BigDecimal returnQuantity = ValueUtil.toBigDecimal(detail.getBigDecimal("returnQuantity"));
            //已退货数量
            BigDecimal returnedQuantity = ValueUtil.toBigDecimal(detail.getBigDecimal("receivedQuantity"));
            if (returnQuantity.compareTo(returnedQuantity) < 0) {
                throw new BusinessException("退货数量不能小于已接收数量");
            }
            detail.put("noReceivedQuantity", BigDecimalUtils.subtractResultMoreThanZero(returnQuantity, returnedQuantity));
            detail.put("receivedQuantity", returnedQuantity);
        });
    }

    private void setDefault(JSONObject jsonObject) {
        //状态-未处理
        if (StringUtils.isBlank(jsonObject.getString("documentStatus"))) {
            jsonObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        }
        //来源系统-dwork
        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            jsonObject.put("sourceSystem", SourceSystemEnum.DWORK.getCode());
        }
        //创建日期
        if (StringUtils.isBlank(jsonObject.getString("createDate"))) {
            jsonObject.put("createDate", new Date());
        }

        //退货日期
        if (StringUtils.isBlank(jsonObject.getString("returnDate"))) {
            jsonObject.put("returnDate", new Date());
        }

        // 填充行号
        if (StringUtils.equals(SourceSystemEnum.DWORK.getCode(), jsonObject.getString("sourceSystem"))) {
            List<JSONObject> details = jsonObject.getJSONArray("salesReturnApplicantDetailIdAutoMapping").toJavaList(JSONObject.class);
            new LineNumUtils().lineNumHandle(null, details);
        }

    }

    @Transactional(rollbackFor = Exception.class)
    public void issued(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("销售退货申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject salesReturnApplication = bmfService.find(SALES_RETURN_APPLICANT, id);
            if (salesReturnApplication == null) {
                throw new BusinessException("销售退货申请单不存在,ID:" + id);
            }
            issuedSalesReturnApplication(salesReturnApplication);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void issuedSalesReturnApplication(BmfObject salesReturnApplication) {
        if (!DocumentStatusEnum.whetherIssued(salesReturnApplication.getString("documentStatus"))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(salesReturnApplication.getString("status")).getName() + "],不能下达");
        }
        //下达更新单据状态-待处理
        businessUtils.updateStatus(salesReturnApplication, DocumentStatusEnum.PENDING.getCode());
        List<BmfObject> details = salesReturnApplication.getAndRefreshList("salesReturnApplicantDetailIdAutoMapping");
        if (CollectionUtil.isEmpty(details)) throw new BusinessException("退货明细不存在");

        //按照同物料+仓库信息合并下达
        Map<String, BmfObject> map = this.groupByMaterialCodeAnyWarehouseCode(details);
        List<JSONObject> issuedCollects = new ArrayList<>(map.values()).stream().map(detail -> {
            detail.getAndRefreshBmfObject("unit");
            BmfObject newSalesReturnApplication = salesReturnApplication.deepClone();
            newSalesReturnApplication.put("salesReturnApplicantDetailIdAutoMapping", Collections.singletonList(detail));
            JSONObject batchLoHandleItem = new JSONObject();
            batchLoHandleItem.put("businessCode", BUSINESS);
            batchLoHandleItem.put("jsonObject", newSalesReturnApplication);
            return batchLoHandleItem;
        }).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(issuedCollects)) {
            JSONObject batchLoHandle = new JSONObject();
            batchLoHandle.put("assigns", issuedCollects);
            logisticsService.batchHandle(batchLoHandle.toJavaObject(BatchLoHandle.class));
        }
    }

    private Map<String, BmfObject> groupByMaterialCodeAnyWarehouseCode(List<BmfObject> details) {
        //按照物料 待收货数量大于0
        Map<String, BmfObject> map = new HashMap<>(details.size());
        String unKey;
        JSONObject unValue;
        for (BmfObject item : details) {
            if (item.getBigDecimal("returnQuantity").compareTo(BigDecimal.ZERO) > 0) {
                unKey = ValueUtil.toStr(item.getString("materialCode") + "-" + item.getString("warehouseCode"));
                unValue = map.get(unKey);
                if (unValue == null) {
                    map.put(unKey, item);
                } else {
                    //待接收数量
                    BigDecimal noReceivedQuantity = BigDecimalUtils.add(unValue.getBigDecimal("noReceivedQuantity"), item.getBigDecimal("noReceivedQuantity"));
                    //已接收数量
                    BigDecimal receivedQuantity = BigDecimalUtils.add(unValue.getBigDecimal("receivedQuantity"), item.getBigDecimal("receivedQuantity"));
                    //退货数量
                    BigDecimal returnQuantity = BigDecimalUtils.add(unValue.getBigDecimal("returnQuantity"), item.getBigDecimal("returnQuantity"));
                    unValue.put("noReceivedQuantity", noReceivedQuantity);
                    unValue.put("receivedQuantity", receivedQuantity);
                    unValue.put("returnQuantity", returnQuantity);
                }
            }
        }
        return map;
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("销售退货申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject salesReturnApplication = this.bmfService.find(SALES_RETURN_APPLICANT, id);
            if (salesReturnApplication == null) {
                throw new BusinessException("销售退货申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherClose(salesReturnApplication.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(salesReturnApplication.getString("documentStatus")).getName() + "],不能关闭");
            }
            businessUtils.updateStatus(salesReturnApplication, DocumentStatusEnum.CLOSED.getCode());
            this.businessUtils.closeCurrentTask(GN_APP, "sourceDocumentCode", salesReturnApplication.getString("code"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("销售退货申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject salesReturnApplication = this.bmfService.find(SALES_RETURN_APPLICANT, id);
            if (salesReturnApplication == null) {
                throw new BusinessException("销售退货申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherCancel(salesReturnApplication.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(salesReturnApplication.getString("documentStatus")).getName() + "],不能取消");
            }
            businessUtils.updateStatus(salesReturnApplication, DocumentStatusEnum.CANCEL.getCode());
            this.businessUtils.closeCurrentTask(GN_APP, "sourceDocumentCode", salesReturnApplication.getString("code"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("销售退货申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject salesReturnApplication = this.bmfService.find(SALES_RETURN_APPLICANT, id);
            if (salesReturnApplication == null) {
                throw new BusinessException("销售退货申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherComplete(salesReturnApplication.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(salesReturnApplication.getString("documentStatus")).getName() + "],不能完成");
            }
            businessUtils.updateStatus(salesReturnApplication, DocumentStatusEnum.COMPLETED.getCode());
            this.businessUtils.closeCurrentTask(GN_APP, "sourceDocumentCode", salesReturnApplication.getString("code"));
        }
    }


}
