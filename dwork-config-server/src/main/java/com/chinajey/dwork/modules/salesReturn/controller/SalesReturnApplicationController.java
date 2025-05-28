package com.chinajey.dwork.modules.salesReturn.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.salesReturn.service.SalesReturnApplicationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 销售退货申请单
 */
@RestController
@RequestMapping("/internal/sales-return-applicant")
public class SalesReturnApplicationController {

    @Resource
    private SalesReturnApplicationService salesReturnApplicationService;

    @PostMapping()
    @BusinessType("销售退货申请单-新增")
    public InvokeResult save(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(salesReturnApplicationService.save(jsonObject));
    }

    @PutMapping()
    @BusinessType("销售退货申请单-更新")
    public InvokeResult update(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(salesReturnApplicationService.update(jsonObject));
    }

    @PostMapping("/issued")
    @BusinessType("销售退货申请单-下达")
    public InvokeResult issued(@RequestBody @Validated List<Long> ids) {
        salesReturnApplicationService.issued(ids);
        return InvokeResult.success();
    }

    @PostMapping("/close")
    @BusinessType("销售退货申请单-关闭")
    public InvokeResult close(@RequestBody @Validated List<Long> ids) {
        salesReturnApplicationService.close(ids);
        return InvokeResult.success();
    }

    @PostMapping("/cancel")
    @BusinessType("销售退货申请单-取消")
    public InvokeResult cancel(@RequestBody @Validated List<Long> ids) {
        salesReturnApplicationService.cancel(ids);
        return InvokeResult.success();
    }

    @PostMapping("/finish")
    @BusinessType("销售退货申请单-完成")
    public InvokeResult finish(@RequestBody @Validated List<Long> ids) {
        salesReturnApplicationService.finish(ids);
        return InvokeResult.success();
    }

}
