package com.chinajey.dwork.modules.purchaseOrder.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.purchaseOrder.form.PurchaseOrderForm;
import com.chinajey.dwork.modules.purchaseOrder.service.ExternalPurchaseOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

/**
 * 外部同步接口-采购订单
 * @author angel.su
 */
@RestController
@RequestMapping("/external/purchase-order")
public class ExternalPurchaseOrderController {
    @Resource
    private ExternalPurchaseOrderService externalPurchaseOrderService;

    @PostMapping("/saveOrUpdate")
    @BusinessType("采购订单-同步")
    public InvokeResult saveOrUpdate(@RequestBody @Validated PurchaseOrderForm form) {
        externalPurchaseOrderService.saveOrUpdate(form);
        return InvokeResult.success();
    }
}
