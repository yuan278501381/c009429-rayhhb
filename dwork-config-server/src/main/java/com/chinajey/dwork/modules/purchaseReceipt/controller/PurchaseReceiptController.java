package com.chinajey.dwork.modules.purchaseReceipt.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.purchaseReceipt.service.PurchaseReceiptService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author angel.su
 * createTime 2025/3/19 18:27
 */
@RestController
@RequestMapping("/internal/purchase-receipt")
public class PurchaseReceiptController {
    @Resource
    private PurchaseReceiptService purchaseReceiptService;

//    @PutMapping()
//    @BusinessType("采购收货计划-更新")
//    public InvokeResult update(@RequestBody JSONObject jsonObject) {
//        return InvokeResult.success(purchaseReceiptService.update(jsonObject));
//    }
//
//    @DeleteMapping()
//    @BusinessType("采购收货计划-删除")
//    public InvokeResult delete(@RequestBody @Validated List<Long> ids) {
//        purchaseReceiptService.delete(ids);
//        return InvokeResult.success();
//    }

    @PostMapping("/issued")
    @BusinessType("采购收货计划-下达")
    public InvokeResult issued(@RequestBody  @Validated List<Long> ids) {
        purchaseReceiptService.issued(ids);
        return InvokeResult.success();
    }

//    @PostMapping("/close")
//    @BusinessType("采购收货计划-关闭")
//    public InvokeResult close(@RequestBody @Validated List<Long> ids) {
//        purchaseReceiptService.close(ids);
//        return InvokeResult.success();
//    }
//
//    @PostMapping("/cancel")
//    @BusinessType("采购收货计划-取消")
//    public InvokeResult cancel(@RequestBody @Validated List<Long> ids) {
//        purchaseReceiptService.cancel(ids);
//        return InvokeResult.success();
//    }
//
//    @PostMapping("/finish")
//    @BusinessType("采购收货计划-完成")
//    public InvokeResult finish(@RequestBody @Validated List<Long> ids) {
//        purchaseReceiptService.finish(ids);
//        return InvokeResult.success();
//    }
}
