package com.chinajey.dwork.modules.standar_interface.department.form;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

/**
 * @description:部门实体类
 * @author: ZSL
 * @date: 2025/4/18 17:03
 */
@Getter
@Setter
public class ExternalDepartmentForm extends CodeForm {

    @NotBlank(message = "部门名称不能为空")
    private String name;

    private String phone;

    private String facsimile;

    private String remark;

    private String managerCode;

    private String deputyManagerCode;

    private String parentCode;

}
