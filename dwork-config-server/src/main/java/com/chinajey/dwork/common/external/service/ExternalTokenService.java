package com.chinajey.dwork.common.external.service;

import com.chinajey.application.common.resp.InvokeResult;

/**
 * @Author 陈阳
 * @Date 2023/5/17 16:50
 * @Version 1.0
 * 通用外部Token服务
 */
public interface ExternalTokenService {
    InvokeResult getToken(String name, String pass);
}
