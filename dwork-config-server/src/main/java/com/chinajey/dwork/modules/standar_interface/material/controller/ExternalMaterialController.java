package com.chinajey.dwork.modules.standar_interface.material.controller;

import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.material.form.ExternalMaterialForm;
import com.chinajey.dwork.modules.standar_interface.material.service.ExternalMaterialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


/**
 * 物料主数据同步
 */
@RestController
@RequestMapping("/external/material")
public class ExternalMaterialController {

    @Resource
    private ExternalMaterialService externalMaterialService;

    @PostMapping
    @BusinessType("同步物料主数据")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalMaterialForm externalMaterialForm) {
        return InvokeResult.success(this.externalMaterialService.saveOrUpdate(externalMaterialForm));
    }

}
