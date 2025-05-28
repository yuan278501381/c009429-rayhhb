package com.chinajey.dwork.modules.purchaseReturn.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.purchaseReturn.service.ExternalPurchaseReturnApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;

/**
* 外部同步接口-采购退货申请单
 * @author erton.bi
 */
@RestController
@RequestMapping("/external/purchase-return-application")
public class ExternalPurchaseReturnApplicationController {

    @Resource
    private ExternalPurchaseReturnApplicationService externalPurchaseReturnApplicationService;

    @PostMapping("/saveOrUpdate")
    @BusinessType("采购退货申请单-同步")
    public InvokeResult saveOrUpdate(@RequestBody JSONObject jsonObject) {
        externalPurchaseReturnApplicationService.saveOrUpdate(jsonObject);
        return InvokeResult.success();
    }
}