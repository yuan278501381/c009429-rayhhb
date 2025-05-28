package com.chinajey.dwork.modules.salesDelivery.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.salesDelivery.form.DeliveryPlanForm;
import com.chinajey.dwork.modules.salesDelivery.service.ExternalSalesDeliveryPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 销售发货计划 - 同步
 */
@RestController
@RequestMapping("/external/sales-delivery-plan")
public class ExternalSalesDeliveryPlanController {

    @Resource
    private ExternalSalesDeliveryPlanService externalSalesDeliveryPlanService;

    @PostMapping("/saveOrUpdate")
    public InvokeResult saveOrUpdate(@RequestBody @Validated DeliveryPlanForm form) {
        BmfObject bmfObject = this.externalSalesDeliveryPlanService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
