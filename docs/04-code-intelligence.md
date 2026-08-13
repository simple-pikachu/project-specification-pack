# 04 Code Intelligence

## 1. Why Code Graph

代码不能只做 Vector RAG。必须保留：

- Symbol
- AST
- Call relation
- Type relation
- HTTP relation
- Import relation
- Dependency relation

## 2. Parsing

推荐：

- Java：JavaParser 或 Eclipse JDT
- JS/TS/Vue：Tree-sitter
- SQL：SQL parser；MVP 可先做表/字段/Mapper SQL 提取

Parser 必须输出统一中间模型：

```text
File
Module
Package
Class
Method
Field
Endpoint
Component
Function
SqlStatement
```

## 3. Relation Types

```text
CONTAINS
IMPORTS
CALLS
EXTENDS
IMPLEMENTS
USES_TYPE
HTTP_CALLS
MAPS_TO
QUERIES
UPDATES
DEPENDS_ON
ROUTES_TO
```

## 4. Code Graph MVP

推荐先存 PostgreSQL：

```text
graph_node
graph_edge
```

当关系规模和查询复杂度明显增加后，再评估 Neo4j 等专用 Graph DB。

MVP 不得因为“图数据库很酷”而增加不必要基础设施。

## 5. Chunk Strategy

不要按固定字符数切代码。

优先：

- class
- method
- component
- function
- API endpoint
- documentation section

Chunk metadata：

```json
{
  "projectId": "...",
  "filePath": "...",
  "language": "JAVA",
  "symbol": "OrderService.cancel",
  "startLine": 120,
  "endLine": 160,
  "module": "order-service"
}
```

## 6. Retrieval Strategy

推荐：

```text
Exact Search
 +
Keyword Search
 +
Vector Search
 +
Graph Expansion
 +
Rerank
```

代码问题优先级：

1. Symbol exact match
2. API/path exact match
3. Graph traversal
4. Keyword
5. Vector

## 7. Frontend/Backend Linking

识别：

```text
frontend API declaration
 ↓
HTTP method + path
 ↓
backend endpoint
```

进一步匹配：

- request schema
- response schema
- auth requirement

输出：

```json
{
  "frontend": "src/api/order.ts:12",
  "method": "POST",
  "path": "/order/cancel",
  "backend": "OrderController.cancel:88",
  "confidence": 0.99
}
```

## 8. Index Incremental Update

Git commit 后只重新解析：

- changed files
- impacted symbols
- impacted relations
- related chunks

必须支持 full reindex 和 incremental reindex。
