package com.chinajey.dwork.modules.standar_interface.warehouse.form;

import com.chinajey.dwork.common.form.CodeForm;
import com.chinajey.dwork.common.form.Relation;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Getter
@Setter
public class ExternalWarehouseForm extends CodeForm {

    @NotBlank(message = "仓库名称不能为空")
    private String name;

    @NotBlank(message = "仓库类别编码不能为空")
    private String categoryCode;

    /**
     * 仓管员编码
     */
    private String keeperCode;

    private Boolean status = Boolean.TRUE;

    private String remark;

    /**
     * 对象关联
     */
    @Valid
    private List<Relation> relations;
}
