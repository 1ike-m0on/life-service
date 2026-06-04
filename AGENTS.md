# Project AI Context Entry

## Project Overview

Life Service 是一个基于 Java 21、Spring Boot 3.5、Vue 3、Redis、MySQL 和 RocketMQ 的本地生活服务脚手架。

它不是单纯的接口测试台，而是一个偏产品化的 full-stack prototype： 用户可以浏览商户、查看优惠券、参与秒杀抢券、查看订单状态，并体验支付与超时关单的基础流程。

## Tech Stack

- Language: Java
- Framework: Spring Boot
- Build tool: Maven

## Common Commands

- docker-start: `docker compose up -d --build`
- docker-stop: `docker compose down`
- docker-reset: `docker compose down -v`
- build: `mvn clean package`
- test: `mvn test`
- run: `mvn spring-boot:run`
- frontend-install: `cd frontend && npm install`
- frontend-dev: `cd frontend && npm run dev`

## Key Directories

- .github
- .github/workflows
- .m2
- .m2/repository
- assets
- assets/screenshots
- deploy
- deploy/monitoring
- docs
- docs/design
- docs/jmeter
- docs/postman
- docs/testing
- frontend
- frontend/public
- frontend/src
- src
- src/main
- src/test
- tests
- tests/load

## AI Context

This project keeps long-lived design and implementation details in `docs/`.
`docs/` is intentionally ignored by git and is for local planning, review, and handoff.

Before coding, read context in this order:

1. `AGENTS.md`
2. `docs/README.md`
3. The task-specific design document under `docs/design/...` when available.
4. Related testing, release, or benchmark notes under `docs/testing/...` when the change affects that area.

Do not rely on a previous chat thread as the only source of truth. If a rule in chat conflicts with a repository document, pause and ask which one should be updated.

After completing a meaningful implementation change, update the relevant local design or testing document under `docs/` before asking to commit. Because `docs/` is ignored, mention the document update in the final summary instead of staging it unless the user explicitly asks to publish docs.

## Boundaries

Life Service 是一个用于学习、展示和继续扩展的本地生活服务脚手架。 它关注真实产品流程和后端工程能力展示，但目前还不是生产级高可用商业系统。

## Backend Development Rules

- Keep feature code inside its domain package, for example `merchant`, `voucher`, `order`, `user`, or `note`.
- New backend code follows: domain package first, then `controller`, `domain/po`, `domain/dto`, `domain/query`, `domain/vo`, `mapper`, `service`, and `service/impl`.
- This project is not strict Clean Architecture. It uses a table-driven, MyBatis-Plus friendly modular monolith style.
- Controllers only handle HTTP binding, validation, authentication context, and `ApiResponse` wrapping.
- Services own business rules, authorization ownership checks, pagination queries, and transaction boundaries.
- Mappers stay thin and extend MyBatis-Plus `BaseMapper` unless a custom SQL query is clearly needed.
- Service interfaces live under `service`; implementations live under `service/impl`.
- For user-facing APIs, return `domain/vo` objects and do not expose `domain/po` database objects.
- Add Flyway migrations as new files. Do not edit old migrations after they have been shared or run, except for explicitly approved local reset work.
- Before adding a new table, document its domain ownership, table type, state fields, unique indexes, query indexes, cache impact, and consistency requirements in `docs/`.
- Current-user APIs must read the user from `UserContext`; never trust a request `userId` for "my" resources.
- Every backend change must include focused tests or an explicit reason why automated tests are not useful.

## Acceptance

- Run `mvn test` for backend changes.
- Run `npm run build` inside `frontend/` for frontend changes.
- Run Docker Compose or K8s checks only when deployment files or runtime configuration are touched.
- Keep commits scoped: backend, frontend, deployment, docs, and tests should not be mixed unless the feature cannot work without all of them.
- Do not commit `.ai/`, `docs/`, local environment files, generated previews, JMeter CSV inputs, or load-test result outputs unless the user explicitly changes the repository policy.
