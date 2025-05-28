package com.chinajey.dwork.modules.purchaseReturn.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.holder.UserAuthDto;
import com.chinajey.application.common.holder.UserHolder;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author erton.bi
 */
@Service
public class PurchaseReturnApplicationService {

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    @Resource
    private CodeGenerator codeGenerator;

    @Resource
    private LogisticsService logisticsService;

    private static final String BUSINESS = "issuedPurchaseReturnApplication";

    private static final String GN_APP = "GN10006";


    @Transactional(rollbackFor = Exception.class)
    public BmfObject save(JSONObject jsonObject) {
        jsonObject.remove("id");
        this.check(jsonObject);
        BmfObject purchaseReturnApplication = BmfUtils.genericFromJson(jsonObject, "purchaseReturnApplication");
        purchaseReturnApplication = codeGenerator.setCode(purchaseReturnApplication);
        bmfService.saveOrUpdate(purchaseReturnApplication);
        return purchaseReturnApplication;
    }

    @Transactional(rollbackFor = Exception.class)
    public BmfObject update(JSONObject jsonObject) {
        if (!DocumentStatusEnum.whetherUpdate(jsonObject.getString("documentStatus"))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(jsonObject.getString("documentStatus")).getName() + "],不能更新");
        }
        Long id = jsonObject.getLong("id");
        BmfObject purchaseReturnApplication = bmfService.find("purchaseReturnApplication", id);
        if (purchaseReturnApplication == null) {
            throw new BusinessException("采购退货申请单不存在,id:" + id);
        }

