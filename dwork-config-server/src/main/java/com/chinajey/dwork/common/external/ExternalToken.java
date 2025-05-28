package com.chinajey.dwork.common.external;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.common.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

/**
 * @Author 陈阳
 * @Date 2023/5/17 15:23
 * @Version 1.0
 * 提供给外部Token
 */
@Slf4j
@Component
public class ExternalToken {

    //token

    //有效时间 7200 秒
    public static final Long TIME = 7200L;
    @Resource
    ExternalApi externalApi;


    /***
     * 生成token header.payload.signature
     */
    public String getToken(String appid, String number) {
        //创建jwt builder
        JWTCreator.Builder builder = JWT.create();
        //payload
        builder.withClaim(appid, number);
        return builder.withExpiresAt(new Date(System.currentTimeMillis() + (TIME * 1000)))//指定令牌的过期时间
                .sign(Algorithm.HMAC256(externalApi.getSignature()));//签发算法
    }

    /***
     * 验证token
     */
    public InvokeResult verify(String token) {
        try {
            JWT.require(Algorithm.HMAC256(externalApi.getSignature())).build().verify(token);
        } catch (TokenExpiredException e) {
            return new InvokeResult(HttpStatus.TOKEN_INVALID, "访问令牌已过期!");
        } catch (Exception e) {
            log.error(e.getMessage());
            return new InvokeResult(HttpStatus.ERROR, "无效的访问令牌!");
        }
        return null;
    }


}
