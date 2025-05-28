package com.chinajey.dwork.modules.standar_interface.file_process.form;

import com.chinajey.dwork.common.annotation.CodeDict;
import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@Setter
public class ExternalFileProcessForm extends CodeForm {

    @NotBlank(message = "工艺文件名称不能为空")
    private String name;

    @NotBlank(message = "文件链接不能为空")
    @Pattern(regexp = "^(http|https)://.*", message = "文件链接必须是http/https开头")
    private String fileLink;

    @CodeDict(value = "fileType", message = "文件类型值错误")
    private String fileType;

    private Boolean status = Boolean.TRUE;

    private String remark;

    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    private String costCenterCode;

    private String processCode;
}
