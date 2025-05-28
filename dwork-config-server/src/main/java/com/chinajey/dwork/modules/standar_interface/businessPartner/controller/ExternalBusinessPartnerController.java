package com.chinajey.dwork.modules.standar_interface.businessPartner.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.businessPartner.form.ExternalBusinessPartnerForm;
import com.chinajey.dwork.modules.standar_interface.businessPartner.service.ExternalBusinessPartnerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @description:同步业务伙伴主数据
 * @author: ZSL
 * @date: 2025/4/18 16:58
 */
@RestController
@RequestMapping("/external/business-partner")
public class ExternalBusinessPartnerController {

    @Resource
    private ExternalBusinessPartnerService externalBusinessPartnerService;
    /**
     * 同步新增业务伙伴
     */
    @PostMapping
    @BusinessType("同步业务伙伴")
    public InvokeResult saveOrUpdate(@Validated @RequestBody ExternalBusinessPartnerForm form) {
        BmfObject bmfObject = this.externalBusinessPartnerService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
