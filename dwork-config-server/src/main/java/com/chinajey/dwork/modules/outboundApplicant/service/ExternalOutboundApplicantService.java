package com.chinajey.dwork.modules.outboundApplicant.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExternalOutboundApplicantService {
    @Resource
    private OutboundApplicantService outboundApplicantService;

    @Resource
    private BmfService bmfService;

    @Transactional(rollbackFor = Exception.class)
    public BmfObject saveOrUpdate(JSONObject jsonObject) {
        this.check(jsonObject);
        jsonObject.put("externalDocumentCode", jsonObject.getString("code"));
        jsonObject.remove("code");
        Map<String, Object> where = new HashMap<>();
        where.put("externalDocumentCode", jsonObject.getString("externalDocumentCode"));
        where.put("externalDocumentType", jsonObject.getString("externalDocumentType"));
        where.put("sourceSystem", jsonObject.getString("sourceSystem"));
        BmfObject outsourceReturnOrder = bmfService.findOne("outboundApplicant", where);
        BmfObject bmfObject = null;
        if (outsourceReturnOrder == null) {
            bmfObject = outboundApplicantService.save(jsonObject);
        } else {
            jsonObject.put("id", outsourceReturnOrder.getPrimaryKeyValue());
            jsonObject.put("code", outsourceReturnOrder.getString("code"));
            bmfObject = outboundApplicantService.update(jsonObject);
            outboundApplicantService.issued(Collections.singletonList(bmfObject.getPrimaryKeyValue()));
        }
        return bmfObject;
    }

    /**
     * 校验
     *
     * @param jsonObject jsonObject
     */
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
        if (StringUtils.isBlank(jsonObject.getString("code"))) {
            throw new BusinessException("来源编码不能为空");
        }

        if (StringUtils.isBlank(jsonObject.getString("sourceSystem"))) {
            throw new BusinessException("来源单据系统不能为空");
        }

        if (StringUtils.isBlank(jsonObject.getString("externalDocumentType"))) {
            throw new BusinessException(" 单据业务类型不能为空");
        }
    }

    /**
     * 校验合法性
     *
     * @param jsonObject jsonObject
     */
    private void validateLegal(JSONObject jsonObject) {
        JSONArray outboundApplicantIdAutoMapping = jsonObject.getJSONArray("outboundApplicantIdAutoMapping");
        if (CollectionUtil.isEmpty(outboundApplicantIdAutoMapping)) {
            throw new BusinessException("出库申请单明细不能为空");
        }
        outboundApplicantIdAutoMapping.toJavaList(JSONObject.class).forEach(detail -> {
            if (StringUtils.isBlank(detail.getString("lineNum"))) {
                throw new BusinessException("出库申请单明细行号不能为空");
            }
            //计划数量校验
            if (ObjectUtils.isEmpty(detail.getBigDecimal("planQuantity"))) {
                throw new BusinessException("出库申请单明细计划出库数量不能为空");
            }
            detail.put("waitQuantity", detail.getString("planQuantity"));
            detail.put("outboundQuantity", BigDecimal.ZERO);
            //物料编码校验
            String materialCode = detail.getString("materialCode");
            if (StringUtils.isBlank(materialCode)) {
                throw new BusinessException("出库申请单明细物料编码不能为空");
            }
            BmfObject material = bmfService.findByUnique("material", "code", materialCode);
            if (material == null) {
                throw new BusinessException("出库申请单明细未找到物料主数据" + materialCode);
            }
            detail.put("materialCode", material.getString("code"));
            detail.put("materialName", material.getString("name"));
            detail.put("specifications", material.getString("specifications"));
            detail.put("unit", material.get("flowUnit"));
            //仓库编码校验
            if (StringUtils.isBlank(detail.getString("sourceWarehouseCode"))) {
                throw new BusinessException("出库申请单明细仓库编码不能为空");
            }
            BmfObject warehouse = bmfService.findByUnique("warehouse", "code", detail.getString("sourceWarehouseCode"));
            if (warehouse == null) {
                throw new BusinessException("出库申请单明细未找到仓库主数据" + detail.getString("sourceWarehouseCode"));
            }
            detail.put("sourceWarehouseCode", warehouse.getString("code"));
            detail.put("sourceWarehouseName", warehouse.getString("name"));
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
    }
}
