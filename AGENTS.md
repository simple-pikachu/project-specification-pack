# Coding Agent Development Contract

## 1. 开始工作前

必须按顺序阅读：

1. `README.md`
2. `docs/00-project-overview.md`
3. `docs/01-product-requirements.md`
4. `docs/02-system-architecture.md`
5. `docs/03-agent-design.md`
6. `docs/04-code-intelligence.md`
7. `docs/05-technical-stack.md`
8. `docs/06-api-and-data-model.md`
9. `docs/07-implementation-plan.md`
10. `docs/08-testing-and-evaluation.md`
11. `docs/09-deployment-and-operations.md`
12. `docs/10-security.md`
13. `docs/12-acceptance-checklist.md`

## 2. 强制规则

- 不得擅自替换核心技术栈。
- 不得把代码 RAG 简化成纯 Vector Search。
- 不得让 LLM 直接访问文件系统、数据库或 Shell。
- 所有外部能力必须通过 Tool/MCP Adapter。
- 所有 Tool 必须声明 input schema、output schema、权限、超时和错误策略。
- 所有 Agent Workflow 必须产生 traceId。
- 所有关键结论必须包含 Evidence。
- 所有数据库变更必须提供 migration。
- 所有 REST API 必须有 Controller/Service 测试。
- 所有 Agent Tool 必须有 Tool 单元测试。
- 所有核心 Workflow 必须有 Evaluation Case。
- 不得为了“让测试通过”删除验收规则。
- 不得把异常吞掉后返回成功。
- 不得把 secrets 写入源码、日志或 Prompt。
- MVP 默认只读，不允许修改用户代码。

## 3. 开发方式

每次只实现一个 Phase。

完成一个 Phase 后：

1. 编译
2. 单元测试
3. 集成测试
4. 更新文档
5. 检查日志
6. 检查安全规则
7. 更新 `12-acceptance-checklist.md` 中对应项

## 4. 代码质量

后端必须遵循：

- Controller 不写业务逻辑。
- Service 负责业务编排。
- Repository/Mapper 负责持久化。
- Agent Tool 不直接依赖 Controller。
- LLM Provider 通过统一 Model Gateway 抽象。
- Tool schema 必须稳定、版本化。
- 所有异步操作必须定义超时、取消和失败策略。

## 5. 禁止行为

- 禁止在没有读取代码证据的情况下声称“项目中存在某实现”。
- 禁止伪造文件路径、方法名、API。
- 禁止在数据库查询 Tool 中开放任意 SQL 执行。
- 禁止默认允许 DELETE/UPDATE。
- 禁止把完整源代码发送到无必要的第三方模型。
