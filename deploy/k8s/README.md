# Life Service Kubernetes 本地部署

这个目录维护 Life Service 的 Kubernetes 编排。当前目标是让项目具备基础云原生部署能力，同时保持本地学习、验收和迭代成本可控。

重点约定：

- Kubernetes 配置只负责部署，不直接修改前后端源码。
- 基础设施包括 MySQL、Redis、RocketMQ。
- 应用包括 backend、frontend。
- 后端或前端代码变化时，只滚动更新 backend/frontend，不重建 MySQL、Redis、RocketMQ。
- 只有数据库结构需要从零验证、临时数据已不可用，或基础设施配置变化时，才删除命名空间重建。

## 目录结构

```text
deploy/k8s
├── base
│   ├── app-config.yaml
│   ├── backend.yaml
│   ├── frontend.yaml
│   ├── kustomization.yaml
│   ├── mysql.yaml
│   ├── namespace.yaml
│   ├── redis.yaml
│   └── rocketmq.yaml
├── overlays
│   └── local
│       └── kustomization.yaml
├── local-rollout.ps1
└── README.md
```

## 当前设计

本地 Kubernetes 使用一套轻量配置：

- MySQL、Redis、RocketMQ 使用临时本地存储，方便反复重建和验证 Flyway 初始化。
- backend 启动前会等待 MySQL 可登录、Redis 可响应、RocketMQ NameServer 和 Broker 可用。
- frontend 启动前会等待 backend 健康检查通过。
- local overlay 使用本地镜像名 `life-service-backend` 和 `life-service-frontend`。

这不是生产级 HA 配置。生产环境后续应引入持久化存储、Secret 管理、Ingress、镜像仓库 tag 策略、资源分级和监控告警。

## 首次部署

第一次部署，或者明确需要重新应用基础设施清单时，使用 `-ApplyBase`：

```powershell
.\deploy\k8s\local-rollout.ps1 -Target all -ApplyBase
```

脚本会做这些事：

1. 构建 backend 镜像。
2. 构建 frontend 镜像。
3. 使用当前 Git SHA 和时间生成唯一镜像 tag。
4. 如果当前 Kubernetes context 是 kind，自动执行 `kind load docker-image`。
5. 应用 `deploy/k8s/overlays/local`。
6. 对 backend/frontend 执行 `kubectl set image`。
7. 等待 backend/frontend rollout 完成。

Docker Desktop Kubernetes 通常不需要 `kind load`，因为它可以直接使用本机 Docker 镜像。

## 日常滚动更新

后端或前端代码变化时，不要带 `-ApplyBase`。这样脚本只会构建新镜像并滚动更新对应 Deployment，不会重建 MySQL、Redis、RocketMQ。

如果当前命名空间里还存在旧版 StatefulSet 形态的基础设施，脚本会阻止 `-ApplyBase`，避免在同一个命名空间里叠出两套 MySQL、Redis 或 RocketMQ。需要从新清单完整验证时，先删除命名空间再重新部署。

`-ForceApplyBase` 只用于你明确知道当前命名空间里的旧资源不会和新清单冲突的场景。普通本地验证不建议使用。

### 后端变更

```powershell
.\deploy\k8s\local-rollout.ps1 -Target backend
```

这会构建新的 backend 镜像并滚动更新 `deployment/backend`，不会重启 MySQL、Redis、RocketMQ、frontend。

### 前端变更

```powershell
.\deploy\k8s\local-rollout.ps1 -Target frontend
```

这会构建新的 frontend 镜像并滚动更新 `deployment/frontend`，不会重启 backend 和基础设施。

### 前后端都变更

```powershell
.\deploy\k8s\local-rollout.ps1 -Target all
```

这会分别构建 backend/frontend 镜像，并只滚动更新两个应用 Deployment。

## 已经手动构建镜像时

如果你已经手动构建了镜像，只想重新设置镜像，可以跳过构建：

```powershell
.\deploy\k8s\local-rollout.ps1 -Target backend -ImageTag local -SkipBuild
```

不过更推荐每次使用唯一 tag，例如：

```powershell
.\deploy\k8s\local-rollout.ps1 -Target backend -ImageTag local-backend-test-001
```

固定使用 `:local` 容易出现 Pod 重启后仍使用旧镜像的问题。唯一 tag 可以强制 Kubernetes 识别为一次新的应用版本。

## 访问服务

前端：

```powershell
kubectl -n life-service port-forward svc/frontend 8080:80
```

浏览器访问：

```text
http://localhost:8080
```

后端：

```powershell
kubectl -n life-service port-forward svc/backend 8081:8081
```

健康检查：

```powershell
curl.exe http://localhost:8081/actuator/health
```

## 常用检查

查看 Pod：

```powershell
kubectl -n life-service get pods -o wide
```

查看 Deployment 和 StatefulSet：

```powershell
kubectl -n life-service get deploy,sts
```

查看后端日志：

```powershell
kubectl -n life-service logs deployment/backend --tail=200
```

查看前端日志：

```powershell
kubectl -n life-service logs deployment/frontend --tail=100
```

查看 rollout：

```powershell
kubectl -n life-service rollout status deployment/backend
kubectl -n life-service rollout status deployment/frontend
```

查看最终渲染出的 manifest：

```powershell
kubectl kustomize deploy/k8s/overlays/local
```

## 什么时候需要重建命名空间

只有这些情况建议重建：

- MySQL 临时数据已经不可信。
- Flyway migration 需要从空库重新验证。
- backend 滚动更新失败，并且日志出现 Flyway checksum mismatch。
- Redis 或 RocketMQ 临时状态干扰测试。
- 基础设施 manifest 大改，例如 Service、端口、ConfigMap、Secret、存储策略调整。

重建命名空间：

```powershell
kubectl delete namespace life-service
.\deploy\k8s\local-rollout.ps1 -Target all -ApplyBase
```

因为当前使用临时本地存储，删除命名空间会清空 MySQL、Redis、RocketMQ 的本地临时数据。

## 推荐开发流程

```text
后端代码变化
  -> .\deploy\k8s\local-rollout.ps1 -Target backend

前端代码变化
  -> .\deploy\k8s\local-rollout.ps1 -Target frontend

前后端都变化
  -> .\deploy\k8s\local-rollout.ps1 -Target all

基础设施或数据库初始化需要重测
  -> kubectl delete namespace life-service
  -> .\deploy\k8s\local-rollout.ps1 -Target all -ApplyBase
```

这样可以保证 k8s 部署不是一次性快照，而是可以随着后端和前端持续变化继续使用。
