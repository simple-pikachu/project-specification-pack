# Project Intelligence Agent

> 面向已有前端 + 后端代码仓库的企业级项目智能分析 Agent。
>
> 目标：让 Agent 能理解 Project A 的前后端代码、API、数据库结构和依赖关系，对需求、Bug、影响范围和实现方案进行可验证的精准分析，并在后续版本支持代码修改、测试和 Git 交付。

## 文档定位

本仓库不是普通设计说明，而是 **可执行项目规格（Executable Specification）**。Coding Agent 必须先阅读 `AGENTS.md`，再按 `docs/` 顺序实施。

## 文档目录

| 文档 | 内容 |
|---|---|
| 00-project-overview.md | 项目目标、边界、用户、核心场景 |
| 01-product-requirements.md | PRD、用户故事、功能需求、验收标准 |
| 02-system-architecture.md | 总体架构、模块、数据流 |
| 03-agent-design.md | Agent Runtime、Planner、Executor、Reviewer、Tools |
| 04-code-intelligence.md | AST、代码索引、Code Graph、前后端关联、RAG |
| 05-technical-stack.md | 技术选型、版本原则、部署依赖 |
| 06-api-and-data-model.md | REST/SSE API、数据模型、数据库设计 |
| 07-implementation-plan.md | 分阶段开发任务、目录、DoD |
| 08-testing-and-evaluation.md | 测试、Agent Evaluation、质量指标 |
| 09-deployment-and-operations.md | Docker、配置、部署、监控、故障处理 |
| 10-security.md | 认证、授权、Tool 权限、Prompt Injection 防护 |
| 11-agent-development-guide.md | Coding Agent 强制执行规则 |
| 12-acceptance-checklist.md | 发布前完整验收清单 |

## 推荐开发顺序

Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6。

MVP 目标是完成：

> 项目导入 → 代码解析 → Code Graph → 混合检索 → Agent 规划 → 多 Tool 调查 → 前后端影响分析 → 带证据的实现方案 → SSE 流式输出。

## 核心原则

1. **证据优先**：Agent 的结论必须尽可能绑定文件、符号、行号或 API/数据库证据。
2. **结构优先于向量**：代码关系由 AST + Code Graph 表达，Vector RAG 作为补充。
3. **LLM 不负责权限**：权限必须由平台和 Tool 层强制执行。
4. **确定性 Workflow 优先**：能用确定性流程解决的问题，不交给自由 Agent。
5. **所有 Tool 可追踪**：Tool 调用、参数、结果摘要、耗时和错误必须进入 Trace。
6. **所有关键结论可验证**：Reviewer 必须检查引用证据与结论的一致性。
7. **默认只读**：MVP 禁止 Agent 修改 Project A 源码；代码修改属于 V0.3。
