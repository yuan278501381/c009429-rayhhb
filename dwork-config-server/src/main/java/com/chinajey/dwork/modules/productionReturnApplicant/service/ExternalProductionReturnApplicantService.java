package com.chinajey.dwork.modules.productionReturnApplicant.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 外部同步生产退料申请单
 *
 * @author angel.su
 * createTime 2025/4/15 19:15
 */
@Service
public class ExternalProductionReturnApplicantService {
    @Resource
    private ProductionReturnApplicantService productionReturnApplicantService;

    private static final String BMF_CLASS = "productionReturnApplicant";

    @Transactional(rollbackFor = Exception.class)
    public BmfObject saveOrUpdate(JSONObject jsonObject) {
        String code = jsonObject.getString("externalDocumentCode");
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("外部单据编码不能为空");
        }
        //外部单据类型-默认生产退料申请单
        jsonObject.putIfAbsent("externalDocumentType", BMF_CLASS);

        return productionReturnApplicantService.save(jsonObject);

    }

}
