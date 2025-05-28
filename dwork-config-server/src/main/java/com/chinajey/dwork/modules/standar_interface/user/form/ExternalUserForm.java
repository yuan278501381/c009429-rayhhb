package com.chinajey.dwork.modules.standar_interface.user.form;

import com.chinajey.dwork.common.annotation.CodeDict;
import com.chinajey.dwork.common.form.CodeForm;
import com.chinajey.dwork.common.form.Relation;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Setter
@Getter
public class ExternalUserForm extends CodeForm {

    @NotBlank(message = "员工名称不能为空")
    private String name;

    private String mobile;

    private String jobNumber;

    @CodeDict(value = "gender", message = "员工性别值错误")
    private String gender;

    private String managerCode;

    private Boolean onSeat = Boolean.TRUE;

    private Boolean status = Boolean.TRUE;

    @NotEmpty(message = "岗位不能为空")
    @CodeDict(value = "postType", message = "员工岗位值错误")
    private List<String> posts;

    @NotEmpty(message = "部门不能为空")
    private List<String> departments;

    private List<String> roles;

    /**
     * 对象关联 - 工种
     */
    @Valid
    private List<Relation> relations;
}
