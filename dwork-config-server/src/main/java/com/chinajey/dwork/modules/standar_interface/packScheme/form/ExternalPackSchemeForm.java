package com.chinajey.dwork.modules.standar_interface.packScheme.form;

import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.annotation.CodeDict;
import com.chinajey.dwork.common.enums.PackageTypeEnum;
import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * @author erton.bi
 */
@Getter
@Setter
public class ExternalPackSchemeForm extends CodeForm {

    @NotBlank(message = "包装方案名称不能为空")
    private String name;

    @NotBlank(message = "包装物类型不能为空")
    @CodeDict(value = "packageType", message = "包装物类型错误")
    private String packageType;

    private String materialCode;

    private String materialClassificationCode;

    @PositiveOrZero(message = "下级数量必须是正整数数或0")
    private Integer lowQuantity = 0;

    private Boolean status = Boolean.TRUE;

    private String unitName;

    private String fileProcessCode;

    private String lowerPackSchemeCode;

    private Boolean defaultStatus = Boolean.TRUE;

    private String remark;

    private List<BarCodeRuleForm> barCodeRules;

    public void valid(){
        if (PackageTypeEnum.MATERIAL.getCode().equals(this.packageType)){
            this.materialClassificationCode = null;
            if (StringUtils.isBlank(this.materialCode)){
                throw new BusinessException("包装物(物料)编码不能为空");
            }
        }else if (PackageTypeEnum.MATERIAL_CLASSIFICATION.getCode().equals(this.packageType)){
            this.materialCode = null;
            if (StringUtils.isBlank(this.materialClassificationCode)){
                throw new BusinessException("包装物类别(物料类别)编码不能为空");
            }
        }else {
            throw new BusinessException("包装物类型错误");
        }
    }
}
