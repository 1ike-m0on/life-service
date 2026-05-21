# Local Development Services

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

Stop the services:

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.dev.yml down
```

Remove local volumes when you want to reset all middleware data:

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.dev.yml down -v
```

## Application Environment

When Spring Boot runs on the host machine, keep these values aligned with
`src/main/resources/application.yaml` or your ignored
`src/main/resources/application-local.yaml`:

```text
MYSQL_HOST=localhost
MYSQL_PORT=3306
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
Dashboard:  http://localhost:18082
```

If Docker runs inside a VM, set `ROCKETMQ_BROKER_IP` in `deploy/.env` to the VM
IP address that your host Spring Boot process can reach.
