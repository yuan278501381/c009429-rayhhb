package com.chinajey.dwork.common.annotation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Constraint(validatedBy = {CodeDictValidator.class})
public @interface CodeDict {

    String value() default "";

    String message() default "数据字典值错误";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
