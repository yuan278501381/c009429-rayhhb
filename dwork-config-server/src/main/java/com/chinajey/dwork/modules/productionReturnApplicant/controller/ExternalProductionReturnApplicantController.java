package com.chinajey.dwork.modules.productionReturnApplicant.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.productionReturnApplicant.service.ExternalProductionReturnApplicantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 外部同步生产退料申请单
 *
 * @author angel.su
 * createTime 2025/4/15 19:55
 */
@RestController
@RequestMapping("/external/production-return-applicant")
public class ExternalProductionReturnApplicantController {
    @Resource
    private ExternalProductionReturnApplicantService externalProductionReturnApplicantService;

    @PostMapping("/saveOrUpdate")
    @BusinessType("生产退料申请单-同步")
    public InvokeResult saveOrUpdate(@RequestBody final JSONObject jsonObject) {
        externalProductionReturnApplicantService.saveOrUpdate(jsonObject);
        return InvokeResult.success();
    }
}
