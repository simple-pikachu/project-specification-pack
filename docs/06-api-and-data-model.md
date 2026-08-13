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
id UUID PK
name VARCHAR
source_type VARCHAR
source_path TEXT
default_branch VARCHAR
created_at TIMESTAMP
updated_at TIMESTAMP
```

### project_file

```text
id UUID PK
project_id UUID
path TEXT
language VARCHAR
hash VARCHAR
size BIGINT
last_indexed_at TIMESTAMP
```

### code_symbol

```text
id UUID PK
project_id UUID
file_id UUID
symbol_type VARCHAR
qualified_name TEXT
start_line INT
end_line INT
signature TEXT
```

### graph_edge

```text
id UUID PK
project_id UUID
source_node_id UUID
target_node_id UUID
relation_type VARCHAR
metadata JSONB
```

### code_chunk

```text
id UUID PK
project_id UUID
file_id UUID
symbol_id UUID
content TEXT
embedding VECTOR
metadata JSONB
```

### agent_run

```text
id UUID PK
project_id UUID
query TEXT
status VARCHAR
started_at TIMESTAMP
finished_at TIMESTAMP
token_usage JSONB
```

### tool_call

```text
id UUID PK
run_id UUID
tool_name VARCHAR
input JSONB
output_summary TEXT
status VARCHAR
latency_ms BIGINT
created_at TIMESTAMP
```

### evidence

```text
id UUID PK
run_id UUID
type VARCHAR
file_path TEXT
symbol TEXT
start_line INT
end_line INT
excerpt TEXT
confidence NUMERIC
```

### evaluation_case

```text
id UUID PK
project_id UUID
name VARCHAR
query TEXT
expected JSONB
created_at TIMESTAMP
```

## 3. Indexes

必须建立：

- project_id
- path
- qualified_name
- relation_type
- run_id
- vector index
- timestamps

Vector index 的具体类型根据 pgvector 版本和数据规模确定。
