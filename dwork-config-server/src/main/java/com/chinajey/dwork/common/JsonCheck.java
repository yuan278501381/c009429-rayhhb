package com.chinajey.dwork.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author 陈阳
 * @Date 2023/3/9 9:07
 * @Version 1.0
 * 是否检查 判空
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonCheck {
    //前端名称
    String value() ;
}
