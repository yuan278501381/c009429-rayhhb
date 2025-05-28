package com.chinajey.dwork.modules.productionReturnApplicant.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.productionReturnApplicant.service.ProductionReturnApplicantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 生产退料申请单
 *
 * @author angel.su
 * createTime 2025/4/15 19:55
 */
@RestController
@RequestMapping("/internal/production-return-applicant")
public class ProductionReturnApplicantController {
    @Resource
    private ProductionReturnApplicantService productionReturnApplicantService;

    @PostMapping()
    @BusinessType("生产退料申请单-新增")
    public InvokeResult save(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(productionReturnApplicantService.save(jsonObject));
    }

    @PostMapping("/issued")
    @BusinessType("生产退料申请单-下达")
    public InvokeResult issued(@RequestBody @Validated List<Long> ids) {
        productionReturnApplicantService.issued(ids);
        return InvokeResult.success();
    }

    @PostMapping("/close")
    @BusinessType("生产退料申请单-关闭")
    public InvokeResult close(@RequestBody @Validated List<Long> ids) {
        productionReturnApplicantService.close(ids);
        return InvokeResult.success();
    }

    @PostMapping("/cancel")
    @BusinessType("生产退料申请单-取消")
    public InvokeResult cancel(@RequestBody @Validated List<Long> ids) {
        productionReturnApplicantService.cancel(ids);
        return InvokeResult.success();
    }

    @PostMapping("/finish")
    @BusinessType("生产退料申请单-完成")
    public InvokeResult finish(@RequestBody @Validated List<Long> ids) {
        productionReturnApplicantService.finish(ids);
        return InvokeResult.success();
    }

}