package com.chinajey.dwork.common.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MES接口参数配置
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "external-api")
public class ExternalApi {

    private String name;

    private String pass;

    private String signature;
}
