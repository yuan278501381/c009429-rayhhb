package com.chinajey.dwork.modules.purchaseOrder.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.FillUtils;
import com.chinajey.dwork.common.enums.DocumentConfirmStatusEnum;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.utils.LineNumUtils;
import com.chinajey.dwork.modules.purchaseReceipt.service.PurchaseReceiptService;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.common.utils.LogisticsUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {
    @Resource
    private BmfService bmfService;
    @Resource
    private CodeGenerator codeGenerator;
    @Resource
    private BusinessUtils businessUtils;
    @Resource
    private PurchaseReceiptService purchaseReceiptService;

    private static final String BMF_CLASS = "purchaseOrder";
    private static final String GN_APP = "GN10005";

    /**
     * 添加采购订单
     *
     * @param jsonObject 订单信息
     * @return 订单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public BmfObject save(JSONObject jsonObject) {
        jsonObject.remove("id");
        this.check(jsonObject);
        BmfObject bmfObject = BmfUtils.genericFromJson(jsonObject, BMF_CLASS);
        FillUtils.fillOperator(bmfObject);
        codeGenerator.setCode(bmfObject);
        // 来源就是自己
        bmfObject.put("sourceDocumentType", BMF_CLASS);
        bmfObject.put("sourceDocumentCode", bmfObject.getString("code"));
        bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    /**
     * 更新采购订单
     *
     * @param jsonObject 订单信息
     * @return 订单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public BmfObject update(JSONObject jsonObject, Boolean isInternal) {
        Long id = jsonObject.getLong("id");
        BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
        if (bmfObject == null) {
            throw new BusinessException("采购订单不存在,id:" + id);
        }
        String status = bmfObject.getString("documentStatus");
        if (!DocumentStatusEnum.whetherUpdate(status)) {
            throw new BusinessException("采购订单状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能更新");
        }
        if (isInternal) {
            if (!SourceSystemEnum.DWORK.getCode().equals(bmfObject.getString("sourceSystem"))) {
                throw new BusinessException("外部单据不能编辑");
            }
        }
        this.check(jsonObject);
        BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
        //更新状态为 未处理
        bmfObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        //更新确认状态 未确认
        bmfObject.put("confirmStatus", DocumentConfirmStatusEnum.UNCONFIRMED.getCode());
        FillUtils.fillOperator(bmfObject);
        bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    @Transactional(rollbackFor = Exception.class)
    public BmfObject update(JSONObject jsonObject) {
        return this.update(jsonObject, true);
    }

    /**
     * 关闭采购订单
     *
     * @param ids 采购订单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购订单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("采购订单不存在,id:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherClose(status)) {
                throw new BusinessException("采购订单状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能关闭");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.CLOSED.getCode());

            this.updatePurchaseReceiptStatus(bmfObject, DocumentStatusEnum.CLOSED.getCode());
        }
    }

    /**
     * 取消采购订单
     *
     * @param ids 采购订单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购订单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("采购订单不存在,id:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.PENDING.getCode().equals(status)) {
                throw new BusinessException("采购订单状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能取消");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.CANCEL.getCode());

            this.updatePurchaseReceiptStatus(bmfObject, DocumentStatusEnum.CANCEL.getCode());
        }
    }

    /**
     * 完成采购订单
     *
     * @param ids 采购订单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void finish(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购订单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("采购收货计划不存在,id:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherComplete(status)) {
                throw new BusinessException("采购订单状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能完成");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.COMPLETED.getCode());

            this.updatePurchaseReceiptStatus(bmfObject, DocumentStatusEnum.COMPLETED.getCode());
        }
    }

    /**
     * 更新采购收货计划/采购收货任务状态
     *
     * @param bmfObject 采购订单
     * @param status    状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchaseReceiptStatus(BmfObject bmfObject, String status) {
        //根据采购订单编码查询采购收货计划列表
        String code = bmfObject.getString("code");
        Map<String, Object> params = new HashMap<>(2);
        params.put("sourceDocumentCode", code);
        List<BmfObject> purchaseReceipts = bmfService.find("purchaseReceipt", params);
        purchaseReceipts.forEach(detail -> {
            //取消PC采购收货计划
            businessUtils.updateStatus(detail, status);
            //取消PDA采购收货任务
            businessUtils.closeCurrentTask(GN_APP, "preDocumentCode", detail.getString("code"));
        });
    }


    /**
     * 获取下达明细，只返回可计划数量大于0的行
     */
    public BmfObject releaseList(Long id) {
        BmfObject bmfObject = bmfService.getBmfObject(BMF_CLASS, id);
        if (bmfObject == null) {
            throw new BusinessException("采购订单不存在,id:" + id);
        }
        bmfObject.autoRefresh();
        List<BmfObject> purchaseOrderRelease = bmfObject.getAndRefreshList("purchaseOrderReleaseIdAutoMapping");
        BmfUtils.batchRefreshAttribute(purchaseOrderRelease, "unit");
        List<BmfObject> filteredRelease = purchaseOrderRelease.stream()
                .filter(item -> {
                    BigDecimal waitPlanQty = item.getBigDecimal("waitPlanQuantity");
                    return waitPlanQty != null && waitPlanQty.compareTo(BigDecimal.ZERO) > 0;
                })
                .collect(Collectors.toList());
        bmfObject.put("purchaseOrderReleaseIdAutoMapping", filteredRelease);
        return bmfObject;
    }

    /**
     * 下达-创建采购收货计划(PC)
     *
     * @param jsonObject jsonObject
     */
    @Transactional(rollbackFor = Exception.class)
    public void issued(JSONObject jsonObject) {
        Long id = jsonObject.getLong("id");
        BmfObject purchaseOrder = bmfService.find(BMF_CLASS, id);
        if (purchaseOrder == null) {
            throw new BusinessException("采购订单不存在,id:" + id);
        }
        purchaseOrder.autoRefresh();
        // 未确认和部分确认可以确认
        String confirmStatus = purchaseOrder.getString("confirmStatus");
        if (!DocumentConfirmStatusEnum.whetherConfirm(confirmStatus)) {
            throw new BusinessException("单据确认状态为[" + DocumentConfirmStatusEnum.getEnum(confirmStatus).getName() + "],不能确认");
        }
        // 未处理、待处理和部分处理可以确认
        String status = purchaseOrder.getString("documentStatus");
        boolean canConfirm = Arrays.asList(DocumentStatusEnum.UNTREATED.getCode(), DocumentStatusEnum.PENDING.getCode(), DocumentStatusEnum.PARTIAL.getCode()).contains(status);
        if (!canConfirm) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能确认");
        }

        //构建采购收货计划jsonObject
        BmfObject purchaseReceiptJSON = new BmfObject();
        //采购订单编码
        String code = purchaseOrder.getString("code");

        //----------采购收货计划主表-------------
        //状态-未处理
        purchaseReceiptJSON.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        //来源系统
        purchaseReceiptJSON.put("sourceSystem", purchaseOrder.getString("sourceSystem"));
        //设置单据数据
        LogisticsUtils.setDocumentData(purchaseOrder, purchaseReceiptJSON);
        //供应商
        purchaseReceiptJSON.put("providerCode", purchaseOrder.getString("providerCode"));
        purchaseReceiptJSON.put("providerName", purchaseOrder.getString("providerName"));
        //采购员
        purchaseReceiptJSON.put("buyerCode", purchaseOrder.getString("buyerCode"));
        purchaseReceiptJSON.put("buyerName", purchaseOrder.getString("buyerName"));

        //----------采购收货计划明细表-------------
        //selectedRows需要前端塞值
        JSONArray purchaseReceiptIdAutoMapping = jsonObject.getJSONArray("selectedRows");
        purchaseReceiptIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            detail.remove("id");
            //填写的计划收货数量
            BigDecimal planReceiveQuantity = ValueUtil.toBigDecimal(detail.getString("planReceiveQuantity"));
            //旧的可计划数量
            BigDecimal waitPlanQuantity = detail.getBigDecimal("waitPlanQuantity");
            if (planReceiveQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("计划收货数量不能为空或小于等于0");
            }
            if (planReceiveQuantity.compareTo(waitPlanQuantity) > 0) {
                throw new BusinessException("计划收货数量不能大于可计划数量");
            }
        });
        purchaseReceiptJSON.put("purchaseReceiptIdAutoMapping", purchaseReceiptIdAutoMapping);

        //新增采购收货计划
        purchaseReceiptService.save(purchaseReceiptJSON);
        //更新采购订单下达明细
        this.updateReleaseDetail(code, purchaseReceiptIdAutoMapping);
        //未处理状态-更新采购订单状态为待处理
        if (DocumentStatusEnum.UNTREATED.getCode().equals(purchaseOrder.getString("documentStatus"))) {
            businessUtils.updateStatus(purchaseOrder, DocumentStatusEnum.PENDING.getCode());
        }
        //更新采购订单确认状态
        this.updateConfirmStatus(id);

    }

    /**
     * 根据可下达明细行数量更新确认状态
     *
     * @param id 采购订单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfirmStatus(Long id) {
        BmfObject bmfObject = this.releaseList(id);
        List<BmfObject> purchaseOrderRelease = bmfObject.getList("purchaseOrderReleaseIdAutoMapping");
        // 下达-部分确认
        String confirmStatus = DocumentConfirmStatusEnum.PARTIAL_CONFIRMED.getCode();
        if (purchaseOrderRelease.isEmpty()) {
            //全部下达-已确认
            confirmStatus = DocumentConfirmStatusEnum.CONFIRMED.getCode();
        }
        bmfObject.put("confirmStatus", confirmStatus);
        bmfService.updateByPrimaryKeySelective(bmfObject);
    }

    /**
     * 根据明细行的待入库数量更新单据状态
     *
     * @param code       单据编码
     * @param bmfClass   单据类名
     * @param detailAttr 明细表属性名
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String code, String bmfClass, String detailAttr) {
        BmfObject bmfObject = bmfService.findByUnique(bmfClass, "code", code);
        if (bmfObject == null) {
            throw new BusinessException("单据不存在,单据编码:" + code);
        }
        bmfObject.autoRefresh();

        //原来的单据状态
        String oldStatus = bmfObject.getString("documentStatus");
        //如果原来的单据状态为已完成、已关闭，直接返回
        if (Arrays.asList(DocumentStatusEnum.COMPLETED.getCode(), DocumentStatusEnum.CLOSED.getCode()).contains(oldStatus)) {
            return;
        }

        //获取明细行
        List<BmfObject> detailIdAutoMapping = bmfObject.getAndRefreshList(detailAttr);

        String documentStatus = DocumentStatusEnum.PARTIAL.getCode();
        //待入库为0 且 待收货为0
        boolean allMatch = detailIdAutoMapping.stream().allMatch(item -> item.getBigDecimal("waitReceivedQuantity").compareTo(BigDecimal.ZERO) == 0
                && item.getBigDecimal("waitWarehousedQuantity").compareTo(BigDecimal.ZERO) == 0);
        if (allMatch) {
            documentStatus = DocumentStatusEnum.COMPLETED.getCode();
        }
        bmfObject.put("documentStatus", documentStatus);

        bmfService.updateByPrimaryKeySelective(bmfObject);

    }

    /**
     * 更新采购订单下达明细
     *
     * @param code         采购订单编码
     * @param selectedRows 下达行
     */
    private void updateReleaseDetail(String code, JSONArray selectedRows) {
        BmfObject purchaseOrder = bmfService.findByUnique(BMF_CLASS, "code", code);
        if (purchaseOrder == null) {
            throw new BusinessException("采购订单不存在,订单编码:" + code);
        }
        purchaseOrder.autoRefresh();
        //采购订单下达明细
        List<BmfObject> purchaseOrderReleaseIdAutoMapping = purchaseOrder.getAndRefreshList("purchaseOrderReleaseIdAutoMapping");
        BmfUtils.batchRefreshAttribute(purchaseOrderReleaseIdAutoMapping, "unit");

        selectedRows.toJavaList(JSONObject.class).forEach(detail -> {
            //填写的计划收货数量
            BigDecimal planReceiveQuantity = ValueUtil.toBigDecimal(detail.getString("planReceiveQuantity"));
            //通过物料编码和行号查询对应的采购订单下达明细行
            //物料编码
            String materialCode = detail.getString("materialCode");
            //行号
            String lineNum = detail.getString("lineNum");
            //更新对应的采购订单下达明细行的可计划数量和已计划数量
            BmfObject purchaseOrderRelease = purchaseOrderReleaseIdAutoMapping.stream()
                    .filter(item -> materialCode.equals(item.getString("materialCode")) && lineNum.equals(item.getString("lineNum")))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("采购订单下达明细行不存在,物料编码:" + materialCode));
            //采购总数量
            BigDecimal purchaseQuantity = detail.getBigDecimal("purchaseQuantity");
            //旧的已计划数量
            BigDecimal plannedQuantity = purchaseOrderRelease.getBigDecimal("plannedQuantity");
            //新的已计划数量=旧的已计划数量+填写的计划收货数量
            purchaseOrderRelease.put("plannedQuantity", plannedQuantity.add(planReceiveQuantity));
            //新的可计划数量=采购总数量-新的已计划数量
            BigDecimal waitPlanQuantity = BigDecimalUtils.subtractResultMoreThanZero(purchaseQuantity, purchaseOrderRelease.getBigDecimal("plannedQuantity"));
            purchaseOrderRelease.put("waitPlanQuantity", waitPlanQuantity);
        });
        bmfService.saveOrUpdate(purchaseOrder);
    }

    private void check(JSONObject jsonObject) {
        //校验必填项
        validateExist(jsonObject);
        //默认值
        setDefault(jsonObject);
        //校验合法性
        validateLegal(jsonObject);
    }

    /**
     * 校验必填项
     *
     * @param jsonObject 表单json
     */
    private void validateExist(JSONObject jsonObject) {
        if (StringUtils.isBlank(jsonObject.getString("providerCode"))) {
            throw new BusinessException("供应商编码不能为空");
        }
        if (StringUtils.isBlank(jsonObject.getString("buyerCode"))) {
            throw new BusinessException("采购员编码不能为空");
        }
    }

    /**
     * 校验合法性
     *
     * @param jsonObject jsonObject
     */
    private void validateLegal(JSONObject jsonObject) {
        JSONArray purchaseOrderDetailIdAutoMapping = jsonObject.getJSONArray("purchaseOrderDetailIdAutoMapping");
        JSONArray purchaseOrderReleaseIdAutoMapping = new JSONArray();
        if (CollectionUtil.isEmpty(purchaseOrderDetailIdAutoMapping)) {
            throw new BusinessException("采购订单明细不能为空");
        }
//        List<String> materialCodeList = new ArrayList<>();
        List<String> lineNums = new ArrayList<>();
        purchaseOrderDetailIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            //行号不能为空且不能重复
            String lineNum = detail.getString("lineNum");
            if (StringUtils.isBlank(lineNum)) {
                throw new BusinessException("外部行号[lineNum]不能为空");
            }
            if (lineNums.contains(lineNum)) {
                throw new BusinessException("外部行号重复");
            }
            lineNums.add(lineNum);

            String materialCode = detail.getString("materialCode");
            if (StringUtils.isBlank(materialCode)) {
                throw new BusinessException("采购订单明细物料编码不能为空");
            }
//            if (materialCodeList.contains(materialCode)) {
//                throw new BusinessException("物料不能重复选择");
//            }
//            materialCodeList.add(materialCode);

            String warehouseCode = detail.getString("warehouseCode");
            if (StringUtils.isBlank(warehouseCode)) {
                throw new BusinessException("采购订单明细目标仓库编码不能为空");
            }

            //计划数量
            BigDecimal planQuantity = ValueUtil.toBigDecimal(detail.getString("planQuantity"));
            if (planQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("采购订单明细计划数量不能为空或小于等于0");
            }
            //已收货数量
            detail.put("receivedQuantity", BigDecimal.ZERO);
            //待收货数量=计划数量
            detail.put("waitReceivedQuantity", planQuantity);
            //已入库数量
            detail.put("warehousedQuantity", BigDecimal.ZERO);
            //待入库数量
            detail.put("waitWarehousedQuantity", BigDecimal.ZERO);
            //退货数量
            detail.put("returnQuantity", BigDecimal.ZERO);

            //创建下达明细行
            JSONObject item = this.createReleaseDetail(detail);
            purchaseOrderReleaseIdAutoMapping.add(item);
        });
        jsonObject.put("purchaseOrderReleaseIdAutoMapping", purchaseOrderReleaseIdAutoMapping);

    }

    /**
     * 创建下达明细行
     *
     * @param detail 采购订单明细行
     * @return 下达明细行
     */
    public JSONObject createReleaseDetail(JSONObject detail) {
        //下达明细
        JSONObject item = new JSONObject();
        item.put("lineNum", detail.getString("lineNum"));
        item.put("materialName", detail.getString("materialName"));
        item.put("materialCode", detail.getString("materialCode"));
        item.put("specifications", detail.getString("specifications"));
        item.put("unit", detail.getJSONObject("unit"));
        //采购总数量=明细计划数量
        BigDecimal planQuantity = ValueUtil.toBigDecimal(detail.getString("planQuantity"));
        item.put("purchaseQuantity", planQuantity);
        //已计划数量
        BigDecimal plannedQuantity = ValueUtil.toBigDecimal(item.getString("plannedQuantity"));
        item.put("plannedQuantity", plannedQuantity);
        //可计划数量=采购总数量-已计划数量
        item.put("waitPlanQuantity", BigDecimalUtils.subtractResultMoreThanZero(planQuantity, plannedQuantity));
        item.put("warehouseName", detail.getString("warehouseName"));
        item.put("warehouseCode", detail.getString("warehouseCode"));
        item.put("receivedQuantity", BigDecimal.ZERO);
        item.put("waitReceivedQuantity", BigDecimal.ZERO);
        item.put("warehousedQuantity", BigDecimal.ZERO);
        item.put("waitWarehousedQuantity", BigDecimal.ZERO);
        return item;
    }

    /**
     * 设置默认值
     *
     * @param jsonObject jsonObject
     */
    private void setDefault(JSONObject jsonObject) {
        //状态-未处理
        if (StringUtils.isBlank(jsonObject.getString("documentStatus"))) {
            jsonObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        }
        //确认状态-未确认
        if (StringUtils.isBlank(jsonObject.getString("confirmStatus"))) {
            jsonObject.put("confirmStatus", DocumentConfirmStatusEnum.UNCONFIRMED.getCode());
        }
        //来源系统-dwork
        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            jsonObject.put("sourceSystem", SourceSystemEnum.DWORK.getCode());
        }
        //创建日期
        if (StringUtils.isBlank(jsonObject.getString("createDate"))) {
            jsonObject.put("createDate", new Date());
        }
        //明细行号
        if (!StringUtils.equals(SourceSystemEnum.DWORK.getCode(), jsonObject.getString("sourceSystem"))) return;
        List<JSONObject> details = jsonObject.getJSONArray("purchaseOrderDetailIdAutoMapping").toJavaList(JSONObject.class);
        new LineNumUtils().lineNumHandle(null, details);
//        int i = 1;
//        for (JSONObject detail : details) {
//            detail.remove("id");
//            detail.put("lineNum", i++);
//        }

    }

}
