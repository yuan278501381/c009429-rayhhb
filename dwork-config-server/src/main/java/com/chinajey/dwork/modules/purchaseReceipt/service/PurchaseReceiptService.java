package com.chinajey.dwork.modules.purchaseReceipt.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.FillUtils;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.modules.logistics.service.LogisticsService;
import com.tengnat.dwork.modules.openapi.domain.form.BatchLoHandle;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 采购收货计划
 *
 * @author angel.su
 * createTime 2025/3/19 09:53
 */
@Service
public class PurchaseReceiptService {
    @Resource
    private BmfService bmfService;
    @Resource
    private CodeGenerator codeGenerator;
    @Resource
    private LogisticsService logisticsService;
    @Resource
    private BusinessUtils businessUtils;

    private static final String BMF_CLASS = "purchaseReceipt";
    private static final String SOURCE_BMF_CLASS = "purchaseOrder";
    private static final String BUSINESS = "issuedPurchaseReceipt";
    private static final String GN_APP = "GN10005";

    /**
     * 添加采购收货计划
     *
     * @param jsonObject 订单信息
     * @return 订单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public BmfObject save(JSONObject jsonObject) {
        jsonObject.remove("id");
        this.check(jsonObject);
        BmfObject bmfObject = BmfUtils.genericFromJson(jsonObject, BMF_CLASS);
        codeGenerator.setCode(bmfObject);
        FillUtils.fillOperator(bmfObject);
        bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    /**
     * 更新采购收货计划
     *
     * @param jsonObject 订单信息
     * @return 订单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public BmfObject update(JSONObject jsonObject) {
        Long id = jsonObject.getLong("id");
        BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
        if (bmfObject == null) {
            throw new BusinessException("采购收货计划不存在,id:" + id);
        }
        String status = bmfObject.getString("documentStatus");
        if (!DocumentStatusEnum.whetherUpdate(status)) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能更新");
        }

        //更新前保存旧的采购收货计划明细用于计算
        List<BmfObject> oldPurchaseReceiptIdAutoMapping = bmfObject.getAndRefreshList("purchaseReceiptIdAutoMapping");

        this.check(jsonObject);

        //更新对应的采购订单明细行已计划数量
        //采购订单编码
        String purchaseOrderCode = bmfObject.getString("preDocumentCode");
        //采购订单
        BmfObject purchaseOrder = bmfService.findByUnique(SOURCE_BMF_CLASS, "code", purchaseOrderCode);
        if (purchaseOrder == null) {
            throw new BusinessException("采购订单不存在,采购订单编码:" + purchaseOrderCode);
        }
        purchaseOrder.autoRefresh();
        //采购订单下达明细
        List<BmfObject> purchaseOrderReleaseIdAutoMapping = purchaseOrder.getAndRefreshList("purchaseOrderReleaseIdAutoMapping");

        //新的采购收货计划明细
        JSONArray purchaseReceiptIdAutoMapping = jsonObject.getJSONArray("purchaseReceiptIdAutoMapping");
        purchaseReceiptIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            //物料编码
            String materialCode = detail.getString("materialCode");
            //行号
            String lineNum = detail.getString("lineNum");
            //对应的采购订单下达明细
            BmfObject purchaseOrderRelease = purchaseOrderReleaseIdAutoMapping.stream()
                    .filter(item -> materialCode.equals(item.getString("materialCode")) && lineNum.equals(item.getString("lineNum")))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("采购订单下达明细行不存在,物料编码:" + materialCode));
            //对应旧的采购收货计划明细
            BmfObject oldPurchaseReceipt = oldPurchaseReceiptIdAutoMapping.stream()
                    .filter(item -> materialCode.equals(item.getString("materialCode")) && lineNum.equals(item.getString("lineNum")))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("采购收货计划明细行不存在,物料编码:" + materialCode));
            //采购总数量
            BigDecimal purchaseQuantity = detail.getBigDecimal("purchaseQuantity");
            //填写的计划收货数量
            BigDecimal planReceiveQuantity = ValueUtil.toBigDecimal(detail.getString("planReceiveQuantity"));
            //旧的计划收货数量
            BigDecimal oldPlanReceiveQuantity = oldPurchaseReceipt.getBigDecimal("planReceiveQuantity");
            //旧的已计划数量
            BigDecimal plannedQuantity = purchaseOrderRelease.getBigDecimal("plannedQuantity");
            //新的已计划数量=旧的已计划数量-旧的计划收货数量+填写的计划收货数量
            purchaseOrderRelease.put("plannedQuantity", BigDecimalUtils.subtractResultMoreThanZero(plannedQuantity, oldPlanReceiveQuantity).add(planReceiveQuantity));
            //新的可计划数量=采购总数量-新的已计划数量
            purchaseOrderRelease.put("waitPlanQuantity", BigDecimalUtils.subtractResultMoreThanZero(purchaseQuantity, purchaseOrderRelease.getBigDecimal("plannedQuantity")));
//            //新的待收货数量=填写的计划收货数量
//            purchaseOrderRelease.put("waitReceivedQuantity", planReceiveQuantity);
//            //新的待入库数量=填写的计划收货数量
//            purchaseOrderRelease.put("waitWarehousedQuantity", planReceiveQuantity);

            //采购收货计划新的待收货数量=填写的计划收货数量
            detail.put("waitReceivedQuantity", planReceiveQuantity);
        });

        BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
        //更新状态为 未处理
        bmfObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        //更新采购收货计划
        bmfService.saveOrUpdate(bmfObject);
        //更新采购订单
        bmfService.saveOrUpdate(purchaseOrder);

        return bmfObject;
    }

    /**
     * 删除采购收货计划
     *
     * @param ids 采购收货计划id
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        for (Long id : ids) {
            //删除对应的采购订单下达明细行已计划数量
            this.updatePurchaseOrderRelease(id);
            //执行删除
            bmfService.delete(BMF_CLASS, id);
        }
    }

    /**
     * 删除采购订单下达明细已计划数量
     *
     * @param id 采购收货计划id
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchaseOrderRelease(Long id) {
        //要删除的采购收货计划
        BmfObject purchaseReceipt = bmfService.find(BMF_CLASS, id);
        String status = purchaseReceipt.getString("documentStatus");
        if (!DocumentStatusEnum.UNTREATED.getCode().equals(status)) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能删除");
        }
        //上级内部单据编码-采购订单编码
        String code = purchaseReceipt.getString("preDocumentCode");
        BmfObject purchaseOrder = bmfService.findByUnique(SOURCE_BMF_CLASS, "code", code);
        if (purchaseOrder == null) {
            throw new BusinessException("采购订单不存在,采购订单编码:" + code);
        }
        purchaseOrder.autoRefresh();

        //采购订单下达明细
        List<BmfObject> purchaseOrderReleaseIdAutoMapping = purchaseOrder.getAndRefreshList("purchaseOrderReleaseIdAutoMapping");
        //采购收货计划明细
        List<BmfObject> purchaseReceiptIdAutoMapping = purchaseReceipt.getAndRefreshList("purchaseReceiptIdAutoMapping");
        purchaseReceiptIdAutoMapping.forEach(detail -> {
            //物料编码
            String materialCode = detail.getString("materialCode");
            //行号
            String lineNum = detail.getString("lineNum");
            //对应的采购订单下达明细
            BmfObject purchaseOrderRelease = purchaseOrderReleaseIdAutoMapping.stream()
                    .filter(item -> materialCode.equals(item.getString("materialCode")) && lineNum.equals(item.getString("lineNum")))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("采购订单下达明细行不存在,物料编码:" + materialCode));
            //采购总数量
            BigDecimal purchaseQuantity = detail.getBigDecimal("purchaseQuantity");
            //填写的计划收货数量
            BigDecimal planReceiveQuantity = ValueUtil.toBigDecimal(detail.getString("planReceiveQuantity"));
            //旧的已计划数量
            BigDecimal plannedQuantity = purchaseOrderRelease.getBigDecimal("plannedQuantity");
            //新的已计划数量=旧的已计划数量-填写的计划收货数量
            purchaseOrderRelease.put("plannedQuantity", BigDecimalUtils.subtractResultMoreThanZero(plannedQuantity, planReceiveQuantity));
            //新的可计划数量=采购总数量-新的已计划数量
            purchaseOrderRelease.put("waitPlanQuantity", BigDecimalUtils.subtractResultMoreThanZero(purchaseQuantity, purchaseOrderRelease.getBigDecimal("plannedQuantity")));

        });
        bmfService.saveOrUpdate(purchaseOrder);
    }

    /**
     * 下达采购收货计划
     *
     * @param ids 采购收货计划id
     */
    @Transactional(rollbackFor = Exception.class)
    public void issued(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购收货计划ID不能为空");
        }
        for (Long id : ids) {
            BmfObject purchaseReceipt = bmfService.find(BMF_CLASS, id);
            if (purchaseReceipt == null) {
                throw new BusinessException("采购收货计划不存在,ID:" + id);
            }
            String status = purchaseReceipt.getString("documentStatus");
            if (!DocumentStatusEnum.whetherIssued(status)) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能下达");
            }
            //下达更新单据状态-待处理
            businessUtils.updateStatus(purchaseReceipt, DocumentStatusEnum.PENDING.getCode());

