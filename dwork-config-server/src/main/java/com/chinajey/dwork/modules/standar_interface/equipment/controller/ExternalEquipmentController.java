package com.chinajey.dwork.modules.standar_interface.equipment.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.equipment.form.ExternalEquipmentForm;
import com.chinajey.dwork.modules.standar_interface.equipment.service.ExternalEquipmentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

/**
 * 同步设备
 * @author erton.bi
 */
@RestController
@RequestMapping("/external/equipment")
public class ExternalEquipmentController {

    @Resource
    private ExternalEquipmentService externalEquipmentService;

    @PostMapping()
    @BusinessType("同步设备")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalEquipmentForm form) {
        BmfObject bmfObject = this.externalEquipmentService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }

}
