package com.chinajey.dwork.modules.standar_interface.jig.controller;

import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.jig.from.ExternalJigSyncFrom;
import com.chinajey.dwork.modules.standar_interface.jig.service.ExternalJigSyncService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/external/jig")
public class ExternalJigSyncController {
    @Resource
    private ExternalJigSyncService jigSyncService;


    /**
     * 新增/修改
     */
    @PostMapping
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalJigSyncFrom jigSyncFrom) {
        this.jigSyncService.saveOrUpdate(jigSyncFrom);
        return InvokeResult.success();
    }
}
