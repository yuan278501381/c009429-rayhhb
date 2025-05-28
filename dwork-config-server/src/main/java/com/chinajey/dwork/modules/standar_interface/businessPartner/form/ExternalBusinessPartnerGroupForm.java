package com.chinajey.dwork.modules.standar_interface.businessPartner.form;

import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

/**
 * @description:业务伙伴组实体类
 * @author: ZSL
 * @date: 2025/4/18 17:03
 */
@Getter
@Setter
public class ExternalBusinessPartnerGroupForm extends CodeForm {

    @NotBlank(message = "业务伙伴组名称不能为空")
    private String name;

    private String parent;

    private String externalDocumentCode;

}
