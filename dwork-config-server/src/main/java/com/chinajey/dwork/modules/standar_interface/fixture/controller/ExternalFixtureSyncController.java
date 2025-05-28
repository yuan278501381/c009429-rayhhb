package com.chinajey.dwork.modules.standar_interface.fixture.controller;

import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.fixture.form.ExternalFixtureSyncForm;
import com.chinajey.dwork.modules.standar_interface.fixture.service.ExternalFixtureSyncService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 夹具-外部同步
 */
@RestController
@RequestMapping("/external/fixture")
public class ExternalFixtureSyncController {
    @Resource
    private ExternalFixtureSyncService fixtureSyncService;

    @PostMapping
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalFixtureSyncForm fixtureSyncForm) {
        this.fixtureSyncService.saveOrUpdate(fixtureSyncForm);
        return InvokeResult.success();
    }
}
