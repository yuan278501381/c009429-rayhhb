package com.chinajey.dwork.common.utils;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.holder.ThreadLocalHolder;
import com.chinajey.application.common.holder.UserAuthDto;

import java.util.Date;

/**
 * 更新数据工具类
 */
public class UpdateDataUtils {

    //更新操作人和操作时间
    public static void updateOperateInfo(JSONObject jsonObject) {
        UserAuthDto.LoginInfo loginInfo = ThreadLocalHolder.getLoginInfo();
        if (loginInfo != null) {
            UserAuthDto.Resource resource = loginInfo.getResource();
            if (resource != null) {
                jsonObject.put("operatorName", resource.getResourceName());
                jsonObject.put("operatorCode", resource.getResourceCode());
            }
        }
        jsonObject.put("operatorTime", new Date());
    }

}
