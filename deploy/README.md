# Deployment Files

For a full one-command demo stack, prefer the root `compose.yaml`:

```powershell
docker compose up -d --build
```

That command starts MySQL, Redis, RocketMQ, the Spring Boot backend, and the Vue
frontend. See `DEPLOYMENT.md` for the full deployment guide.

This folder keeps development and production deployment templates.

## Local Development Services

This compose file starts the local middleware used by life-service:

- MySQL 8.0
- Redis 7.2
- RocketMQ NameServer, Broker, and Dashboard

## Usage

Copy the example environment file and set local values:

```powershell
Copy-Item deploy/.env.example deploy/.env
```

Then start the services:

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.dev.yml up -d
```

The default command starts MySQL, Redis, RocketMQ NameServer, and RocketMQ
Broker. RocketMQ Dashboard is optional to reduce local memory pressure:

```powershell
docker compose --profile dashboard --env-file deploy/.env -f deploy/docker-compose.dev.yml up -d
```

Stop the services:

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.dev.yml down
```

Remove local volumes when you want to reset all middleware data:

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.dev.yml down -v
```

## Production Template

`deploy/docker-compose.prod.yml` is a single-host template that pulls published
images instead of building local source.

Copy the example production environment file:

```powershell
Copy-Item deploy/.env.prod.example deploy/.env.prod
```

Edit secrets and image tags in `deploy/.env.prod`, then start:

```powershell
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d
```

## Application Environment

When Spring Boot runs on the host machine, keep these values aligned with
`src/main/resources/application.yaml` or your ignored
`src/main/resources/application-local.yaml`:

```text
MYSQL_HOST=localhost
MYSQL_PORT=3307
MYSQL_DATABASE=life_service
MYSQL_USERNAME=root
MYSQL_PASSWORD=root
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
ROCKETMQ_NAME_SERVER=<VM or localhost IP>:9876
ORDER_MESSAGE_PROVIDER=rocketmq
```

RocketMQ ports:

```text
NameServer: localhost:9876
Broker:     localhost:10911
Dashboard:  http://localhost:18082  (only with --profile dashboard)
```

If Docker runs inside a VM, set `ROCKETMQ_BROKER_IP` in `deploy/.env` to the VM
IP address that your host Spring Boot process can reach.

## Resource Limits

The compose file sets conservative local limits to avoid Docker Desktop or WSL
using too much memory on Windows:

```text
MySQL:              768m, 1 CPU, InnoDB buffer pool 128M
Redis:              256m, 0.5 CPU, maxmemory 128mb, noeviction
RocketMQ NameServer:384m, 0.5 CPU, JVM Xmx 192m
RocketMQ Broker:    1024m, 1 CPU, JVM Xmx 512m
RocketMQ Dashboard: 512m, 0.5 CPU, JVM Xmx 256m, optional profile
```

If the machine still becomes unstable, lower the `*_MEM_LIMIT`, `*_CPUS`, and
RocketMQ `*_JAVA_OPT*` values in ignored `deploy/.env`. On Docker Desktop with
WSL2, also cap WSL globally in `%UserProfile%\.wslconfig`, for example:

```ini
[wsl2]
memory=4GB
processors=4
swap=2GB
```

After changing `.wslconfig`, run:

```powershell
wsl --shutdown
```

Then start Docker Desktop again.
