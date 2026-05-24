# Life Service JMeter Load Tests

This folder keeps reusable JMeter assets for local load testing. Heavy result
files are not committed.

## Scope

The current load tests focus on the flash-sale order endpoint:

```text
POST /api/v1/flash-sale-vouchers/{voucherId}/orders
```

The endpoint requires `Authorization: Bearer <token>`. The test plan reads a
token CSV so the measured request path stays close to the real flash-sale
order path. Login/token creation is handled as a setup step, not inside the
timed order sampler.

## Prerequisites

Start the local stack:

```powershell
docker compose up -d --build backend
docker compose --profile monitor up -d prometheus grafana
```

Open these pages while testing:

```text
Backend health: http://localhost:8081/actuator/health
Prometheus:     http://localhost:9090
Grafana:        http://localhost:3000
```

Before recording benchmark numbers, keep SQL logging at INFO or higher. MyBatis
DEBUG SQL logs can print several lines for every consumed order and will distort
local JMeter results, especially when the backend and RocketMQ consumer share
the same JVM.

## Prepare Users And Tokens

Generate a user email CSV:

```powershell
.\tests\load\jmeter\scripts\New-LoadUsers.ps1 `
  -Count 12000 `
  -OutputPath .\tests\load\jmeter\data\users-12000.csv
```

Login each user once and write a token CSV:

```powershell
.\tests\load\jmeter\scripts\New-AuthTokens.ps1 `
  -BaseUrl http://localhost:8081 `
  -UserCsv .\tests\load\jmeter\data\users-12000.csv `
  -OutputPath .\tests\load\jmeter\data\tokens-12000.csv
```

The token CSV columns are:

```text
email,token,clientIp
```

`clientIp` is sent as `X-Forwarded-For` so local single-machine tests can avoid
turning the IP-level rate limiter into the only bottleneck.

## Reset Flash-Sale Data

Before a clean benchmark run, reset MySQL state:

```powershell
docker exec -i life-service-mysql mysql -uroot -proot life_service < .\tests\load\sql\reset-flash-sale-voucher.sql
```

Then clear Redis flash-sale hot keys and warm up again:

```powershell
docker exec life-service-redis redis-cli DEL `
  life:cache:flash-sale-voucher:1001 `
  life:flash:voucher:stock:1001 `
  life:flash:voucher:users:1001 `
  life:flash:voucher:released-orders:1001

curl -X POST http://localhost:8081/api/v1/flash-sale-vouchers/1001/warmup
```

## Scenario 1: Success Path Baseline

Purpose:

```text
Measure the optimized async order path when every request should obtain an
order qualification.
```

Recommended inputs:

```text
requests: 12000
users:    12000 unique users
stock:    12000
threads:  200
ramp-up:  10s
loops:    60
```

Run in non-GUI mode:

```powershell
jmeter -n `
  -t .\tests\load\jmeter\flash-sale-orders.jmx `
  -l .\tests\load\jmeter\results\flash-sale-success.jtl `
  -e -o .\tests\load\jmeter\results\flash-sale-success-html `
  -JtokenCsv=.\tests\load\jmeter\data\tokens-12000.csv `
  -Jthreads=200 `
  -JrampUp=10 `
  -Jloops=60 `
  -JvoucherId=1001
```

Watch:

```text
life_flash_sale_request_total
life_flash_sale_success_total
HTTP P95 / P99 in Grafana
```

## Scenario 2: Stock Competition

Purpose:

```text
Verify no oversell and no duplicate orders when users exceed stock.
```

Example:

```text
requests: 30000
users:    30000 unique users
stock:    12000
threads:  500
ramp-up:  10s
loops:    60
```

Business failures such as stock exhaustion are expected in this scenario. Do
not read business failure percentage as system error rate. The important checks
are final order count, Redis stock, purchased-user set size, and duplicate
orders.

## Scenario 3: Sold-Out Fast Failure

Purpose:

```text
Verify requests are quickly rejected by Redis Lua after stock is sold out.
```

Example:

```text
requests: 12000
users:    12000 unique users
stock:    200
threads:  200
ramp-up:  10s
loops:    60
```

Watch:

```text
life_flash_sale_stock_not_enough_total
HTTP 5xx should stay at 0
```

## Scenario 4: Fail Closed

Purpose:

```text
Verify missing Redis hot data returns FLASH_SALE_NOT_READY and does not fall
back to the database on the hot path.
```

Delete the hot keys and call the order endpoint without warmup. Expected:

```text
ApiResponse.success=false
code=FLASH_SALE_NOT_READY
life_flash_sale_not_ready_total increases
no MQ publish
no MySQL order creation
```

## Result Interpretation

For every run, record:

```text
branch / commit
machine and Docker resources
JMeter GUI or non-GUI
requests / users / stock / threads / ramp-up / loops
average / median / P90 / P95 / P99 / max / throughput
HTTP 5xx
business success and business rejection counts
Redis stock and user set size
MySQL order count and duplicate count
Grafana observations
```

Local load-test numbers are useful for comparing versions on the same machine.
They are not production SLA claims.
