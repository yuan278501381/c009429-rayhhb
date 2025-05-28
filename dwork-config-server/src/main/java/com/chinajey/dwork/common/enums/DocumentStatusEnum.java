package com.chinajey.dwork.common.enums;

import java.util.Arrays;

/**
 * 单据状态
 */
public enum DocumentStatusEnum {
    NOT_RECEIVE("notReceive", "待收货"),
    PART_RECEIVE("partReceive", "部分收货"),
    CLOSED("closed", "已关闭"),
    CREATE("create", "已创建"),
    TO_BE_REVIEWED("toBeReviewed", "待评审"),
    PARTIAL_REVIEW("partialReview", "部分评审"),
    REVIEWED("reviewed", "已评审"),
    NOT_CLEAR("notClear", "未清"),
    SETTLEMENT("settlement", "结算"),
    CANCEL("cancel", "已取消"),
    UNFINISHED("unfinished", "未完成"),
    UNCONFIRMED("unconfirmed", "未确认"),
    CONFIRMED("confirmed", "已确认"),
    SCHEDULED("scheduled", "已排产"),
    UN_SCHEDULED("unScheduled", "未排产"),
    TO_CONFIRMED("toConfirmed", "待确认"),
    UNTREATED("untreated", "未处理"),
    PENDING("pending", "待处理"),
    PARTIAL("partial", "部分处理"),
    PARTIAL_OUT_WAREHOUSE("partialOutWarehouse", "部分出库"),
    COMPLETED("completed", "已完成");

    private final String code;

    private final String name;


    DocumentStatusEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static DocumentStatusEnum getEnum(String code) {
        for (DocumentStatusEnum documentStatusEnum : DocumentStatusEnum.values()) {
            if (documentStatusEnum.getCode().equals(code)) {
                return documentStatusEnum;
            }
        }
        return DocumentStatusEnum.UNTREATED;
    }


    /**
     * 是否可以下达
     * 待处理
     *
     * @param status 状态
     */
    public static boolean whetherIssued(String status) {
        return DocumentStatusEnum.UNTREATED.getCode().equals(status);
    }

    /**
     * 是否可以编辑
     * 未处理 已取消
     *
     * @param status 状态
     */
    public static boolean whetherUpdate(String status) {
        return Arrays.asList(DocumentStatusEnum.UNTREATED.getCode(), DocumentStatusEnum.CANCEL.getCode()).contains(status);
    }


    /**
     * 是否可以删除
     * 未处理 已取消 已关闭
     *
     * @param status 状态
     */
    public static boolean whetherDelete(String status) {
        return Arrays.asList(DocumentStatusEnum.UNTREATED.getCode(), DocumentStatusEnum.CANCEL.getCode(), DocumentStatusEnum.CLOSED.getCode()).contains(status);
    }

    /**
     * 是否可以关闭
     * 待处理 部分处理
     *
     * @param status 状态
     */
    public static boolean whetherClose(String status) {
        //待处理
        return Arrays.asList(DocumentStatusEnum.PENDING.getCode(), DocumentStatusEnum.PARTIAL.getCode()).contains(status);
    }

    /**
     * 是否可以取消
     * 待处理
     *
     * @param status 状态
     */
    public static boolean whetherCancel(String status) {
        return DocumentStatusEnum.PENDING.getCode().equals(status);
    }


    /**
     * 是否可以完成
     * 部分处理
     *
     * @param status 状态
     */
    public static boolean whetherComplete(String status) {
        return DocumentStatusEnum.PARTIAL.getCode().equals(status);
    }
}
