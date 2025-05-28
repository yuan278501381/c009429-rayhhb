package com.chinajey.dwork.modules.standar_interface.warehouse.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.warehouse.form.ExternalWarehouseForm;
import com.chinajey.dwork.modules.standar_interface.warehouse.service.ExternalWarehouseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 同步仓库
 */
@RestController
@RequestMapping("/external/warehouse")
public class ExternalWarehouseController {

    @Resource
    private ExternalWarehouseService externalWarehouseService;

    @PostMapping
    @BusinessType("同步仓库")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalWarehouseForm form) {
        BmfObject bmfObject = this.externalWarehouseService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
