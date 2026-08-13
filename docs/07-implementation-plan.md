# 07 Implementation Plan

## Phase 0 — Bootstrap

目标：可启动。

任务：

- 创建 Maven 项目
- Spring Boot
- PostgreSQL
- Redis
- Flyway
- Vue 3
- Docker Compose
- health endpoint
- CI

DoD：

- `docker compose up` 成功
- backend health=UP
- frontend 可访问
- migration 自动执行

## Phase 1 — Project & File Index

实现：

- project CRUD
- local repository import
- file scanner
- hash
- language detection
- full reindex

DoD：

- 可导入 Project A
- 文件数量与实际目录一致
- 越权路径访问测试通过

## Phase 2 — AST & Code Graph

实现：

- Java AST
- TS/Vue AST
- symbols
- relations
- graph query

DoD：

- 能查询类/方法
- 能查询 caller/callee
- 能查询 Controller → Service → Mapper
- 能查询 frontend API → backend endpoint

## Phase 3 — Retrieval

实现：

- exact search
- keyword search
- vector search
- metadata filter
- graph expansion
- evidence extraction

DoD：

- Top-K retrieval Evaluation 建立
- Evidence 带路径和行号

## Phase 4 — Agent

实现：

- Model Gateway
- Tool Registry
- Planner
- Executor
- Reviewer
- Answer Composer
- SSE
- Trace

DoD：

- 能完成至少 20 个固定 Evaluation Cases
- 核心结论有 Evidence
- Tool Call 有 Trace

## Phase 5 — Quality

实现：

- Evaluation runner
- regression dataset
- prompt/version management
- token metrics
- latency metrics
- failure recovery

DoD：

- 每次 Prompt/Runtime 修改自动跑 Evaluation
- 核心指标无明显回归

## Phase 6 — Production

实现：

- RBAC
- audit
- rate limit
- secret management
- observability
- backup
- Docker production profile

DoD：

- 通过 Security Checklist
- 通过 Deployment Checklist

## Recommended Code Structure

```text
backend/
  src/main/java/com/example/pia/
    api/
    project/
    indexing/
    parser/
    graph/
    retrieval/
    agent/
    tool/
    model/
    evaluation/
    trace/
    security/
    common/

frontend/
  src/
    views/
    components/
    api/
    stores/
    types/
```

## Development Rule

先完成 Phase 0，再进入 Phase 1。禁止并行实现全部模块导致无法验证。
