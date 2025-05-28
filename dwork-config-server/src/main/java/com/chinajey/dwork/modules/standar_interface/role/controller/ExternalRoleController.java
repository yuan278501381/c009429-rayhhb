package com.chinajey.dwork.modules.standar_interface.role.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.role.form.ExternalRoleForm;
import com.chinajey.dwork.modules.standar_interface.role.service.ExternalRoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 同步角色
 */
@RestController
@RequestMapping("/external/role")
public class ExternalRoleController {

    @Resource
    private ExternalRoleService externalRoleService;

    @PostMapping
    @BusinessType("同步员工")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalRoleForm form) {
        BmfObject bmfObject = this.externalRoleService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }

}
