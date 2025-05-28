package com.chinajey.dwork.modules.outboundApplicant.controller;


import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.outboundApplicant.service.OutboundTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/internal/outboundTask")
public class OutboundTaskController {
    @Resource
    private OutboundTaskService outboundTaskService;

    @PostMapping("/cancel")
    @BusinessType("出库任务-取消")
    public InvokeResult cancel(@RequestBody  @Validated  List<Long> ids) {
        outboundTaskService.cancel(ids);
        return InvokeResult.success();
    }

}
