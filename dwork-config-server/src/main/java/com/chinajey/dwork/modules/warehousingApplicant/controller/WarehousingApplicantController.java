package com.chinajey.dwork.modules.warehousingApplicant.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.warehousingApplicant.service.WarehousingApplicantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/internal/warehousingApplicant")
public class WarehousingApplicantController {
    @Resource
    private WarehousingApplicantService warehousingApplicantService;

    @PostMapping("/save")
    @BusinessType("新增入库申请单")
    public InvokeResult save(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(this.warehousingApplicantService.save(jsonObject));
    }

    @PutMapping("/update")
    @BusinessType("更新入库申请单")
    public InvokeResult update(@RequestBody JSONObject json) {
        return InvokeResult.success(this.warehousingApplicantService.update(json));
    }

    @PostMapping("/close")
    @BusinessType("入库申请单-关闭")
    public InvokeResult close(@RequestBody  @Validated List<Long> ids) {
        warehousingApplicantService.close(ids);
        return InvokeResult.success();
    }
    @PostMapping("/cancel")
    @BusinessType("入库申请单-取消")
    public InvokeResult cancel(@RequestBody  @Validated  List<Long> ids) {
        warehousingApplicantService.cancel(ids);
        return InvokeResult.success();
    }

    @PostMapping("/issued")
    @BusinessType("入库申请单-下达")
    public InvokeResult issued(@RequestBody  @Validated List<Long> ids) {
        warehousingApplicantService.issued(ids);
        return InvokeResult.success();
    }

    @PostMapping("/finish")
    @BusinessType("入库申请单-完成")
    public InvokeResult finish(@RequestBody  @Validated List<Long> ids) {
        warehousingApplicantService.finish(ids);
        return InvokeResult.success();
    }
}
