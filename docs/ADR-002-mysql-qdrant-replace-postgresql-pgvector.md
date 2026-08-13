# ADR-002 使用 MySQL 8.0+ + Qdrant 替换 PostgreSQL + pgvector

## Decision

将关系型数据库从 PostgreSQL 切换为 MySQL 8.0+，向量存储从 pgvector 切换为独立的 Qdrant 服务。

## Reasons

1. 企业内部已有 MySQL 运维体系，降低基础设施引入成本。
2. MySQL 8.0 支持 JSON 类型（满足 metadata/JSONB 等效需求）和窗口函数，满足 Code Graph 关系存储需求。
3. pgvector 作为 PostgreSQL 扩展，与 MySQL 不兼容，必须另选向量存储方案。
4. Qdrant 是 Spring AI 原生支持的向量数据库，Docker 单容器启动，开源且无需额外 License。
5. 向量检索与关系型数据职责分离，可单独扩容，符合项目长期架构方向。

## Trade-offs

- 引入 Qdrant 增加一个基础设施组件，Docker Compose 多一个服务。
- MySQL UUID 主键需用 VARCHAR(36)，不如 PostgreSQL UUID 类型原生；性能可接受。
- MySQL JSON 类型不支持 GIN 索引，metadata 复杂查询需设计合理的列结构；MVP 阶段影响有限。
- Qdrant 不可用时需降级为关键词检索（已在 09-deployment-and-operations.md 明确降级策略）。

## Consequence

- 所有 Flyway migration 使用 MySQL 语法。
- ORM/MyBatis 配置指向 MySQL。
- 向量写入/检索通过 Spring AI VectorStore（Qdrant 实现）。
- MySQL 侧 code_chunk 表存储 qdrant_point_id 用于关联。
- 禁止在 MySQL 中存储 embedding 向量原始数据。
