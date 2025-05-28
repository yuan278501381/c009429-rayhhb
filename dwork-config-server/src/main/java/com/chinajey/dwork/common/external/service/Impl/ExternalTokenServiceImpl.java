package com.chinajey.dwork.common.external.service.Impl;

import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.common.external.ExternalApi;
import com.chinajey.dwork.common.external.ExternalToken;
import com.chinajey.dwork.common.external.service.ExternalTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author 陈阳
 * @Date 2023/5/17 16:53
 * @Version 1.0
 */
@Service
@Slf4j
public class ExternalTokenServiceImpl implements ExternalTokenService {

    @Resource
    private ExternalToken externalToken;

    @Resource
    private ExternalApi externalApi;

    @Override
    public InvokeResult getToken(String name, String pass) {
        if (externalApi.getName().equals(name) && externalApi.getPass().equals(pass)) {
            Map<String, Object> map = new HashMap<>();
            map.put("token", externalToken.getToken(name, pass));
            map.put("expireIn", ExternalToken.TIME);
            return InvokeResult.success(map);
        } else {
            return InvokeResult.fail("客户端ID或密钥无效");
        }
    }
}
