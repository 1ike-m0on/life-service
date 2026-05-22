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
| MySQL | localhost:3307 |
| Redis | localhost:6379 |
| RocketMQ NameServer | localhost:9876 |
| RocketMQ Broker | localhost:10909 / 10911 / 10912 |

RocketMQ Dashboard is optional:

```bash
docker compose --profile dashboard up -d --build
```

Dashboard URL:

- http://localhost:18082

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

On small Windows / WSL2 machines, do not start the dashboard profile first.

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
