package com.chinajey.dwork.modules.inventoryTransferApplicant.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.inventoryTransferApplicant.service.InventoryTransferApplicantService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
* 库存转储申请单
 *
 */
@RestController
@RequestMapping("/internal/inventory-transfer-applicant")
public class InventoryTransferApplicantController {

    @Resource
    private InventoryTransferApplicantService inventoryTransferApplicantService;

    @PostMapping()
    @BusinessType("库存转储申请单-新增")
    public InvokeResult save(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(inventoryTransferApplicantService.save(jsonObject));
    }

    @PutMapping()
    @BusinessType("库存转储申请单-更新")
    public InvokeResult update(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(inventoryTransferApplicantService.update(jsonObject));
    }

    @PostMapping("/issued")
    @BusinessType("库存转储申请单-下达")
    public InvokeResult issued(@RequestBody @Validated List<Long> ids) {
        inventoryTransferApplicantService.issued(ids);
        return InvokeResult.success();
    }

    @PostMapping("/close")
    @BusinessType("库存转储申请单-关闭")
    public InvokeResult close(@RequestBody @Validated List<Long> ids) {
        inventoryTransferApplicantService.close(ids);
        return InvokeResult.success();
    }

    @PostMapping("/cancel")
    @BusinessType("库存转储申请单-取消")
    public InvokeResult cancel(@RequestBody @Validated List<Long> ids) {
        inventoryTransferApplicantService.cancel(ids);
        return InvokeResult.success();
    }

    @PostMapping("/finish")
    @BusinessType("库存转储申请单-完成")
    public InvokeResult finish(@RequestBody @Validated List<Long> ids) {
        inventoryTransferApplicantService.finish(ids);
        return InvokeResult.success();
    }
}