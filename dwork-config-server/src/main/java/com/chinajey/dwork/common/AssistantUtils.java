package com.chinajey.dwork.common;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.exception.BusinessException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AssistantUtils {

    /**
     * 通用分隔符
     */
    public static final String SPLIT = "@_@";

    /**
     * 获取以分割符分割的字符串
     *
     * @param values 需要分割的数据
     * @param split  分割符
     */
    public static String getSplitStrValues(List<String> values, String split) {
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        return StringUtils.join(values.stream().distinct().collect(Collectors.toList()), split);
    }

    /**
     * 获取以分割符分割的字符串
     *
     * @param origins   原分割数据
     * @param newValues 新值
     * @param split     分割符
     */
    public static String getSplitValues(String origins, String newValues, String split) {
        Set<String> originSet = getMultiInfos(origins, split);
        Set<String> newSet = getMultiInfos(newValues, split);
        originSet.addAll(newSet);
        return StringUtils.join(originSet, split);
    }

    private static Set<String> getMultiInfos(String values, String split) {
        Set<String> valueSet = new LinkedHashSet<>();
        if (StringUtils.isBlank(values)) {
            return valueSet;
        }
        String[] valueArray = values.split(split);
        for (String value : valueArray) {
            valueSet.add(value.trim());
        }
        return valueSet;
    }

    public static String getSplitValues(List<BmfObject> bmfObjects, String attribute) {
        String values = bmfObjects
                .stream()
                .map(it -> it.getString(attribute)).filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.joining(","));
        return StringUtils.isNotBlank(values) ? values : null;
    }

    public static BigDecimal getSubmitQuantity(BmfObject nodeData) {
        List<BmfObject> passBoxes = nodeData.getList("passBoxes");
        if (CollectionUtils.isEmpty(passBoxes)) {
            throw new BusinessException("请提交周转箱");
        }
        // 提交的数量
        return passBoxes
                .stream()
                .map(it -> it.getBigDecimal("quantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static boolean isCompleted(List<BmfObject> allDetails, String attribute) {
        return allDetails
                .stream()
                .allMatch(it -> it.getBigDecimal(attribute).compareTo(BigDecimal.ZERO) == 0);
    }

    public static boolean isCompleted(List<BmfObject> allDetails, String zeroAttr, String eqAttr1, String eqAttr2) {
        return allDetails
                .stream()
                .allMatch(it -> {
                    boolean isZero = it.getBigDecimal(zeroAttr).compareTo(BigDecimal.ZERO) == 0;
                    boolean isEquals = it.getBigDecimal(eqAttr1).compareTo(it.getBigDecimal(eqAttr2)) == 0;
                    return isZero && isEquals;
                });
    }
}
