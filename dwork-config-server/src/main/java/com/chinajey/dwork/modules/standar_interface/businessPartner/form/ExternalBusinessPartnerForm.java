package com.chinajey.dwork.modules.standar_interface.businessPartner.form;

import com.chinajey.dwork.common.form.CodeForm;
import com.chinajey.dwork.common.form.Relation;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @description:业务伙伴实体类
 * @author: ZSL
 * @date: 2025/4/18 17:03
 */
@Getter
@Setter
public class ExternalBusinessPartnerForm extends CodeForm {
    @NotBlank(message = "业务伙伴名称不能为空!")
    private String name;
    @NotBlank(message = "业务伙伴类型不能为空!")
    private String type;
    private Boolean status = Boolean.TRUE;
    private String contact;
    private String contactPhone;
    @NotBlank(message = "业务伙伴组编号不能为空!")
    private String businessPartnerGroupCode;
    private String remark;
    /**
     * 绑定关系
     */
    @Valid
    private List<Relation> relations;
}
