package com.chinajey.dwork.common.utils;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.SpringUtils;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WriteBackUtils {

    public static final String D_KEY = "_distribute_quantity";

    /**
     * 反写单据明细，例如已入库数量、待入库数量
     *
     * @param details           单据的明细，必须同物料
     * @param quantity          要反写的总数量
     * @param distributedAttr   已分配的数量属性 - 例如：已入库数量
     * @param undistributedAttr 未分配的数量属性 - 例如：待入库数量
     */
    public static void writeBack(List<BmfObject> details, BigDecimal quantity, String distributedAttr, String undistributedAttr) {
        writeBack(details, quantity, distributedAttr, undistributedAttr, null);
    }

    /**
     * 反写单据明细，例如已入库数量、待入库数量
     *
     * @param details           单据的明细，必须同物料
     * @param quantity          要反写的总数量
     * @param distributedAttr   已分配的数量属性 - 例如：已确认数量
     * @param undistributedAttr 未分配的数量属性 - 例如：待确认数量
     * @param incAttribute      需要增加数量的属性 - 例如：待入库数量
     */
    public static void writeBack(List<BmfObject> details, BigDecimal quantity, String distributedAttr, String undistributedAttr, String incAttribute) {
        boolean hasLineNum = details.stream().allMatch(it -> StringUtils.isNotBlank(it.getString("lineNum")));
        List<BmfObject> ds;
        if (hasLineNum) {
            try {
                ds = details
                        .stream()
                        .sorted(Comparator.comparing(it -> it.getLong("lineNum")))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                // 为了防止行号使用逗号分割的情况
                ds = details;
            }
        } else {
            ds = details;
        }
        BmfService bmfService = SpringUtils.getBean(BmfService.class);
        for (int i = 0; i < ds.size(); i++) {
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BmfObject detail = ds.get(i);
            // 已分配的数量
            BigDecimal distributed = detail.getBigDecimal(distributedAttr);
            // 未分配的数量
            BigDecimal undistributed = detail.getBigDecimal(undistributedAttr);
            // 本次要分配的数量
            BigDecimal current;
            if (i == ds.size() - 1) {
                current = quantity;
                quantity = BigDecimal.ZERO;
            } else {
                if (quantity.compareTo(undistributed) > 0) {
                    current = undistributed;
                    quantity = BigDecimalUtils.subtractResultMoreThanZero(quantity, undistributed);
                } else {
                    current = quantity;
                    quantity = BigDecimal.ZERO;
                }
            }
            detail.put(undistributedAttr, BigDecimalUtils.subtractResultMoreThanZero(undistributed, current));
            detail.put(distributedAttr, BigDecimalUtils.add(distributed, current));
            if (StringUtils.isNotBlank(incAttribute)) {
                detail.put(incAttribute, BigDecimalUtils.add(detail.getBigDecimal(incAttribute), current));
            }
            detail.put(D_KEY, current);
            bmfService.updateByPrimaryKeySelective(detail);
        }
    }

    /**
     * 反写单据明细，例如已入库数量、待入库数量
     *
     * @param materialCode      会根据物料编码过滤details
     * @param details           单据的明细，不需要同物料
     * @param quantity          要反写的总数量
     * @param distributedAttr   已分配的数量属性 - 例如：已入库数量
     * @param undistributedAttr 未分配的数量属性 - 例如：待入库数量
     */
    public static void writeBack(String materialCode, List<BmfObject> details, BigDecimal quantity, String distributedAttr, String undistributedAttr) {
        List<BmfObject> ds = details
                .stream()
                .filter(it -> StringUtils.equals(it.getString("materialCode"), materialCode))
                .collect(Collectors.toList());
        writeBack(ds, quantity, distributedAttr, undistributedAttr);
    }

    public static boolean isRealWriteBack(BmfObject detail) {
        return detail.getBigDecimal(WriteBackUtils.D_KEY) != null && detail.getBigDecimal(WriteBackUtils.D_KEY).compareTo(BigDecimal.ZERO) > 0;
    }
}
