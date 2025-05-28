package com.chinajey.dwork.common.utils;

public class ValueUtils {

    public static boolean getBoolean(Boolean value) {
        return getBoolean(value, true);
    }

    /**
     * 获取Boolean值，如果为空则返回默认值
     *
     * @param value        Boolean值
     * @param defaultValue 默认值
     */
    public static boolean getBoolean(Boolean value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Boolean.TRUE.equals(value);
    }
}
