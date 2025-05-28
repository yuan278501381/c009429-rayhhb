package com.chinajey.dwork.modules.standar_interface.warehouse.form;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
public class ExternalWarehouseCategoryForm extends CodeForm {

    @NotBlank(message = "仓库类别名称不能为空")
    private String name;

    private Boolean status = Boolean.TRUE;

    private String remark;
}
