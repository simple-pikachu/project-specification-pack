package com.example.pia.security.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 审计日志实体，对应数据库表 {@code audit_log}
 *
 * <p>【Phase 6：审计日志从 log 升级为 DB】
 *
 * <p>Phase 5 的 {@link com.example.pia.security.AuditLogger} 只写应用日志，
 * Phase 6 将审计记录持久化到数据库，支持：
 * <ul>
 *   <li>按用户/项目/时间维度查询操作历史</li>
 *   <li>安全事件溯源（Prompt Injection 尝试记录）</li>
 *   <li>合规审计报告</li>
 * </ul>
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private EventType eventType;

    /** 操作用户（接入 OAuth2 后填充，MVP 为 "anonymous"） */
    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "project_id", length = 36)
    private String projectId;

        @Column(name = "agent_run_id", length = 36)
    private String agentRunId;

    /** 被操作的资源（路径/端点/工具名） */
    @Column(length = 255)
    private String resource;

    @Column(length = 64)
    private String action;

    /** 操作结果：SUCCESS / DENIED / ERROR */
    @Column(length = 32)
    private String result;

    /** 详细信息（JSON，可扩展） */
    @Column(columnDefinition = "JSON")
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum EventType {
        PROJECT_ACCESS,      // 项目读取/列表
        INDEXING,            // 索引触发
        AGENT_RUN,           // Agent 分析任务创建
        TOOL_CALL,           // Agent 工具调用
        SECURITY_VIOLATION   // 安全异常（路径穿越/Prompt Injection）
    }
}
