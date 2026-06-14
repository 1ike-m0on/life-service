# Benchmark Summary

[Back to README](README.md)

This page records local benchmark results for the Life Service flash-sale
ordering flow. The numbers were collected on a local Windows development
machine with Docker/VM infrastructure, so they should be read as engineering
comparison data, not as a production SLA.

The benchmark is split into two different questions:

```text
1. Architecture comparison:
   What changed after moving the hot request path from synchronous database
   work to Redis Lua qualification plus asynchronous order creation?

2. Observability tuning:
   After the user-facing path included token authentication, rate limiting,
   Redis Lua, and message publishing, what did monitoring reveal and how much
   did the tuned configuration improve?
```

These two sets of numbers should not be mixed. The first one explains the core
architecture gain. The second one explains the user-perceived entrance path
after monitoring and tuning.

## 1. Architecture Comparison

### Scenario

```text
scenario: successful order path
total requests: 12000
users: 12000 unique users
stock: 12000
threads: 200
ramp-up: 10s
loop count: 60
business exception expectation: 0%
```

This is a successful-path comparison. It is not a stock-insufficient fast-fail
test.

### Compared Paths

Synchronous baseline:

```text
POST /api/v1/flash-sale-vouchers/1001/orders/sync-baseline

MySQL voucher query
Redis user lock
MySQL user-order query
MySQL hot-row stock deduction
MySQL order insert
```

Optimized flash-sale path:

```text
POST /api/v1/flash-sale-vouchers/1001/orders

warm up flash-sale hot data
read Redis hot data in the request path
Redis Lua qualification
  - flash-sale status/time
  - stock
  - one-user-one-order
generate order number
publish order command
consumer persists order asynchronously
```

### Result

| Metric | Sync baseline | Optimized path |
| --- | ---: | ---: |
| Samples | 12000 | 12000 |
| Average latency | 893 ms | 6 ms |
| Median latency | 956 ms | 7 ms |
| P90 | 1067 ms | 10 ms |
| P95 | 1099 ms | 11 ms |
| P99 | 1145 ms | 14 ms |
| Min | 59 ms | not recorded |
| Max | 1923 ms | 76 ms |
| Throughput | 190.2 req/s | 1176.4 req/s |
| Business exception rate | 0.00% | 0.00% |

### Takeaway

The old synchronous path performs database reads, hot-row stock deduction, and
order insertion in the request thread. Under concurrency, the stock row becomes
the main bottleneck.

The optimized path keeps the flash-sale request thread focused on Redis Lua
qualification, order number generation, and asynchronous order dispatch. MySQL
writes are moved out of the user request path.

In this local benchmark, the core flash-sale entrance improved from `893ms` to
`6ms` on average, and throughput improved from `190.2 req/s` to
`1176.4 req/s`.

## 2. Observability-Driven User Entrance Tuning

After the core architecture was in place, the benchmark focus moved from the
pure hot path to the user-perceived entrance path.

Each request in this phase uses a real bearer token and goes through:

```text
HTTP request
  -> Redis token authentication
  -> request tracing / logging
  -> sliding-window rate limiting
  -> Redis Lua flash-sale qualification
  -> order number generation
  -> message publishing
  -> HTTP response
```

The HTTP response is returned after the order command is accepted for
asynchronous processing. The benchmark does not wait for the consumer to finish
MySQL persistence.

Message publishing remained part of the measured path, but it was not the main
tuning variable. The purpose of this phase was to use monitoring to understand
where the user-facing entrance was spending time under local Docker load.

### What Monitoring Showed

Prometheus, Grafana, and request-stage timing made the following points visible:

- Single-request application-stage timing was usually only a few milliseconds.
- JMeter latency included user-perceived waiting time, including connection and
  server-side queuing that is not fully visible inside business-stage logs.
- Hikari and Redis pool pressure appeared during load tests.
- Background jobs, asynchronous consumers, and flash-sale requests all share
  database/Redis resources, so pool sizing matters.
