# 03 Agent Design

## 1. Agent Roles

### Analysis Agent

负责需求理解和问题分类。

### Planner

负责生成调查计划。

### Executor

负责调用 Tools。

### Reviewer

负责证据完整性和结论一致性。

### Answer Composer

负责生成最终结构化报告。

## 2. Workflow

```text
START
 ↓
Intent Analysis
 ↓
Project Context
 ↓
Planning
 ↓
Tool Execution Loop
 ↓
Evidence Consolidation
 ↓
Reviewer
 ├─ insufficient → Planner refinement
 └─ sufficient
 ↓
Answer Composer
 ↓
END
```

最大循环次数：5。

## 3. Planner Output

必须结构化：

```json
{
  "goal": "分析订单取消功能影响",
  "tasks": [
    {
      "id": "T1",
      "type": "FRONTEND_SEARCH",
      "description": "定位订单列表和现有操作按钮"
    }
  ],
  "expectedEvidence": [
    "order-list-page",
    "order-api",
    "order-controller",
    "order-service"
  ]
}
```

## 4. Tool Contract

每个 Tool 必须：

- name
- description
- inputSchema
- outputSchema
- permission
- timeout
- retryPolicy
- auditPolicy

## 5. MVP Tools

### code_search

输入：

```json
{
  "projectId": "uuid",
  "query": "OrderController",
  "language": "JAVA",
  "limit": 20
}
```

### code_read

输入：

```json
{
  "projectId": "uuid",
  "filePath": "...",
  "startLine": 100,
  "endLine": 160
}
```

### graph_neighbors

输入：

```json
{
  "projectId": "uuid",
  "nodeId": "...",
  "direction": "BOTH",
  "relationTypes": ["CALLS", "USES", "HTTP_CALLS"]
}
```

### api_search

支持 method/path/keyword。

### schema_search

只读数据库元数据，不执行任意 SQL。

### git_search

搜索 commit、作者、时间和文件变更。

### rag_search

查询项目文档和代码语义片段。

## 6. Reviewer Rules

Reviewer 必须检查：

1. 每个重要结论是否有 Evidence。
2. Evidence 是否真的支持结论。
3. 是否遗漏前端/后端/DB。
4. 是否区分“事实”和“建议”。
5. 是否存在未验证假设。
6. 是否出现虚构路径/方法。
7. 是否明确索引缺失。

## 7. Final Answer Schema

```json
{
  "summary": "...",
  "requirements": [],
  "currentBehavior": [],
  "impact": [],
  "implementationPlan": [],
  "apiChanges": [],
  "databaseChanges": [],
  "filesToChange": [],
  "tests": [],
  "risks": [],
  "openQuestions": [],
  "evidence": []
}
```
