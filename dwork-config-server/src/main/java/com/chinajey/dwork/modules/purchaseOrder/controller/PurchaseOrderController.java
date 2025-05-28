package com.chinajey.dwork.modules.purchaseOrder.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.purchaseOrder.service.PurchaseOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/internal/purchase-order")
public class PurchaseOrderController {
    @Resource
    private PurchaseOrderService purchaseOrderService;

    @PostMapping()
    @BusinessType("采购订单-新增")
    public InvokeResult save(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(purchaseOrderService.save(jsonObject));
    }

    @PutMapping()
    @BusinessType("采购订单-更新")
    public InvokeResult update(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(purchaseOrderService.update(jsonObject));
    }

    @GetMapping("/releaseList/{id}")
    @BusinessType("采购订单-下达明细查询")
    public InvokeResult releaseList(@PathVariable("id") Long id) {
        BmfObject detail = purchaseOrderService.releaseList(id);
        return InvokeResult.success(detail);
    }

    @PostMapping("/issued")
    @BusinessType("采购订单-下达")
    public InvokeResult issued(@RequestBody JSONObject jsonObject) {
        purchaseOrderService.issued(jsonObject);
        return InvokeResult.success();
    }

    @PostMapping("/close")
    @BusinessType("采购订单-关闭")
    public InvokeResult close(@RequestBody @Validated List<Long> ids) {
        purchaseOrderService.close(ids);
        return InvokeResult.success();
    }

    @PostMapping("/cancel")
    @BusinessType("采购订单-取消")
    public InvokeResult cancel(@RequestBody @Validated List<Long> ids) {
        purchaseOrderService.cancel(ids);
        return InvokeResult.success();
    }

    @PostMapping("/finish")
    @BusinessType("采购订单-完成")
    public InvokeResult finish(@RequestBody @Validated List<Long> ids) {
        purchaseOrderService.finish(ids);
        return InvokeResult.success();
    }

}
