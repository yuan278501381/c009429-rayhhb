package com.chinajey.dwork.common.utils;

import com.chinajay.virgo.bmf.enums.BmfEnum;
import com.chinajay.virgo.bmf.enums.BmfEnumCache;
import com.chinajay.virgo.bmf.enums.BmfEnumItem;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BmfEnumUtils {

    /**
     * 获取BmfEnum的值列表
     *
     * @param name 枚举名称
     */
    public static List<String> getEnumValues(String name) {
        BmfEnum bmfEnum = BmfEnumCache.getBmfEnum(name);
        if (bmfEnum == null) {
            return new ArrayList<>();
        }
        List<BmfEnumItem> bmfEnumItems = bmfEnum.getBmfEnumItems();
        return bmfEnumItems.stream().map(BmfEnumItem::getValue).collect(Collectors.toList());
    }

    /**
     * 校验枚举的值是否合法
     *
     * @param name  枚举名称
     * @param value 要校验的枚举值
     */
    public static boolean validateBmfEnumValue(String name, String value) {
        if (StringUtils.isBlank(value)) {
            return true;
        }
        return BmfEnumUtils.getEnumValues(name).contains(value);
    }
}
