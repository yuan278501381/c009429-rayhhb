package com.chinajey.dwork.modules.standar_interface.department.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.businessPartner.form.ExternalBusinessPartnerForm;
import com.chinajey.dwork.modules.standar_interface.businessPartner.service.ExternalBusinessPartnerService;
import com.chinajey.dwork.modules.standar_interface.department.form.ExternalDepartmentForm;
import com.chinajey.dwork.modules.standar_interface.department.service.ExternalDepartmentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @description:同步部门主数据
 * @author: ZSL
 * @date: 2025/4/18 16:58
 */
@RestController
@RequestMapping("/external/department")
public class ExternalDepartmentController {

    @Resource
    private ExternalDepartmentService externalDepartmentService;
    /**
     * 同步新增更新部门
     */
    @PostMapping
    @BusinessType("同步业务伙伴")
    public InvokeResult saveOrUpdate(@Validated @RequestBody ExternalDepartmentForm form) {
        BmfObject bmfObject = this.externalDepartmentService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
