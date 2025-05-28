package com.chinajey.dwork.common.enums;

/**
 * 来源系统
 */
public enum SourceSystemEnum {
    DWORK("dwork", "DWORK"),
    K3_CLOUD("k3cloud", "金蝶"),
    SAP("sap", "SAP");

    private final String code;

    private final String name;


    SourceSystemEnum(String code, String name) {
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
