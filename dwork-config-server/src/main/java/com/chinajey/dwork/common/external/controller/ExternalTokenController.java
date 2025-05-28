package com.chinajey.dwork.common.external.controller;

import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.common.external.service.ExternalTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @Author 陈阳
 * @Date 2023/5/17 16:46
 * @Version 1.0
 * 通用外部应用获取Token
 */
@Slf4j
@RestController
@RequestMapping("/common/external")
public class ExternalTokenController {

    @Resource
    private ExternalTokenService externalTokenService;

    @GetMapping("/token")
    public InvokeResult getToken(@RequestParam("client_id") String clientId, @RequestParam("client_secret") String clientSecret) {
        return externalTokenService.getToken(clientId, clientSecret);
    }
}
