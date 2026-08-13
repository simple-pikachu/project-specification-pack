# 01 Product Requirements

## 1. Functional Requirements

### FR-001 Project Import

支持导入本地 Git Repository 或指定目录。

输入：

- projectName
- sourceType
- sourcePath
- branch/commit（可选）

验收：

- 能创建 Project。
- 能生成唯一 projectId。
- 不能访问 project root 之外的文件。

### FR-002 Repository Scan

扫描：

- Java
- Kotlin（可选）
- JavaScript
- TypeScript
- Vue
- SQL
- YAML
- JSON
- Markdown
- Maven/Gradle
- package.json

输出：

- File
- Module
- Symbol
- API
- Dependency
- Config

### FR-003 Code Graph

必须支持：

- file → symbol
- class → method
- method → method
- controller → service
- service → mapper
- mapper → SQL
- frontend page → component
- frontend api → backend endpoint
- endpoint → request/response type
- symbol → file

### FR-004 Hybrid Search

支持：

- exact symbol search
- path search
- keyword search
- semantic search
- metadata filtering
- graph expansion

### FR-005 Agent Analysis

Agent 输入自然语言需求，输出结构化 Analysis Report。

### FR-006 Evidence

每个关键结论至少包含：

- evidenceType
- filePath
- symbol
- startLine
- endLine
- excerpt/hash
- confidence

### FR-007 Streaming

使用 SSE 输出：

- planning
- tool call
- tool result summary
- analysis
- final answer

### FR-008 Trace

每个请求生成：

- traceId
- span
- model
- token
- latency
- tool
- status

## 2. Non-functional Requirements

- API P95 ≤ 500ms（不含 LLM）。
- Tool 默认 timeout 10s。
- 单 Agent Run 最大 30 个 Tool Calls。
- 单次上下文必须受 token budget 控制。
- 所有用户输入进行长度限制。
- 日志禁止输出 secrets。

## 3. Acceptance Format

最终回答必须包含：

1. Requirement Understanding
2. Current Behavior
3. Impact Analysis
4. Evidence
5. Implementation Plan
6. API Changes
7. Database Changes
8. Code Changes
9. Test Plan
10. Risks
11. Open Questions
