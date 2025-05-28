package com.chinajey.dwork.modules.productionReturnApplicant.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.modules.warehousingApplicant.service.WarehousingApplicantService;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.common.utils.LogisticsUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生产退料申请单
 *
 * @author angel.su
 * createTime 2025/4/15 15:47
 */
@Service
public class ProductionReturnApplicantService {
    @Resource
    private BmfService bmfService;
    @Resource
    private CodeGenerator codeGenerator;
    @Resource
    private BusinessUtils businessUtils;
    @Resource
    private WarehousingApplicantService warehousingApplicantService;

    private static final String BMF_CLASS = "productionReturnApplicant";
    private static final String DETAIL_ATTR = "productionReturnApplicantDetailIdAutoMapping";
    private static final String PASS_BOX_ATTR = "productionReturnApplicantPassBoxIdAutoMapping";

    /**
     * 添加生产退料申请单
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
        bmfService.saveOrUpdate(bmfObject);
        Long id = bmfObject.getPrimaryKeyValue();
        if (id != null) {
            this.issued(Collections.singletonList(id));
        }
        return bmfObject;
    }

    /**
     * 下达生产退料申请单
     *
     * @param ids 生产退料申请单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void issued(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("生产退料申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("生产退料申请单不存在,ID:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherIssued(status)) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能下达");
            }
            //下达更新单据状态-待处理
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.PENDING.getCode());

            //周转箱
            List<BmfObject> passBoxes = bmfObject.getAndRefreshList(PASS_BOX_ATTR);
            //明细
            List<BmfObject> details = bmfObject.getAndRefreshList(DETAIL_ATTR);
            //创建生产退料类型入库申请单并自动下达
            this.createWarehousingApplicant(bmfObject, details, passBoxes);
        }
    }

    /**
     * 创建生产退料类型入库申请单并自动下达
     *
     * @param productionReturnApplicant 生产退料申请单
     * @param details                   明细
     */
    private void createWarehousingApplicant(BmfObject productionReturnApplicant, List<BmfObject> details, List<BmfObject> passBoxes) {
        //子表
        List<BmfObject> warehousingApplicantIdAutoMapping = new ArrayList<>();
        for (BmfObject detail : details) {
            BigDecimal planQuantity = detail.getBigDecimal("planReturnQuantity");
            String lineNum = detail.getString("lineNum");
            BmfObject warehousingApplicantDetail = new BmfObject("warehousingApplicantDetail");
            warehousingApplicantDetail.put("lineNum", lineNum);
            warehousingApplicantDetail.put("materialCode", detail.getString("materialCode"));
            warehousingApplicantDetail.put("materialName", detail.getString("materialName"));
            warehousingApplicantDetail.put("specifications", detail.getString("specifications"));
            BmfObject unit = detail.getAndRefreshBmfObject("unit");
            warehousingApplicantDetail.put("unit", unit);
            warehousingApplicantDetail.put("targetWarehouseCode", detail.getString("targetWarehouseCode"));
            warehousingApplicantDetail.put("targetWarehouseName", detail.getString("targetWarehouseName"));
            warehousingApplicantDetail.put("planQuantity", planQuantity);
            warehousingApplicantDetail.put("warehousingQuantity", BigDecimal.ZERO);
            warehousingApplicantDetail.put("confirmedQuantity", BigDecimal.ZERO);
            warehousingApplicantDetail.put("waitConfirmQuantity", planQuantity);
            LogisticsUtils.setDocumentData(productionReturnApplicant, warehousingApplicantDetail);
            warehousingApplicantIdAutoMapping.add(warehousingApplicantDetail);
        }

        //周转箱
        passBoxes.forEach(passBox -> {
            passBox.remove("id");
            passBox.put("warehouseCode", passBox.get("targetWarehouseCode"));
            passBox.put("warehouseName", passBox.get("targetWarehouseName"));
            passBox.put("documentStatus", DocumentStatusEnum.PENDING.getCode());
        });

        //主表
        BmfObject bmfObject = new BmfObject("warehousingApplicant");
        bmfObject.put("warehousingApplicantIdAutoMapping", warehousingApplicantIdAutoMapping);
        bmfObject.put("warehousingApplicantPassBoxIdAutoMapping", passBoxes);
        bmfObject.put("orderBusinessType", "productionReturn");
        LogisticsUtils.setDocumentData(productionReturnApplicant, bmfObject);
        bmfObject = warehousingApplicantService.save(bmfObject);
        if (bmfObject != null) {
            warehousingApplicantService.issued(Collections.singletonList(bmfObject.getPrimaryKeyValue()));
        }
    }

