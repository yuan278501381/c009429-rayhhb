package com.chinajey.dwork.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajey.application.common.utils.ValueUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

public class LineNumUtils {
    public void lineNumHandle(List<BmfObject> oldDetails, List<JSONObject> details) {
        //原最大行号
        int manLineNum = oldDetails == null ? 0 : oldDetails.stream().map(detail -> ValueUtil.toInt(detail.get("lineNum"))).max(Integer::compareTo).orElse(0);
        //新最大行号
        int newManLineNum = details.size() == 0 ? 0 : details.stream().map(detail -> ValueUtil.toInt(detail.get("lineNum"))).max(Integer::compareTo).orElse(0);
        if (newManLineNum != 0 && newManLineNum > manLineNum) {
            //当新的数据有行号，且大于原来的行号
            manLineNum = newManLineNum;
        }
        //过滤没有行号的明细
        List<JSONObject> newDetails = details.stream()
                .filter(detail -> StringUtils.isBlank(detail.getString("lineNum")))
                .collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(newDetails)) {
            for (JSONObject newDetail : newDetails) {
                newDetail.remove("id");
                newDetail.put("lineNum", ++manLineNum);
            }
        }
    }
}