- Local single-machine JMeter tests can distort IP/global rate-limit behavior
  because many requests originate from the same client environment.

The important discovery was not "RocketMQ is slow". The important discovery was
that the local full entrance path was affected by connection-pool capacity and
rate-limit configuration.

### Tuning Changes

The tuned run kept the same user-facing entrance shape and focused on runtime
configuration:

- Exposed and tuned MySQL Hikari pool settings.
- Exposed and tuned Redis Lettuce pool settings.
- Kept request-stage timing so auth, rate limit, Redis Lua, order-id generation,
  and message publishing could be separated.
- Adjusted rate-limit configuration for local benchmark conditions so the test
  did not accidentally measure single-machine IP throttling instead of the
  flash-sale entrance.

Example tunable settings:

```text
MYSQL_POOL_MAX_SIZE
MYSQL_POOL_MIN_IDLE
MYSQL_POOL_CONNECTION_TIMEOUT_MS
REDIS_POOL_MAX_ACTIVE
REDIS_POOL_MAX_IDLE
REDIS_POOL_MIN_IDLE
RATE_LIMIT_ENABLED
RATE_LIMIT_IP_ENABLED
RATE_LIMIT_GLOBAL_LIMIT_OVERRIDE
```

### Result

Common scenario:

```text
successful flash-sale entrance path
total requests: 12000
users: 12000 unique users
stock: 12000
threads: 200
ramp-up: 10s
loop count: 60
HTTP error expectation: 0%
```

Compared versions:

- Initial monitored run: first full user-entrance benchmark after monitoring was
  added.
- Latest tuned run: same benchmark style after pool configuration and
  rate-limit tuning.

| Metric | Initial monitored run | Latest tuned run | Change |
| --- | ---: | ---: | ---: |
| Samples | 12000 | 12000 | - |
| Average latency | 391 ms | 271 ms | -30.7% |
| Median latency | 309 ms | 215 ms | -30.4% |
| P90 | 700 ms | 494 ms | -29.4% |
| P95 | 804 ms | 592 ms | -26.4% |
| P99 | 1098 ms | 698 ms | -36.4% |
| Min | 5 ms | 4 ms | - |
| Max | 1409 ms | 799 ms | -43.3% |
| Throughput | 389.9 req/s | 525.4 req/s | +34.8% |
| HTTP error rate | 0.00% | 0.00% | stable |

### Interpretation

```text
Average response improved from 391ms to 271ms.
P99 improved from 1098ms to 698ms.
Throughput improved from 389.9 req/s to 525.4 req/s.
HTTP error rate stayed at 0.00%.
```

This should be read as an observability-driven tuning result for the
user-perceived flash-sale entrance path.

It does not mean that message publishing alone was optimized by 30%. Message
publishing was part of the path in both runs. The measured improvement came
from identifying and tuning the surrounding runtime bottlenecks, especially
connection-pool capacity and benchmark-specific rate-limit behavior.

## 3. How To Read These Numbers

- The architecture comparison shows why Redis Lua plus asynchronous order
  creation is much faster than doing database stock deduction and order creation
  in the request thread.
- The observability tuning result shows how the full user-facing entrance path
  improved after monitoring exposed connection-pool pressure and rate-limit
  effects.
- JMeter measures HTTP response time from the user's perspective.
- The benchmark does not include waiting for asynchronous consumer persistence.
- Final correctness still needs Redis/MySQL verification: no oversell, no
  duplicate order, Redis stock exhausted as expected, and MySQL orders
  eventually catch up.

## 4. Next Steps

Future benchmark work should focus on:

- consumer lag and message backlog visibility
- publish latency histogram
- Hikari pending connection alerts
- Redis command latency visibility
- adaptive rate limiting when P95/P99, pool pressure, or consumer lag becomes
  abnormal
- separate reports for core hot path, user entrance path, and asynchronous
  persistence completion time
