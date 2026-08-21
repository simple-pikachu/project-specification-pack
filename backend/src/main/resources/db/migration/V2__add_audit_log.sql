-- ============================================================
-- V2__add_audit_log.sql — 审计日志表
-- ============================================================
--
-- 【Phase 6：为什么需要审计日志表？】
--
-- Phase 5 的 AuditLogger 只写应用日志（log.info），
-- 日志文件会滚动丢失，无法支持：
--   - 按用户/项目/时间维度查询"谁做了什么"
--   - 安全事件溯源（Prompt Injection 尝试记录）
--   - 合规报告（谁在什么时间访问了哪个代码仓库）
--
-- V2 迁移：新增 audit_log 表，升级为持久化审计。
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_log (
    id             VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '审计记录唯一ID',
    event_type     VARCHAR(64)   NOT NULL
                   COMMENT '事件类型：PROJECT_ACCESS/AGENT_RUN/TOOL_CALL/INDEXING/SECURITY_VIOLATION',
    user_id        VARCHAR(128)  COMMENT '操作用户（Phase 6 接入 OAuth2 后填充，MVP 为 anonymous）',
    project_id     VARCHAR(36)   COMMENT '关联项目ID（如果有）',
    agent_run_id   VARCHAR(36)   COMMENT '关联 Agent Run ID（如果有）',
    resource       VARCHAR(255)  COMMENT '被操作的资源描述（路径/端点/工具名）',
     action         VARCHAR(64)   COMMENT '操作动作（READ/INDEX/RUN/QUERY）',
    result         VARCHAR(32)   COMMENT '结果：SUCCESS/DENIED/ERROR',
    detail         JSON          COMMENT '详细信息（可扩展，如错误消息/IP/User-Agent）',
    ip_address     VARCHAR(64)   COMMENT '请求来源 IP',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计记录时间',
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_project (project_id),
    INDEX idx_audit_type (event_type),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_run (agent_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='审计日志表：记录所有关键操作，支持安全溯源和合规审计';
