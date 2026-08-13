# 09 Deployment and Operations

## 1. Environments

- local
- test
- staging
- production

## 2. Local Docker Compose

服务：

- mysql
- redis
- qdrant
- backend
- frontend

模型 API 通过环境变量注入。

## 3. Environment Variables

```text
SPRING_PROFILES_ACTIVE
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
REDIS_URL
QDRANT_URL
QDRANT_API_KEY
LLM_BASE_URL
LLM_API_KEY
LLM_MODEL
EMBEDDING_MODEL
OTEL_EXPORTER_OTLP_ENDPOINT
```

禁止将真实 secret 提交 Git。

## 4. Deployment

推荐：

```text
Docker Image
 ↓
Registry
 ↓
Kubernetes/VM
 ↓
Backend
 ↓
MySQL 8.0+
 ↓
Redis
 ↓
Qdrant
```

MVP 可先 Docker Compose，生产再 Kubernetes。

## 5. Observability

必须记录：

- requestId
- traceId
- runId
- projectId
- toolName
- model
- latency
- token usage
- error type

OpenTelemetry：

```text
Agent Run
 ├─ Planning
 ├─ LLM
 ├─ Tool
 ├─ Retrieval
 ├─ Review
 └─ Answer
```

## 6. Logging

禁止：

- API Key
- Password
- Cookie
- Authorization Header
- 完整敏感源码

代码内容日志必须默认摘要化。

## 7. Backup

MySQL：

- daily backup
- retention policy
- restore drill

Qdrant：

- 定期 snapshot，按官方文档执行
- 向量数据可由 MySQL chunk 记录重新 embedding 重建，不应成为唯一事实来源

Project index 可重建，因此不应成为唯一事实来源。

## 8. Failure Handling

MySQL unavailable：

- fail fast
- health check DOWN

Redis unavailable：

- 非关键缓存功能降级

Qdrant unavailable：

- 向量检索降级为关键词检索
- health check 明确标注 vector search degraded

LLM unavailable：

- fallback model 或返回可重试错误

Index incomplete：

- UI 明确显示 index status

## 9. Rollback

应用版本：

- immutable Docker image
- database migration 可回滚策略

Prompt/Agent：

- 每个版本必须可追溯
- 支持恢复上一稳定版本
