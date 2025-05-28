package com.chinajey.dwork.modules.standar_interface.packScheme.form;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BarCodeRuleForm {

    private String codeType;

    private String codeSource;

    private String codeAttribute;

    private String value;

    private String format;

    private Integer length;

    private Long initialValue;

    private Integer step;

    private String compCharacter;

    private Boolean serialNumBasis;

    private String zeroBasis;

    private String timeType;
}
