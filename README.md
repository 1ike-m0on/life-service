# 生活服务系统

Life Service 是一个基于 Java 21 和 Spring Boot 3 的生活服务后端项目。项目目标是从 MVP 开始，逐步实现商户查询、优惠券秒杀、订单关闭、缓存优化、滑动窗口限流等能力。

## 技术栈

- Java 21
- Spring Boot 3
- MyBatis-Plus
- MySQL
- Redis / Memurai
- Flyway

## 当前阶段

当前处于 MVP 脚手架阶段，已经包含：

- 新包名：`io.github.ikemoon.lifeservice`
- 新接口前缀：`/api/v1`
- 新表名：`ls_*`
- Flyway 初始化表结构
- 商户分类、商户查询、商户优惠券查询接口骨架
- 秒杀下单主链路骨架：Redis Lua 资格判断 + 同步落库
- 业务单号方案：`LSO + yyyyMMdd + Redis 当日递增序列`

## 本地配置

公开配置文件 `src/main/resources/application.yaml` 保留环境变量覆盖能力，不写入本机密码。

本机开发使用被 Git 忽略的 `src/main/resources/application-local.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/life_service
    username: root
  data:
    redis:
      host: localhost
      port: 6379
```

启动本地配置：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Redis 使用本机 Memurai，已验证：

```text
127.0.0.1:6379> ping
PONG
```

本地配置已启用 `createDatabaseIfNotExist=true`，第一次启动会自动创建 `life_service` 数据库，然后由 Flyway 创建表。

## 构建

```bash
mvn test
```
