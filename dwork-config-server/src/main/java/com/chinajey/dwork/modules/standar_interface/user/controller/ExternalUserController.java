package com.chinajey.dwork.modules.standar_interface.user.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.user.form.ExternalUserForm;
import com.chinajey.dwork.modules.standar_interface.user.service.ExternalUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 同步员工
 */
@RestController
@RequestMapping("/external/user")
public class ExternalUserController {

    @Resource
    private ExternalUserService externalUserService;

    @PostMapping
    @BusinessType("同步员工")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalUserForm form) {
        BmfObject bmfObject = this.externalUserService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }

}
