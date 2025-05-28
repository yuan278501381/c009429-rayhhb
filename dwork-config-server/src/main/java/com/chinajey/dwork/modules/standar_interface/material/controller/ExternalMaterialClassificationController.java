package com.chinajey.dwork.modules.standar_interface.material.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.material.form.ExternalMaterialClassificationForm;
import com.chinajey.dwork.modules.standar_interface.material.service.ExternalMaterialClassificationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 物料类别同步
 */
@RestController
@RequestMapping("/external/material-classification")
public class ExternalMaterialClassificationController {

    @Resource
    private ExternalMaterialClassificationService externalMaterialClassificationService;

    @PostMapping
    @BusinessType("同步物料类别")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalMaterialClassificationForm form) {
        BmfObject bmfObject = this.externalMaterialClassificationService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
