package com.chinajey.dwork.modules.standar_interface.packScheme.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.packScheme.form.ExternalPackSchemeForm;
import com.chinajey.dwork.modules.standar_interface.packScheme.service.ExternalPackSchemeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 同步包装方案
 * @author erton.bi
 */
@RestController
@RequestMapping("/external/packScheme")
public class ExternalPackSchemeController {

    @Resource
    private ExternalPackSchemeService externalPackSchemeService;

    @PostMapping()
    @BusinessType("同步包装方案")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalPackSchemeForm form) {
        BmfObject bmfObject = this.externalPackSchemeService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
