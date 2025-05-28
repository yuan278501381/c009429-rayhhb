package com.chinajey.dwork.modules.warehousingApplicant.controller;


import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.warehousingApplicant.service.WarehousingTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/internal/warehousingTask")
public class WarehousingTaskController {
    @Resource
    private WarehousingTaskService warehousingTaskService;

    @PostMapping("/cancel")
    @BusinessType("入库任务-取消")
    public InvokeResult cancel(@RequestBody  @Validated  List<Long> ids) {
        warehousingTaskService.cancel(ids);
        return InvokeResult.success();
    }

}
