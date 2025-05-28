package com.chinajey.dwork.modules.standar_interface.material.form;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class ExternalMaterialClassificationForm extends CodeForm {

    @NotBlank(message = "物料类别名称不能为空")
    private String name;

    private String parent;

}
