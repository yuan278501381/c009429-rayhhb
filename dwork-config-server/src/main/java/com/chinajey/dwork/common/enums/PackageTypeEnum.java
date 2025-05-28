package com.chinajey.dwork.common.enums;

/**
 * 包装物类型枚举
 *
 * @author erton.bi
 */
public enum PackageTypeEnum {
    MATERIAL("material", "物料"),
    MATERIAL_CLASSIFICATION("materialClassification", "物料类别");

    private final String code;
    private final String name;

    PackageTypeEnum(String code, String name) {
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
