-- ============================================================
-- V1__init_schema.sql — PIA 初始数据库结构
-- ============================================================
--
-- 【Flyway 使用说明】
-- Flyway 在应用启动时自动执行此脚本。
-- 命名规则：V{版本号}__{描述}.sql
--   V = 版本标识符（固定）
--   {版本号} = 数字，必须递增（V1, V2, V3...）
--   __ = 两个下划线（分隔符）
--   {描述} = 可读描述
--
-- 执行记录存在 flyway_schema_history 表中，已执行的脚本不会重复执行。
-- 如果需要修改表结构，创建新的 V2__xxx.sql，不要修改 V1。
--
-- 【表结构设计说明】
-- PIA 的知识库由以下几类数据组成：
--
-- 1. 项目信息 (project)
--    → 记录导入了哪些项目、源码在哪里
--
-- 2. 文件索引 (project_file)
--    → 记录项目中所有文件，用于增量重新索引
--
-- 3. 代码符号 (code_symbol)
--    → 记录解析出的类/方法/字段/接口等"符号"
--    → 这是 Code Graph 的节点
--
-- 4. 调用关系图 (graph_edge)
--    → 记录符号之间的关系（调用/实现/继承等）
--    → 这是 Code Graph 的边
--
-- 5. 代码片段 (code_chunk)
--    → 按方法/类切分的代码块，用于向量嵌入
--    → embedding 存在 Qdrant，MySQL 只存元数据和 Qdrant point ID
--
-- 6. Agent 运行记录 (agent_run, tool_call, evidence)
--    → 记录每次 Agent 分析的完整过程，支持追溯和评估
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- 项目表
-- 记录导入的代码仓库信息
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project (
    id           VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '项目唯一ID（UUID）',
    name         VARCHAR(255)  NOT NULL COMMENT '项目名称',
    source_type  VARCHAR(64)   NOT NULL COMMENT '源码类型：LOCAL（本地目录）/ GIT（Git仓库）',
    source_path  TEXT          NOT NULL COMMENT '源码路径（本地绝对路径或Git URL）',
    default_branch VARCHAR(255) DEFAULT 'main' COMMENT '默认分支（Git仓库使用）',
    status       VARCHAR(32)   NOT NULL DEFAULT 'CREATED'
                 COMMENT '项目状态：CREATED/INDEXING/INDEXED/ERROR',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_project_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='项目信息表：记录导入的代码仓库';

-- ──────────────────────────────────────────────────────────
-- 文件表
-- 记录项目中每个文件的元数据
-- 用于：增量索引（只重新解析变更的文件）、文件语言检测
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS project_file (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '文件唯一ID',
    project_id       VARCHAR(36)  NOT NULL COMMENT '所属项目ID',
    path             TEXT         NOT NULL COMMENT '文件相对路径（相对于项目根目录）',
    language         VARCHAR(64)  COMMENT '编程语言：JAVA/TYPESCRIPT/VUE/SQL等',
    file_hash        VARCHAR(64)  COMMENT '文件内容 SHA-256 哈希，用于增量索引判断文件是否变更',
    size_bytes       BIGINT       COMMENT '文件大小（字节）',
    last_indexed_at  DATETIME     COMMENT '最近一次索引时间',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_file_project (project_id),
    INDEX idx_file_language (project_id, language),
    CONSTRAINT fk_file_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文件索引表：记录项目中每个文件的元数据，支持增量索引';

-- ──────────────────────────────────────────────────────────
-- 代码符号表（Code Graph 节点）
--
-- 【Agent 开发核心概念：代码符号】
-- 普通搜索：在文件中搜索关键词字符串。
-- 符号搜索：知道 OrderService.cancel() 是一个"方法"，
--           知道它的参数、返回类型、所在类、所在文件。
--
-- 这让 Agent 能回答：
--   "OrderService.cancel() 方法的参数是什么？"（精确查询，不是文本搜索）
--   "哪些类实现了 OrderRepository 接口？"（关系查询）
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS code_symbol (
    id             VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '符号唯一ID',
    project_id     VARCHAR(36)   NOT NULL COMMENT '所属项目ID',
    file_id        VARCHAR(36)   NOT NULL COMMENT '所在文件ID',
    symbol_type    VARCHAR(64)   NOT NULL
                            COMMENT '符号类型：CLASS/INTERFACE/METHOD/FIELD/FUNCTION/COMPONENT/ENDPOINT等',
    qualified_name TEXT          NOT NULL COMMENT '全限定名，如：com.example.order.OrderService.cancel',
    simple_name    VARCHAR(255)  NOT NULL COMMENT '简单名，如：cancel',
    start_line     INT           NOT NULL COMMENT '起始行号（1-based）',
    end_line       INT           NOT NULL COMMENT '结束行号（1-based）',
    signature      TEXT          COMMENT '方法签名，如：void cancel(String orderId)',
    visibility     VARCHAR(32)   COMMENT '访问修饰符：public/protected/private/package',
    is_static      BOOLEAN       DEFAULT FALSE COMMENT '是否静态',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_symbol_project (project_id),
    INDEX idx_symbol_file (file_id),
    INDEX idx_symbol_type (project_id, symbol_type),
    INDEX idx_symbol_name (project_id, simple_name(100)),
    CONSTRAINT fk_symbol_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
        CONSTRAINT fk_symbol_file FOREIGN KEY (file_id) REFERENCES project_file(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='代码符号表（Code Graph 节点）：类、方法、接口、字段等';

-- ──────────────────────────────────────────────────────────
-- 调用关系图表（Code Graph 边）
--
-- 【Agent 开发核心概念：代码关系图】
-- Code Graph = 节点（符号）+ 边（关系）
--
-- 边的类型（relation_type）：
--   CALLS       → 方法 A 调用方法 B
--   EXTENDS     → 类 A 继承类 B
--   IMPLEMENTS  → 类 A 实现接口 B
--   USES_TYPE   → 方法 A 使用类型 B（参数/返回值/局部变量）
--   HTTP_CALLS  → 前端 API 调用后端 Endpoint（跨端关联！）
--   MAPS_TO     → MyBatis Mapper 方法 → SQL 语句
--   QUERIES     → 方法 → 数据库表（读）
--   UPDATES     → 方法 → 数据库表（写）
--
-- 有了这张图，Agent 就能回答：
--   "修改 OrderService.cancel() 会影响哪些地方？"（图遍历，找所有 CALLS 这个方法的节点）
--   "订单取消功能的前端入口在哪里？"（沿 HTTP_CALLS 边反向找前端）
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS graph_edge (
        id               VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '边唯一ID',
    project_id       VARCHAR(36)  NOT NULL COMMENT '所属项目ID',
    source_node_id   VARCHAR(36)  NOT NULL COMMENT '起始节点ID（code_symbol.id）',
    target_node_id   VARCHAR(36)  NOT NULL COMMENT '目标节点ID（code_symbol.id）',
    relation_type    VARCHAR(64)  NOT NULL
                     COMMENT '关系类型：CALLS/EXTENDS/IMPLEMENTS/USES_TYPE/HTTP_CALLS/MAPS_TO/QUERIES/UPDATES',
    confidence       DECIMAL(5,4) DEFAULT 1.0000 COMMENT '关系置信度（0-1），AST 解析的通常为 1.0',
    metadata         JSON         COMMENT '附加信息，如 HTTP_CALLS 记录 method/path',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_edge_project (project_id),
    INDEX idx_edge_source (project_id, source_node_id),
    INDEX idx_edge_target (project_id, target_node_id),
    INDEX idx_edge_type (project_id, relation_type),
    CONSTRAINT fk_edge_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='代码关系图边表（Code Graph 边）：记录符号之间的调用/继承/实现等关系';

-- ──────────────────────────────────────────────────────────
-- 代码片段表（向量检索数据）
--
-- 【Agent 开发核心概念：RAG（检索增强生成）】
-- 代码按方法/类/函数切分为 chunk（片段）。
-- 每个 chunk 通过 Embedding 模型转为向量，存入 Qdrant。
-- 当用户问"和订单相关的代码"，Qdrant 用向量相似度找到最匹配的 chunk。
--
-- MySQL 存元数据（是哪个文件/方法/行号）。
-- Qdrant 存向量（用于语义搜索）。
-- 通过 qdrant_point_id 关联。
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS code_chunk (
    id                VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '片段唯一ID',
    project_id        VARCHAR(36)   NOT NULL COMMENT '所属项目ID',
    file_id           VARCHAR(36)   NOT NULL COMMENT '所在文件ID',
    symbol_id         VARCHAR(36)   COMMENT '对应的代码符号ID（如果 chunk 对应一个方法）',
    content           TEXT          NOT NULL COMMENT '代码片段原文',
    qdrant_point_id   VARCHAR(36)   COMMENT 'Qdrant 向量存储中的 point ID（关联向量数据）',
        chunk_type        VARCHAR(64)   COMMENT '片段类型：CLASS/METHOD/FUNCTION/COMPONENT/ENDPOINT',
    start_line        INT           COMMENT '起始行号',
    end_line          INT           COMMENT '结束行号',
    metadata          JSON          COMMENT '附加元数据（语言/模块/符号名等），同步存到 Qdrant payload',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk_project (project_id),
    INDEX idx_chunk_file (file_id),
    INDEX idx_chunk_symbol (symbol_id),
    CONSTRAINT fk_chunk_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_file FOREIGN KEY (file_id) REFERENCES project_file(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='代码片段表：按方法/类切分的代码块，qdrant_point_id 关联向量数据库中的向量';

-- ──────────────────────────────────────────────────────────
-- Agent 运行表
--
-- 【Agent 开发核心概念：Agent Run】
-- 每次用户提交一个问题，就创建一个 Agent Run。
-- Agent Run 记录：
--   - 输入：用户的问题
--   - 过程：调用了哪些 Tool，每个 Tool 的结果
--   - 输出：最终的分析报告
--   - 状态：RUNNING/COMPLETED/FAILED
--   - Token 消耗：成本追踪
--
-- 有了这张表，就能：
--   - 查询历史分析记录
--   - 复盘某次分析用了哪些工具
--   - 评估 Agent 性能（耗时/准确率/Token 消耗）
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_run (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT 'Agent Run 唯一ID',
    project_id    VARCHAR(36)   NOT NULL COMMENT '分析的目标项目ID',
    trace_id      VARCHAR(36)   NOT NULL COMMENT '追踪ID（用于分布式追踪，与 Span 关联）',
    query         TEXT          NOT NULL COMMENT '用户输入的原始问题',
    status        VARCHAR(32)   NOT NULL DEFAULT 'PENDING'
                  COMMENT '状态：PENDING/PLANNING/EXECUTING/REVIEWING/COMPLETED/FAILED',
    plan          JSON          COMMENT 'Planner 生成的调查计划（结构化JSON）',
    final_answer  LONGTEXT      COMMENT '最终输出的分析报告（Markdown格式）',
    started_at    DATETIME      COMMENT 'Agent 开始执行时间',
    finished_at   DATETIME      COMMENT 'Agent 执行完成时间',
        token_usage   JSON          COMMENT 'Token 消耗统计：{prompt_tokens, completion_tokens, total_tokens}',
    error_message TEXT          COMMENT '失败时的错误信息',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_run_project (project_id),
    INDEX idx_run_status (status),
    INDEX idx_run_created (created_at),
    CONSTRAINT fk_run_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent运行记录表：每次分析请求的完整生命周期记录';

-- ──────────────────────────────────────────────────────────
-- Tool 调用记录表
--
-- 【Agent 开发核心概念：Tool Trace（工具追踪）】
-- Agent 的每次工具调用都必须记录，原因：
-- 1. 可追溯：出了问题能查到 Agent 调用了哪些工具，入参是什么
-- 2. 可评估：统计各 Tool 的成功率、耗时
-- 3. 可审计：安全要求，记录 Agent 做了什么操作
-- 4. 可调试：复现某次分析的完整过程
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tool_call (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT 'Tool调用唯一ID',
    run_id          VARCHAR(36)   NOT NULL COMMENT '所属 Agent Run ID',
    tool_name       VARCHAR(128)  NOT NULL COMMENT '工具名称：code_search/code_read/graph_neighbors等',
    input_params    JSON          COMMENT '工具调用的输入参数',
    output_summary  TEXT          COMMENT '工具返回结果的摘要（原始结果可能很大，只存摘要）',
    status          VARCHAR(32)   NOT NULL DEFAULT 'PENDING'
                    COMMENT '状态：PENDING/SUCCESS/FAILED/TIMEOUT',
    latency_ms      BIGINT        COMMENT '耗时（毫秒）',
    error_message   TEXT          COMMENT '失败时的错误信息',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tool_run (run_id),
    INDEX idx_tool_name (tool_name),
    CONSTRAINT fk_tool_run FOREIGN KEY (run_id) REFERENCES agent_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Tool调用记录表：Agent每次工具调用的输入/输出/耗时追踪';

-- ──────────────────────────────────────────────────────────
-- 证据表
--
-- 【Agent 开发核心概念：Evidence（证据）】
-- 这是 PIA 与普通聊天 AI 的核心区别。
-- 普通 AI：生成一个可能正确也可能是幻觉的回答。
-- PIA Agent：每个结论必须绑定证据（具体文件、具体行号、具体代码片段）。
--
-- 例如：
-- 结论："订单取消需要修改 OrderService.cancel() 方法"
-- 证据：{filePath: "src/OrderService.java", startLine: 88, endLine: 120,
--        excerpt: "public void cancel(String orderId) {...}", confidence: 0.99}
--
-- Reviewer Agent 会检查每个结论是否有对应证据，没有证据的结论会被标记。
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS evidence (
    id           VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '证据唯一ID',
    run_id       VARCHAR(36)   NOT NULL COMMENT '所属 Agent Run ID',
    evidence_type VARCHAR(64)  NOT NULL
                 COMMENT '证据类型：CODE_SYMBOL/FILE/API_ENDPOINT/DB_TABLE/GIT_COMMIT',
    file_path    TEXT          COMMENT '证据所在文件路径',
    symbol       VARCHAR(255)  COMMENT '相关符号名（方法名/类名等）',
    start_line   INT           COMMENT '证据起始行',
        end_line     INT           COMMENT '证据结束行',
    excerpt      TEXT          COMMENT '代码片段摘录（直接引用，不是 AI 生成）',
    confidence   DECIMAL(5,4)  DEFAULT 1.0000 COMMENT '置信度（0-1）',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_evidence_run (run_id),
    CONSTRAINT fk_evidence_run FOREIGN KEY (run_id) REFERENCES agent_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='证据表：Agent每个关键结论必须绑定的代码证据（文件路径+行号+代码片段）';

-- ──────────────────────────────────────────────────────────
-- 评估用例表
-- 用于持续评估 Agent 质量，记录"标准答案"
-- Phase 5 的核心：建立测试集，防止 Agent 退化
-- ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS evaluation_case (
    id           VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '用例唯一ID',
    project_id   VARCHAR(36)   NOT NULL COMMENT '关联的项目ID',
    name         VARCHAR(255)  NOT NULL COMMENT '用例名称',
        category     VARCHAR(64)   COMMENT '用例分类：CODE_NAV/API_MAPPING/IMPACT/BUG/REQUIREMENT',
    query        TEXT          NOT NULL COMMENT '用户问题',
    expected     JSON          NOT NULL COMMENT '期望输出（关键文件/方法/结论的标准答案）',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_project (project_id),
    CONSTRAINT fk_eval_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='评估用例表：记录标准测试问题和期望答案，用于持续评估Agent质量';
