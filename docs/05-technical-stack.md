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
| Persistence | Spring Data JPA 或 MyBatis |
| DB | MySQL 8.0+ |
| Vector | Qdrant |
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
- MySQL graph tables
- Qdrant（向量检索）

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
└── Rera└── RerankModel
```

Provider 可插拔。

## 7. Why This Stack

选择 Java/Spring 的原因：

- 用户已有 Java 后端经验。
- 企业服务生态成熟。
- 与现有微服务体系容易集成。
- Agent 平台重点是工程化而不是模型训练。

选择 MySQL 8.0+ 的原因：

- 企业内部已有 MySQL 运维体系，降低运维成本。
- MySQL 8.0 支持 JSON 类型、窗口函数，满足 metadata 和图关系存储需求。
- Qdrant 独立承接向量检索，职责分离，可单独扩容。

Python 只作为 AI 生态验证和特殊 Parser/模型服务的补充，不作为主后端。
