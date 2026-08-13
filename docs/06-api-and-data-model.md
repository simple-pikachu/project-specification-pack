# 06 API and Data Model

## 1. REST API

### POST /api/projects

创建项目。

### GET /api/projects/{projectId}

项目详情。

### POST /api/projects/{projectId}/index

启动索引。

### GET /api/projects/{projectId}/index/status

索引状态。

### POST /api/agent/runs

创建 Agent Run。

Request：

```json
{
  "projectId": "uuid",
  "query": "订单取消功能需要修改哪些地方？"
}
```

Response：

```json
{
  "runId": "uuid",
  "traceId": "uuid"
}
```

### GET /api/agent/runs/{runId}/events

SSE。

Event types：

- run.started
- planning.started
- tool.started
- tool.completed
- evidence.added
- review.completed
- answer.delta
- run.completed
- run.failed

## 2. Database Tables

### project

```text
id VARCHAR(36) PK
name VARCHAR(255)
source_type VARCHAR(64)
source_path TEXT
default_branch VARCHAR(255)
created_at DATETIME
updated_at DATETIME
```

### project_file

```text
id VARCHAR(36) PK
project_id VARCHAR(36)
path TEXT
language VARCHAR(64)
hash VARCHAR(64)
size BIGINT
last_indexed_at DATETIME
```

### code_symbol

```text
id VARCHAR(36) PK
project_id VARCHAR(36)
file_id VARCHAR(36)
symbol_type VARCHAR(64)
qualified_name TEXT
start_line INT
end_line INT
signature TEXT
```

### graph_edge

```text
id VARCHAR(36) PK
project_id VARCHAR(36)
source_node_id VARCHAR(36)
target_node_id VARCHAR(36)
relation_type VARCHAR(64)
metadata JSON
```

### code_chunk

```text
id VARCHAR(36) PK
project_id VARCHAR(36)
file_id VARCHAR(36)
symbol_id VARCHAR(36)
content TEXT
qdrant_point_id VARCHAR(36)
metadata JSON
```

注：向量 embedding 存储在 Qdrant，MySQL 侧记录对应的 Qdrant point id 用于关联查询。

### agent_run

```text
id VARCHAR(36) PK
project_id VARCHAR(36)
query TEXT
status VARCHAR(32)
started_at DATETIME
finished_at DATETIME
token_usage JSON
```

### tool_call

```text
id VARCHAR(36) PK
run_id VARCHAR(36)
tool_name VARCHAR(128)
input JSON
output_summary TEXT
status VARCHAR(32)
latency_ms BIGINT
created_at DATETIME
```

### evidence

```text
id VARCHAR(36) PK
run_id VARCHAR(36)
type VARCHAR(64)
file_path TEXT
symbol TEXT
start_line INT
end_line INT
excerpt TEXT
confidence DECIMAL(5,4)
```

### evaluation_case

```text
id VARCHAR(36) PK
project_id VARCHAR(36)
name VARCHAR(255)
query TEXT
expected JSON
created_at DATETIME
```

## 3. Indexes

必须建立：

- project_id
- path
- qualified_name
- relation_type
- run_id
- timestamps

向量索引由 Qdrant 内部管理，MySQL 侧无需建向量索引。
