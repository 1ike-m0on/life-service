# 生活服务系统

Life Service 是一个基于 Java 21 和 Spring Boot 3 的生活服务后端项目。项目以本地生活优惠券业务为主线，逐步实现商户查询、优惠券秒杀、订单关闭、缓存一致性、限流等能力。

当前版本聚焦 V1：缓存优化、秒杀异步下单、未支付订单自动关闭，以及支付和关单并发控制。

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
- 缓存删除失败补偿任务表

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
Header: X-User-Id: {userId}
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
POST /api/v1/flash-sale-vouchers/{voucherId}/orders
POST /api/v1/voucher-orders/{orderNo}/payment
```

秒杀下单示例：

```http
POST /api/v1/flash-sale-vouchers/1001/orders
X-User-Id: 2001
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
