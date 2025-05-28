package com.chinajey.dwork.modules.standar_interface.knife.controller;

import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.knife.form.ExternalKnifeSyncForm;
import com.chinajey.dwork.modules.standar_interface.knife.service.ExternalKnifeSyncService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/external/knife")
public class ExternalKnifeSyncController {
    @Resource
    private ExternalKnifeSyncService knifeSyncService;


    /**
     * 新增/修改
     */
    @PostMapping
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalKnifeSyncForm knifeSyncForm) {
        this.knifeSyncService.saveOrUpdate(knifeSyncForm);
        return InvokeResult.success();
    }
}
