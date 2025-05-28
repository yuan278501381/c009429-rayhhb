package com.chinajey.dwork.common;

/**
 * 返回状态码
 */
public interface HttpStatus {

    /**
     * 操作成功
     */
    public static final int SUCCESS = 10000;

    /**
     * 操作失败
     */
    public static final int ERROR = 10001;

    /**
     * token 无效
     */
    public static final int TOKEN_INVALID = 10002;


    /**
     * 系统错误
     */
    public static final int SYS_ERROR = 20001;


}
