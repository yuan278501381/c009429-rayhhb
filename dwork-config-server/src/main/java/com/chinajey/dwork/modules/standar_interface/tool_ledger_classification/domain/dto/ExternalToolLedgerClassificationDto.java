package com.chinajey.dwork.modules.standar_interface.tool_ledger_classification.domain.dto;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ExternalToolLedgerClassificationDto extends CodeForm {

    private Long id;

    private String bmfClassName;

    private String descriptionName;

    @NotBlank(message = "类别名称不能为空")
    private String name;

    private String sort;

    private String parentExternalCode;

}
