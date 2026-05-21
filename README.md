# 生活服务系统

Life Service 是一个基于 Java 21 和 Spring Boot 3 的生活服务后端项目。项目以本地生活优惠券业务为主线，逐步实现商户查询、优惠券秒杀、订单关闭、缓存一致性、限流等能力。

当前版本聚焦 V2：多级缓存、缓存删除补偿、秒杀热路径预热，以及 Redis + Lua 滑动窗口限流。

## 技术栈

- Java 21
- Spring Boot 3.5.x
- MyBatis-Plus
- MySQL 8.x
- Redis
- RocketMQ
- Flyway
- JUnit 5 / Mockito

## 当前能力

### 商户与优惠券

- 商户分类查询
- 商户列表与商户详情查询
- 商户详情 Redis 缓存
- 秒杀券基础数据与库存表结构

### 缓存优化

- 缓存空值，防止缓存穿透
- TTL 随机抖动，降低缓存雪崩风险
- 逻辑过期缓存客户端
- Caffeine + Redis 二级缓存
- 缓存删除失败补偿任务表

### 滑动窗口限流

- `@RateLimiter` 注解 + AOP 无侵入限流
- Redis ZSet + Lua 原子滑动窗口计数
- 支持 `GLOBAL`、`IP`、`USER` 三种维度
- 秒杀下单接口启用全局、用户、IP 三层限流
- 普通商户查询限流 Redis 异常时 fail open，秒杀限流异常时 fail closed

### 秒杀下单

当前秒杀入口链路：

```text
读取秒杀券缓存
  -> Java 校验活动时间
  -> Redis Lua 原子判断库存和一人一单
  -> 生成订单号
  -> 发送 RocketMQ 消息
  -> Consumer 异步创建订单并扣减 MySQL 库存
```

已实现：

- 秒杀入口只读取 Redis 热数据，缺失时直接失败，不回源 MySQL
- 秒杀预热接口写入券元数据、Redis 库存，并重建用户集合就绪标记与已有订单用户
- 应用启动时默认自动预热未结束的秒杀券，便于脚手架阶段开箱联调；后续接入管理端后可关闭
- Redis Lua 防超卖
- Redis Set 保证一人一单资格
- RocketMQ 异步下单
- MQ 发送失败时回滚 Redis 资格
- 数据库唯一索引兜底防重复订单
- 业务单号：`LSO + yyyyMMdd + Redis 当日递增序列`

### 订单关闭

未支付订单关闭链路：

```text
Spring Task 扫描超时待支付订单
  -> 条件更新 PENDING_PAYMENT -> CLOSED
  -> 写入库存释放补偿任务
  -> 释放 Redis 库存
  -> 释放 MySQL 库存并标记任务成功
```

已实现：

- 超时待支付订单自动关闭
- 只关闭 `PENDING_PAYMENT` 订单
- 库存释放失败后进入补偿任务重试
- 多次失败后保留失败记录，便于后续人工处理或补偿系统接入

### 支付和关单并发

当前版本不接真实支付网关，只提供模拟支付接口用于验证状态并发：

```text
POST /api/v1/voucher-orders/{orderNo}/payment
Header: Authorization: Bearer {token}
```

支付和关单都依赖数据库条件更新：

```text
支付: where order_no = ? and user_id = ? and status = 1
关单: where id = ? and status = 1 and created_at <= ?
```

因此：

- 支付先成功，关单不会覆盖为关闭
- 关单先成功，支付返回 `ORDER_CLOSED`
- 重复支付已支付订单返回幂等成功

真实支付流水、退款单、退款补偿任务属于后续版本范围。

## 接口示例

```text
GET  /api/v1/merchant-categories
GET  /api/v1/merchants
GET  /api/v1/merchants/{id}
GET  /api/v1/merchants/{merchantId}/vouchers
POST /api/v1/auth/login
GET  /api/v1/auth/me
POST /api/v1/auth/logout
POST /api/v1/flash-sale-vouchers/{voucherId}/warmup
POST /api/v1/flash-sale-vouchers/{voucherId}/orders
POST /api/v1/voucher-orders/{orderNo}/payment
```

