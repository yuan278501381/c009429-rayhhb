package com.chinajey.dwork.modules.standar_interface.measuringTool.controller;

import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.measuringTool.form.ExternalMeasuringToolForm;
import com.chinajey.dwork.modules.standar_interface.measuringTool.service.ExternalMeasuringToolSyncService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 量检具-外部同步
 */
@RestController
@RequestMapping("/external/measuringTool")
public class ExternalMeasuringToolController {

    @Resource
    private ExternalMeasuringToolSyncService measuringToolSyncService;

    @PostMapping
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalMeasuringToolForm measuringToolForm) {
        this.measuringToolSyncService.saveOrUpdate(measuringToolForm);
        return InvokeResult.success();
    }

}
