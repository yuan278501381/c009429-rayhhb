## 项目说明

该项目只提供配置模块的源代码，不包含德沃克核心源代码（核心模块）。

## 项目结构

## 项目启动

* 启动前确认服务模块的resources下的*.yml是否配置正确（主要：MySQL、Redis、MQTT、MinIO、FastBI）
* 运行服务模块中的com.chinajey.dwork.ServerApplication类即可

## 核心模块升级

* 根据版本清单，按需升级想要的版本。语雀：https://lzop85.yuque.com/lzop85/ler0el/gw4zogxgcgb2lvig
* 找到顶层pom.xml, 修改properties中的revision的版本号即可

> 升级注意：__升级时，不仅仅是修改版本号，有些升级包有相对应的增量SQL文件需要执行__

## 开发须知
新增的接口只能有三种URI前缀，否则会默认被拦截掉
1. /ek/**  
   这是配置表单及列表用的，例如给前端配置页面使用
   访问需要x-dw-token也就是德沃克的token
2. /external/**  
   这是对外的OpenAPI接口，访问时需要在URL拼接token参数  
   token生成地址：/common/external/token?client_id=CLIENT_ID&client_secret=CLIENT_SECRET

## 开发示例

## OpenAPI
```text
https://lzop85.yuque.com/gcc6g8/ov7r6y/qfet5nzap5w9rqo5
```
   
## 打包发布
```shell
  mvn clean package -Dmaven.test.skip=true
```
