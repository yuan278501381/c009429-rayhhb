package com.chinajey.dwork.common.annotation;

import com.chinajey.dwork.common.utils.BmfEnumUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

public class CodeDictValidator implements ConstraintValidator<CodeDict, Object> {

    private String name;

    @Override
    public void initialize(CodeDict codeDict) {
        this.name = codeDict.value();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return BmfEnumUtils.validateBmfEnumValue(this.name, String.valueOf(value));
        }
        if (value instanceof List<?>) {
            for (Object o : (List<?>) value) {
                if (o instanceof String) {
                    if (!BmfEnumUtils.validateBmfEnumValue(this.name, String.valueOf(o))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
