# ADR-001 MVP 使用模块化单体

## Decision

MVP 使用 Spring Boot 模块化单体，不拆微服务。

## Reasons

1. 项目处于学习和快速迭代阶段。
2. Agent Runtime、Tool、Graph、Retrieval 高度相关。
3. 过早拆分会把精力消耗在网络、部署和一致性上。
4. 模块边界清晰后可以平滑拆分。

## Consequence

代码必须严格按领域模块隔离依赖，禁止互相直接访问内部实现。
