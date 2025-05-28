package com.chinajey.dwork.common.form;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class Relation {

    @NotBlank(message = "对象关联类型不能为空")
    private String type;

    @NotBlank(message = "对象关联编码不能为空")
    private String code;

    private String remark;
}