            //按照同物料+仓库合并下达
            List<BmfObject> details = purchaseReceipt.getAndRefreshList("purchaseReceiptIdAutoMapping");
            Map<String, BmfObject> map = this.groupByMaterialCode(details);
            List<JSONObject> issuedCollects = new ArrayList<>(map.values()).stream().map(detail -> {
                detail.getAndRefreshBmfObject("unit");
                BmfObject purchaseReceiptData = purchaseReceipt.deepClone();
                purchaseReceiptData.put("purchaseReceiptIdAutoMapping", Collections.singletonList(detail));
                JSONObject batchLoHandleItem = new JSONObject();
                batchLoHandleItem.put("businessCode", BUSINESS);
                batchLoHandleItem.put("jsonObject", purchaseReceiptData);
                return batchLoHandleItem;
            }).collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(issuedCollects)) {
                JSONObject batchLoHandle = new JSONObject();
                batchLoHandle.put("assigns", issuedCollects);
                logisticsService.batchHandle(batchLoHandle.toJavaObject(BatchLoHandle.class));
            }

        }
    }

    private Map<String, BmfObject> groupByMaterialCode(List<BmfObject> details) {
        //按照物料 待收货数量大于0
        Map<String, BmfObject> map = new HashMap<>(details.size());
        String unKey;
        JSONObject unValue;
        for (BmfObject item : details) {
            if (item.getBigDecimal("waitReceivedQuantity").compareTo(BigDecimal.ZERO) > 0) {
                unKey = ValueUtil.toStr(item.getString("materialCode")) + ValueUtil.toStr(item.getString("warehouseCode"));
//                unKey = ValueUtil.toStr(item.getString("materialCode"));
                unValue = map.get(unKey);
                if (unValue == null) {
                    map.put(unKey, item);
                } else {
                    //计划收货数量
                    BigDecimal planReceiveQuantity = BigDecimalUtils.add(unValue.getBigDecimal("planReceiveQuantity"), item.getBigDecimal("planReceiveQuantity"));
                    //已收货数量
                    BigDecimal receivedQuantity = BigDecimalUtils.add(unValue.getBigDecimal("receivedQuantity"), item.getBigDecimal("receivedQuantity"));
                    //待收货数量
                    BigDecimal waitReceivedQuantity = BigDecimalUtils.add(unValue.getBigDecimal("waitReceivedQuantity"), item.getBigDecimal("waitReceivedQuantity"));
                    unValue.put("planReceiveQuantity", planReceiveQuantity);
                    unValue.put("receivedQuantity", receivedQuantity);
                    unValue.put("waitReceivedQuantity", waitReceivedQuantity);
                }
            }
        }
        return map;
    }

    /**
     * 关闭采购收货计划
     *
     * @param ids 采购收货计划id
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购收货计划ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("采购收货计划不存在,id:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherClose(status)) {
                throw new BusinessException("采购收货计划状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能关闭");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.CLOSED.getCode());
            businessUtils.closeCurrentTask(GN_APP, "preDocumentCode", bmfObject.getString("code"));
        }
    }

    /**
     * 取消采购收货计划
     *
     * @param ids 采购收货计划id
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("采购订单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("采购收货计划不存在,id:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherCancel(status)) {
                throw new BusinessException("采购收货计划状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能取消");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.CANCEL.getCode());
            businessUtils.closeCurrentTask(GN_APP, "preDocumentCode", bmfObject.getString("code"));
        }
    }

    /**
     * 完成采购收货计划
     *
     * @param ids 采购收货计划id
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
                throw new BusinessException("采购收货计划状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能完成");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.COMPLETED.getCode());
            businessUtils.closeCurrentTask(GN_APP, "preDocumentCode", bmfObject.getString("code"));
        }
    }

    private void check(JSONObject jsonObject) {
        //校验合法性
        validateLegal(jsonObject);
        //默认值
        setDefault(jsonObject);
    }

    /**
     * 校验合法性
     *
     * @param jsonObject jsonObject
     */
    private void validateLegal(JSONObject jsonObject) {
        JSONArray purchaseReceiptIdAutoMapping = jsonObject.getJSONArray("purchaseReceiptIdAutoMapping");
        if (CollectionUtil.isEmpty(purchaseReceiptIdAutoMapping)) {
            throw new BusinessException("采购收货计划明细不能为空");
        }
        purchaseReceiptIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            String planArrivalDate = detail.getString("planArrivalDate");
            if (StringUtils.isBlank(planArrivalDate)) {
                throw new BusinessException("计划到货日期不能为空");
            }
            if (StringUtils.isBlank(detail.getString("lineNum"))) {
                throw new BusinessException("行号不能为空");
            }

            //计划收货数量
            BigDecimal planReceiveQuantity = ValueUtil.toBigDecimal(detail.getString("planReceiveQuantity"));
            if (planReceiveQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("计划收货数量不能为空或小于等于0");
            }
        });

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
        //来源系统-dwork
        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            jsonObject.put("sourceSystem", SourceSystemEnum.DWORK.getCode());
        }
        //下达时间
        if (StringUtils.isBlank(jsonObject.getString("releaseDate"))) {
            jsonObject.put("releaseDate", new Date());
        }
//        //明细行号
//        int i = 1;
//        List<JSONObject> details = jsonObject.getJSONArray("purchaseReceiptIdAutoMapping").toJavaList(JSONObject.class);
//        for (JSONObject detail : details) {
//            detail.remove("id");
//            detail.put("lineNum", i++);
//        }

    }



    /**
     * 反写待入库数量
     *
     * @param detailList 明细
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(List<BmfObject> detailList) {
        detailList.forEach(detail -> {
            //已收货数量
            BigDecimal receivedQuantity = detail.getBigDecimal("receivedQuantity");
            //已入库数量
            BigDecimal warehousedQuantity = detail.getBigDecimal("warehousedQuantity");
            //新的待入库数量=已收货数量-已入库数量
            BigDecimal newWaitWarehousedQuantity = BigDecimalUtils.subtractResultMoreThanZero(receivedQuantity, warehousedQuantity);
            detail.put("waitWarehousedQuantity", newWaitWarehousedQuantity);
            bmfService.updateByPrimaryKeySelective(detail);
        });
    }
}
