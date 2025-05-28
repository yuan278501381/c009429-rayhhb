package com.chinajey.dwork.common.enums;

/**
 * 单据类型
 * 外部单据类型、内部单据类型、上级内部单据类型
 */
public enum DocumentTypeEnum {

    PRODUCTION_STOCK("productionStock", "生产备料"),
    OUTSOURCE_ORDER("outsourceOrder", "委外订单"),

    PRODUCT_REWORK_MATERIAL_REQUISITION("productMaterialRequisition", "生产补料");

    private final String code;

    private final String name;


    DocumentTypeEnum(String code, String name) {
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
