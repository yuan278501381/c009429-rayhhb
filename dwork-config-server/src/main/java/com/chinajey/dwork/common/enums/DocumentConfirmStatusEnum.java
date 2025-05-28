package com.chinajey.dwork.common.enums;

import java.util.Arrays;

/**
 * @author angel.su
 * createTime 2025/4/29 15:02
 */
public enum DocumentConfirmStatusEnum {
    UNCONFIRMED("unConfirmed", "未确认"),
    PARTIAL_CONFIRMED("partialConfirmed", "部分确认"),
    CONFIRMED("confirmed", "已确认");

    private final String code;

    private final String name;

    DocumentConfirmStatusEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static DocumentConfirmStatusEnum getEnum(String code) {
        for (DocumentConfirmStatusEnum documentConfirmStatusEnum : DocumentConfirmStatusEnum.values()) {
            if (documentConfirmStatusEnum.getCode().equals(code)) {
                return documentConfirmStatusEnum;
            }
        }
        return DocumentConfirmStatusEnum.UNCONFIRMED;
    }

    /**
     * 是否可以确认
     * 未确认 部分确认
     *
     * @param status 状态
     */
    public static boolean whetherConfirm(String status) {
        return Arrays.asList(DocumentConfirmStatusEnum.UNCONFIRMED.getCode(), DocumentConfirmStatusEnum.PARTIAL_CONFIRMED.getCode()).contains(status);
    }
}
