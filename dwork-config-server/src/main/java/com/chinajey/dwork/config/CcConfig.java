package com.chinajey.dwork.config;


import com.chinajey.dwork.interceptor.ExternalInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * /ek/** - 标准拦截
 * /external/** - 配置拦截
 * /internal/** - 配置拦截
 */
@Configuration
public class CcConfig implements WebMvcConfigurer {

    @Resource
    private ExternalInterceptor externalInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this.externalInterceptor)
                .addPathPatterns("/external/**")    // 拦截二开项目对外开放的接口
                .order(2);
    }
}
