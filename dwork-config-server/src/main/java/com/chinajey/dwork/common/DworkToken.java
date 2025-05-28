package com.chinajey.dwork.common;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.exception.BusinessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

public class DworkToken {

    private static String value;

    private static LocalDateTime localDateTime;

    public static void init(String requestTokenUrl) {
        JSONObject jsonObject = new RestTemplate().getForObject(requestTokenUrl, JSONObject.class);
        if (jsonObject == null) {
            throw new BusinessException("获取访问令牌失败");
        }
        if (jsonObject.getInteger("code") != 10000) {
            throw new BusinessException("获取访问令牌失败：" + jsonObject.getString("message"));
        }
        JSONObject data = jsonObject.getJSONObject("data");
        value = data.getString("token");
        localDateTime = LocalDateTime.now().plusSeconds(data.getInteger("expire") - 100);
    }

    public static void clear() {
        value = null;
        localDateTime = null;
    }

    public static String getValue() {
        return value;
    }

    public static LocalDateTime getLocalDateTime() {
        return localDateTime;
    }
}
