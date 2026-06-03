# Deployment

This document describes how to run Life Service as a complete local demo stack.

## Quick Start

Requirements:

- Docker Desktop or Docker Engine

Run the full stack from the repository root:

```bash
docker compose up -d --build
```

Open:

- Frontend: http://localhost:8080
- Backend health: http://localhost:8081/actuator/health
- Backend Prometheus metrics: http://localhost:8081/actuator/prometheus

Stop the stack:

```bash
docker compose down
```

Reset all local data:

```bash
docker compose down -v
```

## What Starts

The root `compose.yaml` starts:

- MySQL 8.0
- Redis 7.2
- RocketMQ NameServer
- RocketMQ Broker
- Spring Boot backend
- Vue frontend served by Nginx

The frontend proxies `/api/*` to the backend inside the Docker Compose network,
so the browser only needs to visit `http://localhost:8080`.

## Default Ports

| Service | URL / Port |
| --- | --- |
| Frontend | http://localhost:8080 |
| Backend | http://localhost:8081 |
| Backend health | http://localhost:8081/actuator/health |
| Backend metrics | http://localhost:8081/actuator/prometheus |
| MySQL | localhost:3307 |
| Redis | localhost:6379 |
| RocketMQ NameServer | localhost:9876 |
| RocketMQ Broker | Docker network only |

RocketMQ Dashboard is optional:

```bash
docker compose --profile dashboard up -d --build
```

Dashboard URL:

- http://localhost:18082

Monitoring is optional:

```bash
docker compose --profile monitor up -d
```

Monitoring URLs:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000

Grafana automatically provisions the `Life Service Overview` dashboard.

Default Grafana login for the local demo is:

```text
admin / admin
```

Prometheus scrapes the backend inside the Docker Compose network through:

```text
backend:8081/actuator/prometheus
```

Key application metrics:

| Metric | Meaning |
| --- | --- |
| `life_flash_sale_request_total` | Flash-sale order endpoint requests |
| `life_flash_sale_success_total` | Flash-sale qualifications accepted |
| `life_flash_sale_stock_not_enough_total` | Flash-sale requests rejected because stock is exhausted |
| `life_flash_sale_duplicate_total` | Flash-sale requests rejected by one-user-one-order rule |
| `life_flash_sale_not_ready_total` | Flash-sale requests rejected because hot Redis data is missing |
| `life_flash_sale_mq_publish_failure_total` | MQ publish failures after Redis qualification |
| `life_flash_sale_redis_rollback_failure_total` | Redis qualification rollback failures |
| `life_cache_delete_failure_total` | Redis cache delete failures that created retry tasks |
| `life_cache_delete_task_pending` | Pending cache delete retry tasks |
| `life_cache_delete_task_failed_total` | Cache delete retry tasks that reached the retry limit |
| `life_order_close_success_total` | Expired pending orders closed successfully |
| `life_order_close_failure_total` | Expired order close failures |
| `life_stock_release_failure_total` | Stock release failures during close compensation |
| `life_stock_release_retry_total` | Stock release retries scheduled |
| `life_payment_order_closed_total` | Payment callbacks rejected because close already won |
| `life_rate_limit_allowed_total` | Requests accepted by the sliding-window limiter |
| `life_rate_limit_rejected_total` | Requests rejected by the sliding-window limiter |

## Kubernetes Local Deployment

`deploy/k8s` contains a Kustomize-based local Kubernetes deployment foundation.
It is intended for Docker Desktop Kubernetes, kind, or minikube demos and for
learning the deployment shape. It is not yet a production-grade highly available
Kubernetes setup.

Requirements:

- `kubectl`
- Docker Desktop Kubernetes, kind, minikube, or another local Kubernetes cluster
- A default StorageClass for MySQL persistent storage
- Local images named `life-service-backend:local` and `life-service-frontend:local`

Build the local images first:

```bash
docker compose build backend frontend
```

Apply the local overlay:

```bash
kubectl apply -k deploy/k8s/overlays/local
kubectl get pods -n life-service -w
```

Open the frontend through port-forwarding:

```bash
kubectl -n life-service port-forward svc/frontend 8080:80
```

Then visit:

```text
http://localhost:8080
```

Backend health can be checked with:

```bash
kubectl -n life-service port-forward svc/backend 8081:8081
curl http://localhost:8081/actuator/health
```

Clean up the local Kubernetes deployment:

```bash
kubectl delete namespace life-service
```

See [deploy/k8s/README.md](deploy/k8s/README.md) for manifest layout,
configuration notes, troubleshooting commands, and the intended next steps for
future production overlays.

## Demo Data

The project already contains Flyway demo seed data:

- Demo users
- Merchant categories
- Merchants
- Normal vouchers
- Flash-sale vouchers

The Docker demo stack starts the backend with:

```text
SPRING_PROFILES_ACTIVE=demo
FLASH_SALE_STARTUP_WARMUP_ENABLED=true
```

This means the application runs Flyway migrations, loads demo data, and warms
eligible flash-sale vouchers into Redis at startup.

Demo login example:

```text
demo2001@life.local
```

## Resource Limits

The default compose file uses conservative resource limits for local machines:

| Service | Default limit |
| --- | --- |
| MySQL | 768m, 1 CPU |
| Redis | 256m, 0.5 CPU |
| RocketMQ NameServer | 384m, 0.5 CPU |
| RocketMQ Broker | 1024m, 1 CPU |
| Backend | 768m, 1 CPU |
| Frontend | 128m, 0.25 CPU |
| Prometheus | 256m, 0.25 CPU |
| Grafana | 256m, 0.25 CPU |

On small Windows / WSL2 machines, do not start the dashboard or monitor profile
first. Start the core stack first, then enable optional profiles only when
needed.

If Docker Desktop still consumes too much memory, cap WSL globally in
`%UserProfile%\.wslconfig`:

```ini
[wsl2]
memory=4GB
processors=4
swap=2GB
```

Then restart WSL:

```powershell
wsl --shutdown
```

## Development Middleware Mode

If you want to run Spring Boot and Vite on the host machine, use the dev compose
file instead. It only starts middleware:

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.dev.yml up -d
```

Then run:

```bash
mvn spring-boot:run
```

And in another terminal:

```bash
cd frontend
pnpm run dev
```

## Published Image Mode

After the GitHub Actions image publish workflow runs on `main`, a server can use
the production compose template:

```bash
cp deploy/.env.prod.example deploy/.env.prod
```

Edit `deploy/.env.prod`, then run:

```bash
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d
```

Production mode pulls images instead of building local source:

- `ghcr.io/1ike-m0on/life-service-backend`
- `ghcr.io/1ike-m0on/life-service-frontend`

## Troubleshooting

Check container status:

```bash
docker compose ps
```

View backend logs:

```bash
docker compose logs -f backend
```

View RocketMQ broker logs:

```bash
docker compose logs -f rocketmq-broker
```

Rebuild after code changes:

```bash
docker compose up -d --build
```

Reset everything:

```bash
docker compose down -v
```
