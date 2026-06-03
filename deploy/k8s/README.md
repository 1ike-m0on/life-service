# Life Service Kubernetes 部署说明

这套 K8s 配置用于补齐 Life Service 的云原生部署能力。当前版本定位是
**本地 Kubernetes / 学习验证 / 脚手架展示**，目标是让项目可以从 Docker
Compose 平滑走向 Kubernetes，而不是一次性做成生产级高可用平台。

## 当前包含内容

`deploy/k8s` 采用 Kustomize 组织：

```text
deploy/k8s
├── base
│   ├── namespace.yaml
│   ├── app-config.yaml
│   ├── mysql.yaml
│   ├── redis.yaml
│   ├── rocketmq.yaml
│   ├── backend.yaml
│   ├── frontend.yaml
│   └── kustomization.yaml
└── overlays
    └── local
        └── kustomization.yaml
```

基础组件：

- MySQL 8.0：`StatefulSet + PVC`
- Redis 7.2：`StatefulSet + PVC`
- RocketMQ NameServer：`Deployment`
- RocketMQ Broker：`StatefulSet + PVC`
- Spring Boot Backend：`Deployment + Service`
- Vue/Nginx Frontend：`Deployment + Service`

默认命名空间：

```text
life-service
```

## 前置条件

你需要先准备：

- Docker Desktop Kubernetes、minikube、kind 或其他本地 K8s 环境
- `kubectl`
- 集群中存在默认 `StorageClass`

检查当前上下文：

```powershell
kubectl config current-context
kubectl get nodes
kubectl get storageclass
```

## 一键部署本地 K8s 版本

在项目根目录执行：

```powershell
kubectl apply -k deploy/k8s/overlays/local
```

观察 Pod 状态：

```powershell
kubectl -n life-service get pods -w
```

查看 Service：

```powershell
kubectl -n life-service get svc
```

## 访问前端和后端

当前没有引入 Ingress，使用 `port-forward` 访问。

前端：

```powershell
kubectl -n life-service port-forward svc/frontend 8080:80
```

浏览器打开：

```text
http://localhost:8080
```

后端健康检查：

```powershell
kubectl -n life-service port-forward svc/backend 8081:8081
```

访问：

```text
http://localhost:8081/actuator/health
```

## 镜像说明

`base` 默认后端和前端镜像使用 GitHub Container Registry：

```text
ghcr.io/1ike-m0on/life-service-backend:latest
ghcr.io/1ike-m0on/life-service-frontend:latest
```

`overlays/local` 会将镜像替换为本地演示镜像：

```text
life-service-backend:local
life-service-frontend:local
```

因此本地部署前建议先构建：

```powershell
docker compose build backend frontend
```

如果使用 kind，需要先把镜像加载到 kind 集群：

```powershell
kind load docker-image life-service-backend:local
kind load docker-image life-service-frontend:local
```

## 本地 Secret

`overlays/local` 使用 Kustomize `secretGenerator` 创建本地演示 Secret：

```text
MYSQL_ROOT_PASSWORD=root
```

这只是本地演示默认值。生产环境不要直接使用该 Secret，后续应替换为：

- 外部 Secret 管理
- 云厂商 Secret Manager
- Sealed Secrets
- External Secrets Operator

## 配置映射

后端运行时主要通过 `life-service-config` 注入配置：

```text
SPRING_PROFILES_ACTIVE=demo
MYSQL_HOST=mysql
REDIS_HOST=redis
ROCKETMQ_NAME_SERVER=rocketmq-namesrv:9876
ORDER_MESSAGE_PROVIDER=rocketmq
FLASH_SALE_STARTUP_WARMUP_ENABLED=true
```

这与 Docker Compose 的内部服务名保持一致，方便从 Compose 迁移到 K8s。

## 启动顺序

Kubernetes 没有 Docker Compose 的 `depends_on`。如果后端在 MySQL 完全可连接前启动，
Flyway 会因为拿不到数据库连接而导致 Pod 重启。

当前清单使用轻量 `initContainer` 处理启动顺序：

- RocketMQ Broker 启动前等待 `rocketmq-namesrv:9876`
- Backend 启动前等待 `mysql:3306`、`redis:6379`、`rocketmq-namesrv:9876`
- Frontend 启动前等待 `backend:8081/actuator/health`

这不是复杂的服务编排，只是为了让本地 K8s demo 启动过程更稳定。

## 删除部署

删除整个命名空间即可清理所有资源：

```powershell
kubectl delete namespace life-service
```

这会同时删除 PVC 中的数据。如果你只是想重启服务，不要执行这个命令。

## 常见问题

### Pod 一直 Pending

通常是本地 K8s 没有默认 StorageClass。

检查：

```powershell
kubectl get storageclass
kubectl -n life-service describe pvc
```

### 后端 ImagePullBackOff

说明集群拉不到 GHCR 镜像。可以：

1. 确认镜像已经发布；
2. 使用本地镜像；
3. 为私有镜像仓库配置 `imagePullSecrets`。

### 后端 CrashLoopBackOff

优先检查后端日志：

```powershell
kubectl -n life-service logs deployment/backend
```

常见原因：

- MySQL 还没完全 ready；
- Secret 密码不一致；
- Redis 或 RocketMQ 没启动；
- Flyway 初始化失败。

### RocketMQ Broker 无法连接

当前 Broker 使用 Pod IP 作为 `brokerIP1`，适合本地单 Broker 验证。
如果后续做多副本或跨节点访问，需要重新设计 Broker 暴露方式。

## 当前边界

当前 K8s 版本暂不包含：

- Ingress / TLS
- HPA 自动扩缩容
- PDB
- Prometheus Operator
- Grafana Dashboard 自动导入
- 外部 MySQL / Redis / RocketMQ
- Secret 外部化管理
- 多副本 RocketMQ 集群

这些会放到后续云原生增强阶段逐步补齐。
