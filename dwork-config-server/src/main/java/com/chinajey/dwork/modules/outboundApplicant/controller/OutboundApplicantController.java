package com.chinajey.dwork.modules.outboundApplicant.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.outboundApplicant.service.OutboundApplicantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/internal/outboundApplicant")
public class OutboundApplicantController {
    @Resource
    private OutboundApplicantService outboundApplicantService;

    @PostMapping("/save")
    @BusinessType("新增出库申请单")
    public InvokeResult save(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(this.outboundApplicantService.save(jsonObject));
    }

    @PutMapping("/update")
    @BusinessType("更新出库申请单")
    public InvokeResult update(@RequestBody JSONObject json) {
        return InvokeResult.success(this.outboundApplicantService.update(json));
    }

    @PostMapping("/close")
    @BusinessType("出库申请单-关闭")
    public InvokeResult close(@RequestBody  @Validated List<Long> ids) {
        outboundApplicantService.close(ids);
        return InvokeResult.success();
    }
    @PostMapping("/cancel")
    @BusinessType("出库申请单-取消")
    public InvokeResult cancel(@RequestBody  @Validated  List<Long> ids) {
        outboundApplicantService.cancel(ids);
        return InvokeResult.success();
    }

    @PostMapping("/finish")
    @BusinessType("出库申请单-完成")
    public InvokeResult finish(@RequestBody  @Validated  List<Long> ids) {
        outboundApplicantService.finish(ids);
        return InvokeResult.success();
    }

    @PostMapping("/issued")
    @BusinessType("出库申请单-下达")
    public InvokeResult issued(@RequestBody  @Validated List<Long> ids) {
        outboundApplicantService.issued(ids);
        return InvokeResult.success();
    }


}
