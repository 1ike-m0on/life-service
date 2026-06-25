# Life Service JMeter Load Tests

This folder keeps reusable JMeter assets for local load testing. Heavy result
files are not committed.

## Scope

The load-test plans cover two different goals:

```text
flash-sale-orders.jmx
flash-sale-orders-gui.jmx
```

These are focused flash-sale order plans for the hot endpoint:

```text
POST /api/v1/flash-sale-vouchers/{voucherId}/orders
```

Use them to benchmark the optimized async flash-sale order path, stock
competition, sold-out fast failure, and fail-closed behavior.

```text
mixed-user-behavior.jmx
```

This is a broader user-journey plan. It keeps browsing traffic as the dominant
behavior and adds a small amount of flash-sale order, order detail, order list,
and optional payment traffic. Use it when you want production-like evidence
across the local life-service surface instead of a single hot endpoint.

All plans read a token CSV prepared before the timed run. Login/token creation
stays outside JMeter so the measured path is repeatable.

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

The committed JMX files use `recycle=false` and `stopThread=true`. Prepare at
least `threads * loops` token rows for a run unless you intentionally change
that JMeter setting in a local copy.

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

The same reset is enough for the mixed behavior plan when it uses
`voucherId=1001`. If you pass another `voucherId`, update the SQL variables and
Redis keys in a local copy before the run.

## Flash-Sale Specialized Scenarios

### Scenario 1: Success Path Baseline

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
  "-JtokenCsv=.\tests\load\jmeter\data\tokens-12000.csv" `
  "-Jthreads=200" `
  "-JrampUp=10" `
  "-Jloops=60" `
  "-JvoucherId=1001"
```

Watch:

```text
life_flash_sale_request_total
life_flash_sale_success_total
HTTP P95 / P99 in Grafana
```

### Scenario 2: Stock Competition

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

### Scenario 3: Sold-Out Fast Failure

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

### Scenario 4: Fail Closed

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

## Mixed User Behavior Scenario

Purpose:

```text
Exercise the product-shaped path: browse discovery data, inspect merchants and
content, view vouchers, attempt a small amount of flash-sale ordering, review
orders, and optionally pay a subset of created orders.
```

Per loop, `mixed-user-behavior.jmx` covers:

```text
GET  /api/v1/merchant-categories
GET  /api/v1/merchants
GET  /api/v1/notes
GET  /api/v1/merchants/{merchantId}
GET  /api/v1/merchants/{merchantId}/vouchers
GET  /api/v1/merchants/{merchantId}/notes
GET  /api/v1/notes/{noteId}
POST /api/v1/flash-sale-vouchers/{voucherId}/orders        optional
GET  /api/v1/voucher-orders/{orderNo}                       optional after a created order
POST /api/v1/voucher-orders/{orderNo}/payment               optional after a created order
GET  /api/v1/users/me/voucher-orders
```

The plan extracts `merchantId` and `noteId` from list responses. If extraction
does not find a record, it falls back to `defaultMerchantId=1` and
`defaultNoteId=1`.

Recommended starting inputs:

```text
threads:       50
ramp-up:       20s
loops:         20
orderPercent:  5
payPercent:    20
orderWaitMs:   1000
```

`orderPercent` is evaluated per loop. `payPercent` is evaluated only after a
successful flash-sale order response created an `orderNo`. Business rejections
from duplicate users or stock exhaustion are expected as data is consumed; do
not treat them as infrastructure errors unless HTTP 5xx or assertion failures
also appear.

Run in non-GUI mode:

```powershell
jmeter -n `
  -t .\tests\load\jmeter\mixed-user-behavior.jmx `
  -l .\tests\load\jmeter\results\mixed-user-behavior.jtl `
  -e -o .\tests\load\jmeter\results\mixed-user-behavior-html `
  "-JbaseUrl=http://localhost:8081" `
  "-JtokenCsv=.\tests\load\jmeter\data\tokens-12000.csv" `
  "-Jthreads=50" `
  "-JrampUp=20" `
  "-Jloops=20" `
  "-JvoucherId=1001" `
  "-JorderPercent=5" `
  "-JpayPercent=20" `
  "-JorderWaitMs=1000"
```

Small smoke run:

```powershell
jmeter -n `
  -t .\tests\load\jmeter\mixed-user-behavior.jmx `
  -l .\tests\load\jmeter\results\mixed-user-behavior-smoke.jtl `
  "-JbaseUrl=http://localhost:8081" `
  "-JtokenCsv=.\tests\load\jmeter\data\tokens-12000.csv" `
  "-Jthreads=1" `
  "-JrampUp=1" `
  "-Jloops=1" `
  "-JvoucherId=1001" `
  "-JorderPercent=100" `
  "-JpayPercent=0"
```

If the smoke run reports order-detail failures under load, inspect RocketMQ
consumer lag and either increase `orderWaitMs` or set `payPercent=0` when the
run is meant to measure browsing plus order submission only.

## Result Interpretation

For every run, record:

```text
branch / commit
machine and Docker resources
JMeter GUI or non-GUI
scenario file and parameters
requests / users / stock / threads / ramp-up / loops
average / median / P90 / P95 / P99 / max / throughput
HTTP 5xx
business success and business rejection counts
Redis stock and user set size
MySQL order count and duplicate count
Grafana observations
```

For `mixed-user-behavior.jmx`, compare parent transaction samples such as
`Browse home feed`, `Inspect merchant and content`, and `Review my orders`
alongside individual endpoint samples. The mixed plan is meant to expose
read-path latency, authentication overhead on order queries, and the effect of
a small write path mixed into normal browsing traffic.

The JMeter console summary can be misleading for this plan because parent
transactions and child HTTP samples are both written to the JTL. Use the JTL or
HTML report as the source of truth for total sample count, per-label latency,
and error rate.

Local load-test numbers are useful for comparing versions on the same machine.
They are not production SLA claims.

## JMX Validation

Validate the mixed user behavior JMX is well-formed XML:

```powershell
[xml](Get-Content -Raw -LiteralPath .\tests\load\jmeter\mixed-user-behavior.jmx) | Out-Null
```

Before handing off a change, also run:

```powershell
git diff --check
```
