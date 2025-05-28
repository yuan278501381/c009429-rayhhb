package com.chinajey.dwork.common.enums;

import java.util.Arrays;

/**
 * 单据业务类型
 */
public enum OrderBusinessTypeEnum {

    XXXX("xxx", "xxx");

    private final String code;

    private final String name;


    OrderBusinessTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
