package com.chinajey.dwork.modules.warehousingApplicant.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.warehousingApplicant.form.StoreForm;
import com.chinajey.dwork.modules.warehousingApplicant.service.ExternalWarehousingApplicantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/external/warehousingApplicant")
public class ExternalWarehousingApplicantController {

    @Resource
    private ExternalWarehousingApplicantService externalWarehousingApplicantService;

    @PostMapping("/saveOrUpdate")
    @BusinessType("入库申请单-同步")
    public InvokeResult saveOrUpdate(@RequestBody @Validated StoreForm form) {
        BmfObject bmfObject = this.externalWarehousingApplicantService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }

}
