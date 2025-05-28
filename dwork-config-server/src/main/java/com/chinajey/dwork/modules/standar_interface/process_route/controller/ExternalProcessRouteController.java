package com.chinajey.dwork.modules.standar_interface.process_route.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.process_route.form.ExternalProcessRouteForm;
import com.chinajey.dwork.modules.standar_interface.process_route.service.ExternalProcessRouteService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 同步工艺路线
 */
@RestController
@RequestMapping("/external/process-route")
public class ExternalProcessRouteController {

    @Resource
    private ExternalProcessRouteService externalProcessRouteService;

    @PostMapping
    @BusinessType("同步工艺路线")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalProcessRouteForm form) {
        BmfObject bmfObject = this.externalProcessRouteService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
