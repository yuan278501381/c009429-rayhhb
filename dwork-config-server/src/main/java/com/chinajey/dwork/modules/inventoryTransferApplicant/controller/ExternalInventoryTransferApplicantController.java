package com.chinajey.dwork.modules.inventoryTransferApplicant.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.inventoryTransferApplicant.service.ExternalInventoryTransferApplicantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
* 外部同步接口-库存转储申请单
 *
 */
@RestController
@RequestMapping("/external/inventory-transfer-applicant")
public class ExternalInventoryTransferApplicantController {

    @Resource
    private ExternalInventoryTransferApplicantService externalInventoryTransferApplicantService;

    @PostMapping("/saveOrUpdate")
    @BusinessType("库存转储申请单-同步")
    public InvokeResult saveOrUpdate(@RequestBody JSONObject jsonObject) {
        externalInventoryTransferApplicantService.saveOrUpdate(jsonObject);
        return InvokeResult.success();
    }
}