package com.chinajey.dwork.modules.exceptionTransferPassBox.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.exceptionTransferPassBox.service.ExceptionPassBoxService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * @description: 异常转出周转箱
 * @author: ZSL
 * @date: 2025.05.26 11:16
 */
@RestController
@RequestMapping("/internal/exception-transfer-pass-box")
public class ExceptionPassBoxController {
    @Resource
    private ExceptionPassBoxService exceptionPassBoxService;
    @PostMapping("/submit")
    @BusinessType("异常转出周转箱-提交")
    public InvokeResult submit(@RequestBody @Validated JSONObject jsonObject) {
        exceptionPassBoxService.submit(jsonObject);
        return InvokeResult.success();
    }

    @PostMapping("/remove")
    @BusinessType("异常转出周转箱-移除")
    public InvokeResult remove(@RequestBody @Validated List<Long> ids) {
        exceptionPassBoxService.remove(ids);
        return InvokeResult.success();
    }
}
