package com.chinajey.dwork.modules.commonInterface.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.annotation.NoRepeatSubmit;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.application.common.resp.InvokeResultData;
import com.chinajey.dwork.modules.commonInterface.from.MaintenanceApplySyncForm;
import com.chinajey.dwork.modules.commonInterface.service.CommonInterfaceService;
import com.chinajey.dwork.modules.commonInterface.service.DiscardService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @description 二开配置通用接口
 */
@RestController
@RequestMapping("/ek")
public class CommonInterface {

    @Resource
    CommonInterfaceService commonInterfaceService;
    @Resource
    DiscardService discardService;
    @GetMapping("/getSubPageV1")
    public InvokeResult getSubQuery(@RequestParam Long id,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String bmfClassItemName,
                                    @RequestParam(required = false) String attributeName,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String bmfClassMainId) {
        return InvokeResult.success(commonInterfaceService.findItemPageV1(id, page, size,bmfClassItemName,attributeName, keyword,bmfClassMainId));
    }

    /**
     * 二开通用分页
     */
    @PostMapping("/getSubPage")
    public InvokeResult getSubQuery(@RequestBody JSONObject jsonObject) {
        return InvokeResult.success(commonInterfaceService.findItemPage(jsonObject));
    }



    /**
     * 报废申请推送OA(pc原生)
     */
    @PostMapping("/free-api/push")
    @BusinessType("推送")
    @NoRepeatSubmit
    public InvokeResult push(@RequestBody JSONObject jsonObject) {
        this.commonInterfaceService.push(jsonObject);
        return InvokeResult.success();
    }

    /**
     * 报废申请推送OA(pc原生)
     */
    @PostMapping("/free-api/batchPush")
    @BusinessType("批量推送")
    public InvokeResult batchPush(@RequestBody List<Long> ids) {
        this.commonInterfaceService.batchPush(ids);
        return InvokeResultData.success();
    }

    /**
     * 同步工器具维修申请单主数据
     */
    @PostMapping("/maintenanceApplySync")
    public InvokeResult maintenanceApplySync(@RequestBody @Validated MaintenanceApplySyncForm form) {
        return this.commonInterfaceService.maintenanceApplySync(form);
    }



    @GetMapping("/internal/discardAsUselessCauseGroup")
    @BusinessType("获取报废原因分类")
    public InvokeResult getResponsibleUser(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size,
                                           @RequestParam(required = false) String keyword) {
        return InvokeResultData.success(this.discardService.getDiscardAsUselessCauseGroup(page,size,keyword));
    }

    @GetMapping("/internal/discardAsUselessCause")
    @BusinessType("获取报废原因信息")
    public InvokeResult getDiscardAsUselessCause(@RequestParam(required = false) String classCode,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) String keyword) {
        return InvokeResultData.success(this.discardService.getDiscardAsUselessCause(classCode,page,size,keyword));
    }

    @GetMapping("/internal/workGroup")
    @BusinessType("获取报废原因分类")
    public InvokeResult getWorkGroup(@RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size,
                                     @RequestParam(required = false) String keyword) {
        return InvokeResultData.success(this.discardService.getWorkGroup(page,size,keyword));
    }
}
