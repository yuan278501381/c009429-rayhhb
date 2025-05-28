package com.chinajey.dwork.modules.standar_interface.mold.controller;

import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.mold.form.ExternalMoldSyncForm;
import com.chinajey.dwork.modules.standar_interface.mold.service.ExternalMoldSyncService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/external/mold")
public class ExternalMoldSyncController {
    @Resource
    private ExternalMoldSyncService moldOaService;


    /**
     * 新增/修改
     */
    @PostMapping
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalMoldSyncForm moldSyncForm) {
        this.moldOaService.saveOrUpdate(moldSyncForm);
        return InvokeResult.success();
    }
}
