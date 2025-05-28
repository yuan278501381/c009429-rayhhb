package com.chinajey.dwork.modules.standar_interface.equipment.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.equipment.form.ExternalEquipmentClassificationForm;
import com.chinajey.dwork.modules.standar_interface.equipment.service.ExternalEquipmentClassificationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @description:同步设备类别
 * @author: ZSL
 * @date: 2025/4/18 16:58
 */
@RestController
@RequestMapping("/external/equipment-classification")
public class ExternalEquipmentClassificationController {

    @Resource
    private ExternalEquipmentClassificationService externalEquipmentClassificationService;

    @PostMapping
    @BusinessType("同步业务伙伴组")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalEquipmentClassificationForm form) {
        BmfObject bmfObject = this.externalEquipmentClassificationService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
