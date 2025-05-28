package com.chinajey.dwork.modules.standar_interface.warehouse.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.warehouse.form.ExternalWarehouseCategoryForm;
import com.chinajey.dwork.modules.standar_interface.warehouse.service.ExternalWarehouseCategoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 同步仓库类别
 */
@RestController
@RequestMapping("/external/warehouse-category")
public class ExternalWarehouseCategoryController {

    @Resource
    private ExternalWarehouseCategoryService externalWarehouseCategoryService;

    @PostMapping
    @BusinessType("同步仓库类别")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalWarehouseCategoryForm form) {
        BmfObject bmfObject = this.externalWarehouseCategoryService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
