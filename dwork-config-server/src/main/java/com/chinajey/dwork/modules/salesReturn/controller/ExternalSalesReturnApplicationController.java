package com.chinajey.dwork.modules.salesReturn.controller;


import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.salesReturn.service.ExternalSalesReturnApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 外部同步-销售退货申请单
 */
@RestController
@RequestMapping("/external/sales-return-applicant")
public class ExternalSalesReturnApplicationController {
    @Resource
    private ExternalSalesReturnApplicationService externalSalesReturnApplicationService;

    @PostMapping("/saveOrUpdate")
    @BusinessType("销售退货申请单-同步")
    public InvokeResult saveOrUpdate(@RequestBody JSONObject jsonObject) {
        externalSalesReturnApplicationService.saveOrUpdate(jsonObject);
        return InvokeResult.success();
    }
}
