package com.chinajey.dwork.modules.standar_interface.product_order.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.product_order.form.ExternalProductOrderForm;
import com.chinajey.dwork.modules.standar_interface.product_order.service.ExternalProductOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 同步生产订单
 */
@RestController
@RequestMapping("/external/product-order")
public class ExternalProductOrderController {

    @Resource
    private ExternalProductOrderService externalProductOrderService;

    @PostMapping
    @BusinessType("同步生产订单")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalProductOrderForm form) {
        BmfObject bmfObject = this.externalProductOrderService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
