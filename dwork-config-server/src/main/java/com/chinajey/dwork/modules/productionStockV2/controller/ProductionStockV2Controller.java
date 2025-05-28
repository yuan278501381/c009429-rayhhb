package com.chinajey.dwork.modules.productionStockV2.controller;

import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.productionStockV2.service.ProductionStockV2Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/internal/productionStock")
public class ProductionStockV2Controller {
    @Resource
    private ProductionStockV2Service productionStockV2Service;

    @PostMapping("/issued")
    @BusinessType("生产拣配单-下达")
    public InvokeResult issued(@RequestBody @Validated List<Long> ids) {
        productionStockV2Service.issued(ids);
        return InvokeResult.success();
    }

    @PostMapping("/close")
    @BusinessType("生产拣配单-关闭")
    public InvokeResult close(@RequestBody @Validated List<Long> ids) {
        productionStockV2Service.close(ids);
        return InvokeResult.success();
    }

    @PostMapping("/cancel")
    @BusinessType("生产拣配单-取消")
    public InvokeResult cancel(@RequestBody @Validated List<Long> ids) {
        productionStockV2Service.cancel(ids);
        return InvokeResult.success();
    }

    @PostMapping("/finish")
    @BusinessType("生产拣配单-完成")
    public InvokeResult finish(@RequestBody @Validated List<Long> ids) {
        productionStockV2Service.finish(ids);
        return InvokeResult.success();
    }
}
