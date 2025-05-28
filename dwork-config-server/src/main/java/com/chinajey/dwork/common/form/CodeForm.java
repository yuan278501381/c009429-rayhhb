package com.chinajey.dwork.common.form;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class CodeForm extends ExtForm {

    @NotBlank(message = "来源编码不能为空")
    private String code;
}
