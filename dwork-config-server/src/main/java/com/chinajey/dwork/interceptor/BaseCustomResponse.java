package com.chinajey.dwork.interceptor;

import com.chinajey.application.common.resp.InvokeResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class BaseCustomResponse {

    protected void writeExceptionJsonObject(HttpServletResponse response, ObjectMapper objectMapper, InvokeResult returnJson) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(objectMapper.writeValueAsString(returnJson));
    }

}