邮箱登录示例：

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "demo2001@life.local"
}
```

登录成功后，秒杀下单和支付接口需要携带：

```http
Authorization: Bearer {token}
```

## 前端

当前仓库新增 `frontend/` Vue 3 前端工程，用于提供一个适配 PC 浏览器的本地生活用户端原型，而不是接口测试面板或后台 Dashboard。

前端主流程包括：

- 浏览商户分类和商户列表
- 查看商户详情和优惠券
- 邮箱登录并保存 Token
- 体验秒杀券下单
- 查看最近订单并模拟支付
- 未完成的用户入口统一提示“功能未完成”

前端本地启动：

```bash
cd frontend
corepack enable
corepack prepare pnpm@10.11.0 --activate
pnpm install
pnpm run dev
```

Vite 开发服务默认运行在：

```text
http://localhost:5173/
```

开发环境会将 `/api` 代理到 Spring Boot：

```text
http://localhost:8081
```

构建：

```bash
cd frontend
pnpm run build
```

旧版 Spring Boot 静态联调页仍可作为轻量调试入口保留：

```text
http://localhost:8081/app/index.html
```

秒杀预热示例：

```http
POST /api/v1/flash-sale-vouchers/1001/warmup
```

秒杀下单示例：

```http
POST /api/v1/flash-sale-vouchers/1001/orders
Authorization: Bearer {token}
```

成功响应：

```json
{
  "success": true,
  "code": "OK",
  "message": null,
  "data": "LSO202605210000000001"
}
```

## 本地运行

公开配置文件 `src/main/resources/application.yaml` 只保留默认值和环境变量占位，不写入本机密码。

本地私有配置使用 `src/main/resources/application-local.yaml`，该文件已被 Git 忽略。

示例：

```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.150.101:3306/life_service
    username: root
    password: root
  data:
    redis:
      host: 192.168.150.101
      port: 6379
      password:

rocketmq:
  name-server: 192.168.150.101:9876
```

启动：

```bash
mvn spring-boot:run
```

如果使用环境变量：

```bash
MYSQL_HOST=192.168.150.101 \
MYSQL_PORT=3306 \
MYSQL_DATABASE=life_service \
MYSQL_USERNAME=root \
MYSQL_PASSWORD=root \
REDIS_HOST=192.168.150.101 \
ROCKETMQ_NAME_SERVER=192.168.150.101:9876 \
mvn spring-boot:run
```

启动秒杀券自动预热默认开启。脚手架阶段没有管理端时，应用启动会把未结束、状态为未开始或进行中的秒杀券写入 Redis 热数据；后续有优惠券发布流程后，可以关闭该行为：

```bash
FLASH_SALE_STARTUP_WARMUP_ENABLED=false mvn spring-boot:run
```

## 测试

运行单元测试：

```bash
mvn test
```

当前测试覆盖：

- 缓存客户端
- Redis 订单号生成
- 商户查询缓存
- 秒杀 Lua 资格判断链路
- MQ 消息发布与消费
- 超时关单
- 库存释放补偿
- 支付和关单并发状态判断
- 滑动窗口限流注解、Key 解析和 Redis Lua 客户端
- 启动时秒杀券自动预热

## 项目结构

```text
src/main/java/io/github/ikemoon/lifeservice
  common          通用响应、异常、配置
  infrastructure  缓存、ID 等基础设施
  merchant        商户查询
  voucher         优惠券与秒杀券
  order           优惠券订单、秒杀下单、关单、支付状态
```

订单模块当前按职责拆分：

```text
order
  controller
  entity
  enums
  mapper
  messaging
  service
    impl
    close
    payment
    stock
```

## 当前边界

当前版本已经完成中小规模秒杀入口的核心闭环，但仍不包含：

- 真实支付网关接入
- 支付流水表和退款单
- 退款补偿任务
- 多实例下的完整运维监控
- Prometheus / Grafana 指标看板
- 网关限流和风控

这些能力会在后续 V2/V3 版本继续演进。