    /**
     * 关闭生产退料申请单
     *
     * @param ids 生产退料申请单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void close(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("生产退料申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("生产退料申请单不存在,id:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherClose(status) && !DocumentStatusEnum.UNTREATED.getCode().equals(status)) {
                throw new BusinessException("生产退料申请单状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能关闭");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.CLOSED.getCode());

            String preDocumentCode = bmfObject.getString("code");
            BmfObject warehousingApplicant = bmfService.findByUnique("warehousingApplicant", "preDocumentCode", preDocumentCode);
            if (warehousingApplicant != null) {
                warehousingApplicantService.close(Collections.singletonList(warehousingApplicant.getPrimaryKeyValue()));
            }
        }
    }

    /**
     * 取消生产退料申请单
     *
     * @param ids 生产退料申请单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("生产退料申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("生产退料申请单不存在,id:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherCancel(status) && !DocumentStatusEnum.UNTREATED.getCode().equals(status)) {
                throw new BusinessException("生产退料申请单状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能取消");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.CANCEL.getCode());

            String preDocumentCode = bmfObject.getString("code");
            BmfObject warehousingApplicant = bmfService.findByUnique("warehousingApplicant", "preDocumentCode", preDocumentCode);
            if (warehousingApplicant != null) {
                warehousingApplicantService.cancel(Collections.singletonList(warehousingApplicant.getPrimaryKeyValue()));
            }
        }
    }

    /**
     * 完成生产退料申请单
     *
     * @param ids 生产退料申请单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void finish(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("生产退料申请单ID不能为空");
        }
        for (Long id : ids) {
            BmfObject bmfObject = bmfService.find(BMF_CLASS, id);
            if (bmfObject == null) {
                throw new BusinessException("生产退料申请单不存在,id:" + id);
            }
            String status = bmfObject.getString("documentStatus");
            if (!DocumentStatusEnum.whetherComplete(status)) {
                throw new BusinessException("生产退料申请单状态为[" + DocumentStatusEnum.getEnum(status).getName() + "]，不能完成");
            }
            businessUtils.updateStatus(bmfObject, DocumentStatusEnum.COMPLETED.getCode());

            String preDocumentCode = bmfObject.getString("code");
            BmfObject warehousingApplicant = bmfService.findByUnique("warehousingApplicant", "preDocumentCode", preDocumentCode);
            if (warehousingApplicant != null) {
                warehousingApplicantService.finish(Collections.singletonList(warehousingApplicant.getPrimaryKeyValue()));
            }
        }
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
     * @param jsonObject 表单json
     */
    private void validateExist(JSONObject jsonObject) {
        if (jsonObject == null) {
            throw new BusinessException("参数不能为空");
        }
        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            throw new BusinessException("来源系统不能为空");
        }
    }

    /**
     * 校验合法性
     *
     * @param jsonObject jsonObject
     */
    private void validateLegal(JSONObject jsonObject) {
        //主表-校验工序编码存在
        String processCode = jsonObject.getString("processCode");
        if (StringUtils.isBlank(processCode)) {
            throw new BusinessException("工序编码[processCode]不能为空");
        }
        BmfObject process = bmfService.findByUnique("workProcedure", "code", processCode);
        if (process == null) {
            throw new BusinessException("生产退料申请单未找到工序主数据" + processCode);
        }
        jsonObject.put("processName", process.getString("name"));

        //退料信息-校验生产订单编码(源头内部)、物料编码、接收仓库编码存在，计划退料数量大于0，同生产订单行号不能重复
        JSONArray details = jsonObject.getJSONArray(DETAIL_ATTR);
        if (CollectionUtil.isEmpty(details)) {
            throw new BusinessException("生产退料申请单明细不能为空");
        }
        details.toJavaList(JSONObject.class).forEach(detail -> {
            detail.remove("id");
            //生产订单编码
            String sourceDocumentCode = detail.getString("sourceDocumentCode");
            if (StringUtils.isBlank(sourceDocumentCode)) {
                throw new BusinessException("生产退料申请单明细生产订单编码[sourceDocumentCode]不能为空");
            }
            BmfObject productOrder = bmfService.findByUnique("productOrder", "code", sourceDocumentCode);
            if (productOrder == null) {
                throw new BusinessException("生产退料申请单明细未找到生产订单" + sourceDocumentCode);
            }

            //物料编码
            String materialCode = detail.getString("materialCode");
            if (StringUtils.isBlank(materialCode)) {
                throw new BusinessException("生产退料申请单明细物料编码[materialCode]不能为空");
            }
            BmfObject material = bmfService.findByUnique("material", "code", materialCode);
            if (material == null) {
                throw new BusinessException("生产退料申请单明细未找到物料主数据" + materialCode);
            }
            detail.put("materialName", material.getString("name"));
            detail.put("specifications", material.getString("specifications"));
            detail.put("unit", material.get("flowUnit"));

            //接收仓库编码
            String targetWarehouseCode = detail.getString("targetWarehouseCode");
            if (StringUtils.isBlank(targetWarehouseCode)) {
                throw new BusinessException("生产退料申请单明细接收仓库编码[targetWarehouseCode]不能为空");
            }
            BmfObject targetWarehouse = bmfService.findByUnique("warehouse", "code", targetWarehouseCode);
            if (targetWarehouse == null) {
                throw new BusinessException("生产退料申请单明细未找到仓库主数据" + targetWarehouseCode);
            }
            detail.put("targetWarehouseName", targetWarehouse.getString("name"));

            //计划退料数量
            BigDecimal planReturnQuantity = ValueUtil.toBigDecimal(detail.getString("planReturnQuantity"));
            if (planReturnQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("生产退料申请单明细计划数量不能为空或小于等于0");
            }
            //已退数量
            detail.put("returnedQuantity", BigDecimal.ZERO);
            //待退料数量
            detail.put("waitReturnQuantity", planReturnQuantity);
        });

        //校验同生产订单行号不能重复
        //根据生产订单编码分组
        Map<String, List<JSONObject>> detailMap = details.toJavaList(JSONObject.class).stream()
                .collect(Collectors.groupingBy(detail -> detail.getString("sourceDocumentCode")));
        for (String key : detailMap.keySet()) {
            List<String> lineNums = new ArrayList<>();
            for (JSONObject detail : detailMap.get(key)) {
                String lineNum = detail.getString("lineNum");
                if (StringUtils.isBlank(lineNum)) {
                    throw new BusinessException("明细行号[lineNum]不能为空");
                }
                if (lineNums.contains(lineNum)) {
                    throw new BusinessException("生产订单" + key + "明细行号重复");
                }
                lineNums.add(lineNum);
            }
        }

        //周转箱信息-校验周转箱编码(周转箱实时表)存在
        JSONArray passBoxes = jsonObject.getJSONArray(PASS_BOX_ATTR);
        //来源系统
        String sourceSystem = jsonObject.getString("sourceSystem");
        if (CollectionUtil.isNotEmpty(passBoxes) && sourceSystem.equals(SourceSystemEnum.DWORK.getCode())) {
            passBoxes.toJavaList(JSONObject.class).forEach(passBoxInfo -> {
                passBoxInfo.remove("id");
                String passBoxCode = passBoxInfo.getString("passBoxCode");
                if (StringUtils.isBlank(passBoxCode)) {
                    throw new BusinessException("生产退料申请单周转箱编码[passBoxCode]不能为空");
                }
                BmfObject passBox = bmfService.findByUnique("passBoxReal", "passBoxCode", passBoxCode);
                if (passBox == null) {
                    throw new BusinessException("生产退料申请单未找到周转箱主数据" + passBoxCode);
                }
                passBoxInfo.put("passBoxRealCode", passBox.getString("code"));
                passBoxInfo.put("passBoxName", passBox.getString("passBoxName"));
            });
        }
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
    }
}
