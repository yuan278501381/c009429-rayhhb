package com.chinajey.dwork.modules.standar_interface.businessPartner.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.businessPartner.form.ExternalBusinessPartnerGroupForm;
import com.chinajey.dwork.modules.standar_interface.businessPartner.service.ExternalBusinessPartnerGroupService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @description:同步业务伙伴组
 * @author: ZSL
 * @date: 2025/4/18 16:58
 */
@RestController
@RequestMapping("/external/business-partner-group")
public class ExternalBusinessPartnerGroupController {

    @Resource
    private ExternalBusinessPartnerGroupService externalBusinessPartnerGroupService;

    @PostMapping
    @BusinessType("同步业务伙伴组")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalBusinessPartnerGroupForm form) {
        BmfObject bmfObject = this.externalBusinessPartnerGroupService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
