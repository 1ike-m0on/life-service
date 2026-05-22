# Benchmark Summary

[Back to README](README.md)

This page records a local benchmark used to compare the old synchronous
flash-sale order path with the optimized Redis Lua + RocketMQ path.

The numbers are from a local Windows + Docker/VM development environment. They
are useful for architectural comparison, but they are not a production SLA.

## Scenario

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

## Paths

Synchronous baseline:

```text
POST /api/v1/flash-sale-vouchers/1001/orders/sync-baseline

MySQL voucher query
Redis user lock
MySQL user-order query
MySQL stock deduction
MySQL order insert
```

Optimized path:

```text
POST /api/v1/flash-sale-vouchers/1001/orders

warm up flash-sale hot data
read Redis only in the request path
Redis Lua stock and one-user-one-order qualification
generate order number
send RocketMQ order command
consumer persists order asynchronously
```

## Result

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

## Takeaway

The old synchronous path performs database reads, hot-row stock deduction, and
order insertion in the request thread. Under concurrency, the stock row becomes
the main bottleneck.

The optimized path keeps the flash-sale request thread focused on Redis Lua
qualification, order number generation, and RocketMQ publishing. MySQL writes
are moved to the asynchronous consumer.

In the current local environment, the most stable result appears around
1000-1100+ req/s for the core flash-sale path. When pressure is pushed higher,
latency and tail latency rise quickly, which indicates local single-instance or
pressure-client queuing.

Future versions should add monitoring for core endpoint latency, P95/P99,
business feedback rate, and MQ backlog, then tighten rate limiting when the
core experience starts to degrade.
