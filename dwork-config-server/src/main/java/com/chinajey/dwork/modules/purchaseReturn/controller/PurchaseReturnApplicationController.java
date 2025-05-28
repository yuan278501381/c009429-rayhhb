package com.chinajey.dwork.modules.purchaseReturn.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.purchaseReturn.service.PurchaseReturnApplicationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;

/**
* 采购退货申请单
 * @author erton.bi
 */
@RestController
@RequestMapping("/internal/purchase-return-application")
public class PurchaseReturnApplicationController {

    @Resource
    private PurchaseReturnApplicationService purchaseReturnApplicationService;

    @PostMapping()
    @BusinessType("采购退货申请单-新增")
    public InvokeResult save(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(purchaseReturnApplicationService.save(jsonObject));
    }

    @PutMapping()
    @BusinessType("采购退货申请单-更新")
    public InvokeResult update(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(purchaseReturnApplicationService.update(jsonObject));
    }

    @PostMapping("/issued")
    @BusinessType("采购退货申请单-下达")
    public InvokeResult issued(@RequestBody @Validated List<Long> ids) {
        purchaseReturnApplicationService.issued(ids);
        return InvokeResult.success();
    }

    @PostMapping("/close")
    @BusinessType("采购退货申请单-关闭")
    public InvokeResult close(@RequestBody @Validated List<Long> ids) {
        purchaseReturnApplicationService.close(ids);
        return InvokeResult.success();
    }

    @PostMapping("/cancel")
    @BusinessType("采购退货申请单-取消")
    public InvokeResult cancel(@RequestBody @Validated List<Long> ids) {
        purchaseReturnApplicationService.cancel(ids);
        return InvokeResult.success();
    }

    @PostMapping("/finish")
    @BusinessType("采购退货申请单-完成")
    public InvokeResult finish(@RequestBody @Validated List<Long> ids) {
        purchaseReturnApplicationService.finish(ids);
        return InvokeResult.success();
    }
}