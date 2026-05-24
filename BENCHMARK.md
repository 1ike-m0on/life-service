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

## V2.1 Observability Tuning

After Prometheus/Grafana monitoring and request-stage timing were added, the
flash-sale path was measured again in the same local Docker benchmark style.
This comparison uses the real RocketMQ publisher, not the diagnostic dry-run
publisher.

Common scenario:

```text
successful flash-sale order path
total requests: 12000
users: 12000 unique users
stock: 12000
threads: 200
ramp-up: 10s
loop count: 60
HTTP error expectation: 0%
```

Compared versions:

- Initial monitored run: first real RocketMQ run after the monitoring stack was added.
- Latest tuned run: real RocketMQ run after connection-pool tuning, order-close
  isolation during load testing, and rate-limit configuration tuning.

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

Star summary:

```text
★ Average response improved from 391ms to 271ms.
★ P99 improved from 1098ms to 698ms.
★ Throughput improved from 389.9 req/s to 525.4 req/s.
★ HTTP error rate stayed at 0.00%.
★ The latest result still includes real RocketMQ send acknowledgement.
★ The number does not include waiting for asynchronous consumer persistence.
```

What changed:

- Monitoring became visible through Actuator, Prometheus, and Grafana.
- Request-stage timing made auth, rate-limit, Redis Lua, order-id generation,
  and MQ publishing costs easier to separate.
- MySQL and Redis pool sizes became configurable for local load testing.
- Auto-close jobs can be disabled during flash-sale entrance benchmarks to
  avoid mixing background order-close work into the measurement.
- Rate-limit behavior became configurable so local one-machine tests do not
  accidentally benchmark IP/global throttling instead of the flash-sale path.

The result should be read as a local engineering comparison. It is useful for
showing optimization direction and bottleneck discovery, but it is not a
production capacity guarantee.
