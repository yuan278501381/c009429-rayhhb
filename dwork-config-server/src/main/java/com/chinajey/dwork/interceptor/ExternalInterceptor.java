package com.chinajey.dwork.interceptor;

import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.common.external.ExternalToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ExternalInterceptor extends BaseCustomResponse implements HandlerInterceptor {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ExternalToken externalToken;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String token = request.getParameter("token");
        if (StringUtils.isBlank(token)) {
            this.writeExceptionJsonObject(response, this.objectMapper, InvokeResult.fail("缺少访问令牌!"));
            return false;
        }
        InvokeResult verifyResult = externalToken.verify(token);
        if (verifyResult != null) {
            this.writeExceptionJsonObject(response, this.objectMapper, verifyResult);
            return false;
        }
        return true;
    }
}
