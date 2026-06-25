# Life Service Test Data

This directory documents reusable test-data rules for Life Service. Large
generated datasets are intentionally not committed.

## Data Levels

Use different data sizes for different goals:

| Level | Purpose | Typical Size | Commit Data |
| --- | --- | --- | --- |
| Demo | Let reviewers open the UI and complete the main flow | small seed data from Flyway | yes, as migration seed |
| Integration | Verify API and service behavior automatically | mocked or test-created data | yes, in tests |
| Benchmark | Compare one implementation with another on the same machine | 12,000 to 30,000 generated users | no |
| Stress | Find local bottlenecks and protection thresholds | environment-dependent | no |

Benchmark and stress results are evidence for local engineering decisions. They
are not production SLA claims.

## Generated Files

Generated JMeter CSV files live under:

```text
tests/load/jmeter/data/
```

This path is ignored by Git. It is used for files such as:

```text
users-12000.csv
tokens-12000.csv
users-30000.csv
tokens-30000.csv
```

JMeter result files live under:

```text
tests/load/jmeter/results/
```

This path is also ignored by Git.

## User CSV

Generate deterministic benchmark users:

```powershell
.\tests\load\jmeter\scripts\New-LoadUsers.ps1 `
  -Count 12000 `
  -StartUserId 1 `
  -OutputPath .\tests\load\jmeter\data\users-12000.csv
```

The user CSV contains:

```text
email
```

## Token CSV

After the backend is running, log in generated users once and write tokens:

```powershell
.\tests\load\jmeter\scripts\New-AuthTokens.ps1 `
  -BaseUrl http://localhost:8081 `
  -UserCsv .\tests\load\jmeter\data\users-12000.csv `
  -OutputPath .\tests\load\jmeter\data\tokens-12000.csv
```

The token CSV contains:

```text
email,token,clientIp
```

`clientIp` is sent by JMeter as `X-Forwarded-For`. This keeps local single-host
tests from accidentally measuring only the IP rate limiter.

To validate the input CSV and parameters without calling the backend:

```powershell
.\tests\load\jmeter\scripts\New-AuthTokens.ps1 `
  -BaseUrl http://localhost:8081 `
  -UserCsv .\tests\load\jmeter\data\users-12000.csv `
  -OutputPath .\tests\load\jmeter\data\tokens-12000.csv `
  -ValidateOnly
```

## Flash-Sale Reset

Before a clean flash-sale benchmark, reset MySQL data:

```powershell
docker exec -i life-service-mysql mysql -uroot -proot life_service < .\tests\load\sql\reset-flash-sale-voucher.sql
```

Then clear Redis hot keys and warm up the voucher again:

```powershell
docker exec life-service-redis redis-cli DEL `
  life:cache:flash-sale-voucher:1001 `
  life:flash:voucher:stock:1001 `
  life:flash:voucher:users:1001 `
  life:flash:voucher:released-orders:1001

curl.exe -X POST http://localhost:8081/api/v1/flash-sale-vouchers/1001/warmup
```

## Evidence Rules

For every benchmark or stress run, keep local evidence with:

```text
commit
scenario
parameters
JMeter aggregate result
Redis final state
MySQL final state
Grafana observations
conclusion and boundary
```

Store heavy evidence locally, not in Git. Only stable, summarized conclusions
should be copied into public documents such as `BENCHMARK.md`.

## Git Safety

Before committing test-related changes, check ignored generated data:

```powershell
git check-ignore -v tests/load/jmeter/data/users-12000.csv
git check-ignore -v tests/load/jmeter/data/tokens-12000.csv
git check-ignore -v tests/load/jmeter/results
```

Do not force-add generated CSV, JTL, HTML reports, or local evidence folders.

## Public Dataset Importers

Public local-life or review datasets can be transformed with the scripts under:

```text
tests/data/importers/
```

The importer path keeps raw public datasets out of the repository and writes
normalized merchant/note CSV outputs under an ignored generated folder. Start
with `tests/data/importers/README.md` and the synthetic sample fixtures there.
