package com.chinajey.dwork.modules.standar_interface.toolScheme.controller;

import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.toolScheme.form.ExternalToolSchemeForm;
import com.chinajey.dwork.modules.standar_interface.toolScheme.service.ExternalToolSchemeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 工器具方案同步
 */
@RestController
@RequestMapping("/external/toolScheme")
public class ExternalToolSchemeController {

    @Resource
    private ExternalToolSchemeService toolSchemeSyncService;

    @PostMapping
    @BusinessType("同步工器具方案")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalToolSchemeForm form) {
        return InvokeResult.success(this.toolSchemeSyncService.saveOrUpdate(form));
    }
}
