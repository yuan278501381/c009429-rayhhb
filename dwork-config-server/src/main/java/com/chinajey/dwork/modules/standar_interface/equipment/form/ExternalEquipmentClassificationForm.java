package com.chinajey.dwork.modules.standar_interface.equipment.form;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

/**
 * @description:设备类别实体类
 * @author: ZSL
 * @date: 2025/4/18 17:03
 */
@Getter
@Setter
public class ExternalEquipmentClassificationForm extends CodeForm {

    @NotBlank(message = "设备类别名称不能为空")
    private String name;

    private String parent;

    private String externalDocumentCode;
}
