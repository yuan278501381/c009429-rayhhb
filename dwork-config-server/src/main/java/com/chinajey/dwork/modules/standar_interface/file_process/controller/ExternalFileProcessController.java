package com.chinajey.dwork.modules.standar_interface.file_process.controller;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.file_process.form.ExternalFileProcessForm;
import com.chinajey.dwork.modules.standar_interface.file_process.service.ExternalFileProcessService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 同步工艺文件
 */
@RestController
@RequestMapping("/external/file-process")
public class ExternalFileProcessController {

    @Resource
    private ExternalFileProcessService externalFileProcessService;

    @PostMapping
    @BusinessType("同步工艺文件")
    public InvokeResult saveOrUpdate(@RequestBody @Validated ExternalFileProcessForm form) {
        BmfObject bmfObject = this.externalFileProcessService.saveOrUpdate(form);
        return InvokeResult.success(bmfObject);
    }
}
