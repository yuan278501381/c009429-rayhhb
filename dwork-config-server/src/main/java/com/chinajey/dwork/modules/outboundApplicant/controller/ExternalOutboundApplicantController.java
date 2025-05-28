package com.chinajey.dwork.modules.outboundApplicant.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.outboundApplicant.service.ExternalOutboundApplicantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/external/outbound_applicant")
public class ExternalOutboundApplicantController {
    @Resource
    private ExternalOutboundApplicantService externalOutboundApplicantService;

    @PostMapping("/saveOrUpdate")
    @BusinessType("出库申请单-同步")
    public InvokeResult saveOrUpdate(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(externalOutboundApplicantService.saveOrUpdate(jsonObject));
    }

}
