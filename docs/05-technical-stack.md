# 05 Technical Stack

## 1. Backend

| Layer | Choice |
|---|---|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.x |
| AI | Spring AI |
| API | Spring MVC/WebFlux + SSE |
| Build | Maven |
| Validation | Jakarta Validation |
| Persistence | Spring Data JDBC/JPA 或 MyBatis |
| DB | PostgreSQL |
| Vector | pgvector |
| Cache | Redis |
| Migration | Flyway |
| Test | JUnit 5 + Testcontainers |

## 2. Frontend

- Vue 3
- TypeScript
- Vite
- Pinia
- UI framework可选
- SSE client

## 3. Code Intelligence

- JavaParser/JDT
- Tree-sitter
- 自定义 symbol extractor
- PostgreSQL graph tables
- pgvector

## 4. Agent

- Spring AI
- 自研轻量 Agent Runtime
- MCP Client/Server 按协议实现
- Tool Registry
- Model Gateway

不要在 MVP 同时引入多个 Agent Framework。

## 5. Infrastructure

- Docker
- Docker Compose
- PostgreSQL
- Redis
- OpenTelemetry
- Prometheus/Grafana（生产环境）

## 6. Model Abstraction

统一：

```text
ModelGateway
├── ChatModel
├── EmbeddingModel
└── RerankModel
```

Provider 可插拔。

## 7. Why This Stack

选择 Java/Spring 的原因：

- 用户已有 Java 后端经验。
- 企业服务生态成熟。
- 与现有微服务体系容易集成。
- Agent 平台重点是工程化而不是模型训练。

Python 只作为 AI 生态验证和特殊 Parser/模型服务的补充，不作为主后端。
