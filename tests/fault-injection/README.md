# Fault injection recovery verification

This directory contains local demo fault-injection helpers. The durable entrypoint is:

```powershell
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1
```

## Safety model

Default behavior is non-destructive probe-only mode. Without `-ApplyFaults`, the durable script does not pause, stop, or start any container. It still writes a JSON and Markdown summary under `tests/fault-injection/results/<run-name>/`.

When `-ApplyFaults` is supplied, the script delegates fault and recovery actions to the existing wrappers:

- `scripts/Set-RedisAvailability.ps1`
- `scripts/Set-MysqlAvailability.ps1`
- `scripts/Set-RocketMqBrokerAvailability.ps1`

Those wrappers only pause, unpause, stop, or start individual containers. They do not run `docker compose down`, remove containers, or remove volumes. The app will be unavailable during fault windows, so use this only against a disposable local demo stack.

The default Redis fault is `Pause`, which recovers with `Unpause`. MySQL and RocketMQ broker faults use `Stop`, then recover with `Start`.

## Common runs

Render the planned 600 second schedule without touching the stack:

```powershell
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1 -PlanOnly
```

Run the default 600 second probe-only verification:

```powershell
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1
```

Run a 5 minute fault/recovery verification:

```powershell
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1 -DurationSeconds 300 -ApplyFaults -Force
```

Run a 30 second smoke:

```powershell
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1 -Smoke -ApplyFaults -Force
```

Run only Redis:

```powershell
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1 -DurationSeconds 300 -FaultTargets Redis -ApplyFaults -Force
```

## Probes

Every probe cycle checks:

- `GET /actuator/health`
- `GET /api/v1/merchants?pageNo=1&pageSize=5`
- `GET /api/v1/merchants/{MerchantId}`
- `GET /api/v1/merchants/{MerchantId}/vouchers`
- `POST /api/v1/flash-sale-vouchers/{VoucherId}/warmup`

Consistency checks periodically call `scripts/Invoke-FaultInjectionAcceptance.ps1`, which reads Redis hot keys and MySQL order state for the voucher.

If `-AuthToken` is supplied, the script also probes:

- `GET /api/v1/users/me/voucher-orders?pageNo=1&pageSize=5`

`-IncludeOrderProbe` additionally calls the authenticated order endpoint. This can create or reuse a voucher order for the user, so it is off by default and requires `-AuthToken`.

## Key parameters

- `-DurationSeconds`: total run length, default `600`; use `300` for 5 minutes or `-Smoke` for 30 seconds.
- `-FaultTargets`: one or more of `Redis`, `MySQL`, `RocketMQ`; default is all three.
- `-ApplyFaults`: actually delegate pause/stop/start actions. Without it, actions are dry-run.
- `-Force`: skip the single interactive confirmation when applying faults.
- `-PlanOnly`: write the plan and summaries without sleeping, probing, or changing containers.
- `-BaseUrl`: backend URL, default `http://localhost:8081`.
- `-VoucherId`: voucher used by warmup and consistency checks, default `1001`.
- `-MerchantId`: merchant used by read probes, default `1001`.
- `-ProbeIntervalSeconds`: override automatic probe cadence.
- `-ConsistencyIntervalSeconds`: override automatic consistency cadence.
- `-SkipHttpProbes` / `-SkipConsistency`: useful when validating script behavior without a running stack.

## Results

Each run writes:

- `summary.json`: full parameters, phase plan, actions, probes, and consistency checks.
- `summary.md`: compact run summary.
- `action-*.log`: delegated availability wrapper output.
- `consistency-*.log`: Redis/MySQL acceptance output.
