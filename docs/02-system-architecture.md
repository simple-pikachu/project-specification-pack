# 02 System Architecture

## 1. Logical Architecture

```text
Web UI
  |
SSE / REST
  |
API Gateway
  |
Agent Service
  |
Agent Runtime
  |---- Planner
  |---- Executor
  |---- Reviewer
  |---- Context Manager
  |
Tool Registry
  |---- Code Search Tool
  |---- Code Read Tool
  |---- Code Graph Tool
  |---- API Search Tool
  |---- Schema Tool
  |---- Git Tool
  |---- RAG Tool
  |
Knowledge Layer
  |---- MySQL 8.0+
  |---- Qdrant
  |---- Redis
  |---- Code Graph
  |
Project A
```

## 2. Service Boundaries

MVP 推荐模块化单体，避免过早微服务化：

```text
pia-server
├── api
├── project
├── indexing
├── code-analysis
├── graph
├── retrieval
├── agent
├── tool
├── model
├── evaluation
├── trace
├── security
└── common
```

当规模达到多团队/多租户后再拆：

- Agent Runtime
- Indexing Service
- Tool Gateway
- Model Gateway
- Evaluation Service

## 3. Core Data Flow

### Indexing

```text
Repository
 → File Scanner
 → Language Detector
 → AST Parser
 → Symbol Extractor
 → Relation Extractor
 → Graph Store
 → Chunker
 → Embedding
 → Vector Store
```

### Query

```text
User Query
 → Intent
 → Planner
 → Tool Calls
 → Evidence Collector
 → Reviewer
 → Answer Composer
 → SSE
```

## 4. Agent State

```json
{
  "runId": "uuid",
  "projectId": "uuid",
  "query": "...",
  "plan": [],
  "evidence": [],
  "toolCalls": [],
  "openQuestions": [],
  "finalAnswer": null
}
```

## 5. Failure Strategy

LLM failure：

- retry once for transient error
- fallback model if configured
- otherwise return actionable error

Tool failure：

- record error
- planner may retry once
- reviewer must know evidence is incomplete

Index incomplete：

- answer must明确“索引不完整”
- 禁止假装完整分析

## 6. Architecture Decision

MVP：

- Java 21
- Spring Boot 3.x
- Spring AI
- MySQL 8.0+
- Qdrant
- Redis
- Vue 3
- Docker Compose

版本必须在项目初始化时通过官方兼容矩阵最终锁定，不允许随意混用 Spring Boot/Spring Cloud 版本。
