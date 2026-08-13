# 12 Acceptance Checklist

## Phase 0 ✅ 代码已实现（需 docker compose up 验证）

- [x] Backend 代码骨架：PiaApplication / pom.xml / Dockerfile
- [x] Frontend 代码骨架：Vue3 / Vite / Dockerfile
- [x] MySQL 8.0+ 配置（替代 PostgreSQL）
- [x] Redis 配置
- [x] Flyway V1__init_schema.sql（8 张核心表）
- [ ] CI 配置（待补充 GitHub Actions）
- [ ] docker compose up 运行时验证（需本地执行）

## Phase 1 ✅ 代码已实现

- [x] Project CRUD（ProjectController / ProjectService / ProjectRepository）
- [x] Repository 本地目录导入（IndexingService.startFullIndex）
- [x] 文件扫描（FileScanner：递归遍历 + Hash + 语言检测）
- [x] 增量索引设计（syncFiles：哈希比对，只更新变更文件）
- [x] 路径安全检查（PathSecurityValidator + PathSecurityValidatorTest）
- [x] LanguageDetector（14 种语言/扩展名映射）
- [ ] 路径越权测试用例运行验证（需本地执行）

## Phase 2 ✅ 代码已实现

- [x] Java 符号提取（JavaAstParser：CLASS/INTERFACE/METHOD/FIELD/ENDPOINT）
- [ ] JS/TS/Vue 符号提取（Tree-sitter 集成待实现，当前 Java 优先）
- [x] Controller/Service/Endpoint 识别（@PostMapping 等注解检测）
- [x] Caller/callee 关系提取（CALLS 边，PendingEdge 机制）
- [x] GraphService（图构建 + getNeighbors 查询）
- [x] GraphController（/symbols + /symbols/{id}/neighbors）
- [x] JavaAstParserTest（5 个场景）
- [ ] 前后端 HTTP_CALLS 关联（需 TS 解析器，Phase 2 后期）

## Phase 3 ✅ 代码已实现

- [x] Exact Search（CodeSymbolRepository.findByQualifiedName）
- [x] Keyword Search（searchBySimpleName）
- [x] Vector Search（EmbeddingService → Spring AI VectorStore → Qdrant）
- [x] Graph Expansion（RetrievalService 混合检索第 4 步）
- [x] Evidence 提取（RetrievalResult 含 filePath/startLine/endLine/excerpt）
- [x] ChunkingService（按符号边界切片，携带 metadata）
- [x] ChunkingServiceTest（3 个场景）
- [ ] 检索 Evaluation 数据集（需接入真实项目后构建）

## Phase 4 ✅ 代码已实现

- [x] Planner（PlannerService：Spring AI ChatClient + System Prompt）
- [x] Executor（ExecutorService：Tool 调用 + 超时控制 + Trace 记录）
- [x] Tool Registry（ToolRegistry：自动注册 + 按任务类型动态暴露）
- [x] code_search Tool（CodeSearchTool → RetrievalService）
- [x] Reviewer（ReviewerService：7 项证据完整性检查）
- [x] SSE 流式输出（AgentRuntime + Sinks.Many + AgentController）
- [x] Trace（tool_call 表：每次 Tool 调用写入状态/耗时/摘要）
- [x] AgentRuntimeTest（3 个场景）
- [ ] 100 Evaluation Cases（需接入真实项目后构建）

## Phase 5

- [ ] Regression evaluation
- [ ] Prompt versioning
- [ ] Model versioning
- [ ] Token metrics
- [ ] Latency metrics
- [ ] Failure recovery
- [ ] Quality gates

## Phase 6

- [ ] Authentication
- [ ] RBAC
- [ ] Project authorization
- [ ] Tool authorization
- [ ] Audit log
- [ ] Prompt injection tests
- [ ] Path traversal tests
- [ ] Secret leakage tests
- [ ] Backup
- [ ] Restore drill
- [ ] Production deployment
- [ ] Monitoring

## Release Gate

必须全部满足：

- [ ] No known P0/P1 defects
- [ ] Build green
- [ ] Tests green
- [ ] Evaluation gate green
- [ ] Security gate green
- [ ] Deployment rehearsal complete
- [ ] Rollback verified
- [ ] Documentation updated
