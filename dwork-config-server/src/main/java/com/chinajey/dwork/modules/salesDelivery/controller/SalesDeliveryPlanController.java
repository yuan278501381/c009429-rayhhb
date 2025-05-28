package com.chinajey.dwork.modules.salesDelivery.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.salesDelivery.service.SalesDeliveryPlanService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 销售发货计划
 */
@RestController
@RequestMapping("/internal/sales-delivery-plan")
public class SalesDeliveryPlanController {

    @Resource
    private SalesDeliveryPlanService salesDeliveryPlanService;

    /**
     * 创建销售发货计划
     */
    @PostMapping
    public InvokeResult create(@RequestBody JSONObject jsonObject) {
        BmfObject bmfObject = this.salesDeliveryPlanService.create(jsonObject);
        return InvokeResult.success(bmfObject);
    }

    /**
     * 修改销售发货计划
     */
    @PutMapping
    public InvokeResult update(@RequestBody JSONObject jsonObject) {
        BmfObject bmfObject = this.salesDeliveryPlanService.update(jsonObject);
        return InvokeResult.success(bmfObject);
    }

    /**
     * 下达销售发货计划
     */
    @PostMapping("/issued")
    public InvokeResult issued(@RequestBody List<Long> ids) {
        this.validate(ids);
        this.salesDeliveryPlanService.issued(ids);
        return InvokeResult.success();
    }

    /**
     * 关闭销售发货计划
     */
    @PostMapping("/close")
    public InvokeResult close(@RequestBody @Validated List<Long> ids) {
        this.validate(ids);
        this.salesDeliveryPlanService.close(ids);
        return InvokeResult.success();
    }

    /**
     * 取消销售发货计划
     */
    @PostMapping("/cancel")
    public InvokeResult cancel(@RequestBody @Validated List<Long> ids) {
        this.validate(ids);
        this.salesDeliveryPlanService.cancel(ids);
        return InvokeResult.success();
    }


    /**
     * 完成销售发货计划
     */
    @PostMapping("/finish")
    public InvokeResult finish(@RequestBody @Validated List<Long> ids) {
        this.validate(ids);
        this.salesDeliveryPlanService.finish(ids);
        return InvokeResult.success();
    }

    private void validate(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("销售发货计划单id不能为空");
        }
    }
}