        this.check(jsonObject);
        BmfUtils.bindingAttributeForEXT(purchaseReturnApplication, jsonObject);
        //更新状态为 未处理
        purchaseReturnApplication.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        bmfService.saveOrUpdate(purchaseReturnApplication);
        return purchaseReturnApplication;
    }


    private void check(JSONObject jsonObject) {
        //校验必填项
        validateExist(jsonObject);
        //校验合法性
        validateLegal(jsonObject);
    }

    /**
     * 校验必填项
     *
     * @param jsonObject jsonObject
     */
    private void validateExist(JSONObject jsonObject) {
        JSONArray purchaseReturnApplicationIdAutoMapping = jsonObject.getJSONArray("purchaseReturnApplicationIdAutoMapping");
        if (CollectionUtil.isEmpty(purchaseReturnApplicationIdAutoMapping)) {
            throw new BusinessException("采购退货申请单不能为空");
        }
        purchaseReturnApplicationIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            if (StringUtils.isBlank(detail.getString("lineNum"))) {
                throw new BusinessException("采购退货申请单明细行号不能为空");
            }
            if (ObjectUtils.isEmpty(detail.getBigDecimal("planReturnQuantity"))) {
                throw new BusinessException("采购退货申请单明细计划退货数量不能为空");
            }
        });
    }

    /**
     * 校验合法性
     *
     * @param jsonObject jsonObject
     */
    private void validateLegal(JSONObject jsonObject) {
        if (StringUtils.isBlank(jsonObject.getString("operatorCode"))) {
            //填充退货人
            UserAuthDto.Resource loginUser = UserHolder.getLoginUser();
            if (loginUser != null) {
                jsonObject.put("operatorCode", loginUser.getResourceCode());
                jsonObject.put("operatorName", loginUser.getResourceName());
            }
        }
        JSONArray purchaseReturnApplicationIdAutoMapping = jsonObject.getJSONArray("purchaseReturnApplicationIdAutoMapping");
        purchaseReturnApplicationIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            //计划退货数量
            BigDecimal planReturnQuantity = ValueUtil.toBigDecimal(detail.getBigDecimal("planReturnQuantity"));
            //已退货数量
            BigDecimal returnedQuantity = ValueUtil.toBigDecimal(detail.getBigDecimal("returnedQuantity"));
            if (planReturnQuantity.compareTo(returnedQuantity) < 0) {
                throw new BusinessException("计划退货数量不能小于已退货数量");
            }
            detail.put("waitReturnQuantity", BigDecimalUtils.subtractResultMoreThanZero(planReturnQuantity, returnedQuantity));
            detail.put("returnedQuantity", returnedQuantity);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void issued(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购退货申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject purchaseReturnApplication = bmfService.find("purchaseReturnApplication", id);
            if (purchaseReturnApplication == null) {
                throw new BusinessException("出采购退货申请单不存在,ID:" + id);
            }
            issuedPurchaseReturnApplication(purchaseReturnApplication);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void issuedPurchaseReturnApplication(BmfObject purchaseReturnApplication) {
        if (!DocumentStatusEnum.whetherIssued(purchaseReturnApplication.getString("documentStatus"))) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(purchaseReturnApplication.getString("documentStatus")).getName() + "],不能下达");
        }
        //下达更新单据状态-待处理
        businessUtils.updateStatus(purchaseReturnApplication, DocumentStatusEnum.PENDING.getCode());

        //下达之前先关闭任务
        this.businessUtils.closeCurrentTask(GN_APP, "preDocumentCode", purchaseReturnApplication.getString("code"));

        //按照同物料+仓库信息合并下达
        List<BmfObject> details = purchaseReturnApplication.getAndRefreshList("purchaseReturnApplicationIdAutoMapping");
        Map<String, BmfObject> map = groupByMaterialCodeAndWarehouseCode(details);
        List<JSONObject> issuedCollects = new ArrayList<>(map.values()).stream().map(detail -> {
            //根据主表维度下达
            BmfObject issuedBmfObject = purchaseReturnApplication.deepClone();
            detail.getAndRefreshBmfObject("unit");
            issuedBmfObject.put("purchaseReturnApplicationIdAutoMapping",detail);
            //组装下达数据
            JSONObject batchLoHandleItem = new JSONObject();
            batchLoHandleItem.put("businessCode", BUSINESS);
            batchLoHandleItem.put("jsonObject", issuedBmfObject);
            return batchLoHandleItem;
        }).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(issuedCollects)) {
            JSONObject batchLoHandle = new JSONObject();
            batchLoHandle.put("assigns", issuedCollects);
            logisticsService.batchHandle(batchLoHandle.toJavaObject(BatchLoHandle.class));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购退货申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject purchaseReturnApplication = this.bmfService.find("purchaseReturnApplication", id);
            if (purchaseReturnApplication == null) {
                throw new BusinessException("采购退货申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherClose(purchaseReturnApplication.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(purchaseReturnApplication.getString("documentStatus")).getName() + "],不能关闭");
            }
            businessUtils.updateStatus(purchaseReturnApplication, DocumentStatusEnum.CLOSED.getCode());
            this.businessUtils.closeCurrentTask(GN_APP, "preDocumentCode", purchaseReturnApplication.getString("code"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购退货申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject purchaseReturnApplication = this.bmfService.find("purchaseReturnApplication", id);
            if (purchaseReturnApplication == null) {
                throw new BusinessException("采购退货申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherCancel(purchaseReturnApplication.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(purchaseReturnApplication.getString("documentStatus")).getName() + "],不能取消");
            }
            businessUtils.updateStatus(purchaseReturnApplication, DocumentStatusEnum.CANCEL.getCode());
            this.businessUtils.closeCurrentTask(GN_APP, "preDocumentCode", purchaseReturnApplication.getString("code"));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购退货申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject purchaseReturnApplication = this.bmfService.find("purchaseReturnApplication", id);
            if (purchaseReturnApplication == null) {
                throw new BusinessException("采购退货申请单不存在,ID:" + id);
            }
            if (!DocumentStatusEnum.whetherComplete(purchaseReturnApplication.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(purchaseReturnApplication.getString("documentStatus")).getName() + "],不能完成");
            }
            businessUtils.updateStatus(purchaseReturnApplication, DocumentStatusEnum.COMPLETED.getCode());
            this.businessUtils.closeCurrentTask(GN_APP, "preDocumentCode", purchaseReturnApplication.getString("code"));
        }
    }

    private Map<String, BmfObject> groupByMaterialCodeAndWarehouseCode(List<BmfObject> details) {
        //按照物料 待退货数量大于0
        Map<String, BmfObject> map = new HashMap<>(details.size());
        String unKey;
        JSONObject unValue;
        for (BmfObject item : details) {
            if (item.getBigDecimal("waitReturnQuantity").compareTo(BigDecimal.ZERO) > 0) {
                unKey = ValueUtil.toStr(item.getString("materialCode")) + ValueUtil.toStr(item.getString("sourceWarehouseCode"));
                unValue = map.get(unKey);
                if (unValue == null) {
                    map.put(unKey, item);
                } else {
                    //计划退货数量
                    BigDecimal planReturnQuantity = BigDecimalUtils.add(unValue.getBigDecimal("planReturnQuantity"), item.getBigDecimal("planReturnQuantity"));
                    //已退货数量
                    BigDecimal returnedQuantity = BigDecimalUtils.add(unValue.getBigDecimal("returnedQuantity"), item.getBigDecimal("returnedQuantity"));
                    //待退货数量
                    BigDecimal waitReturnQuantity = BigDecimalUtils.add(unValue.getBigDecimal("waitReturnQuantity"), item.getBigDecimal("waitReturnQuantity"));
                    unValue.put("planReturnQuantity", planReturnQuantity);
                    unValue.put("returnedQuantity", returnedQuantity);
                    unValue.put("waitReturnQuantity", waitReturnQuantity);
                }
            }
        }
        return map;
    }
}