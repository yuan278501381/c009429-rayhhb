package com.chinajey.dwork.modules.standar_interface.role.form;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class ExternalRoleForm extends CodeForm {

    @NotBlank(message = "角色名称不能为空")
    private String name;

    private String description;
}
