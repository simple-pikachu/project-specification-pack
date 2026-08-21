package com.example.pia.agent.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Agent 运行记录实体
 *
 * <p>【Agent 开发：AgentRun 是什么？】
 *
 * <p>每次用户提交一个自然语言问题，就创建一个 AgentRun。
 * AgentRun 记录了这次分析的完整生命周期：
 * <ul>
 *   <li>输入：用户的原始问题</li>
 *   <li>计划：Planner 生成的调查步骤（JSON）</li>
 *   <li>过程：每个 Tool 调用（在 ToolCall 表中）</li>
 *   <li>证据：Agent 找到的代码证据（在 Evidence 表中）</li>
 *   <li>输出：最终的结构化分析报告</li>
 *   <li>状态：PENDING → PLANNING → EXECUTING → REVIEWING → COMPLETED/FAILED</li>
 * </ul>
 *
 * <p>有了 AgentRun，可以：
 * <ul>
 *   <li>前端通过 SSE 实时推送进度</li>
 *   <li>复盘某次分析：Planner 做了什么计划？调用了哪些工具？结论基于什么证据？</li>
 *   <li>评估 Agent 质量：平均耗时多少？Token 消耗多少？</li>
 * </ul>
 */
@Entity
@Table(name = "agent_run")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRun {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
        private String projectId;

    /**
     * 追踪 ID（用于 OpenTelemetry 分布式追踪）
     *
     * <p>TraceId 将整个请求链路（HTTP 入口 → Planner → Tool 调用 → LLM → 响应）
     * 串联成一条可追踪的链，在 Jaeger/Grafana 中可视化调用链路。
     */
    @Column(name = "trace_id", nullable = false, length = 36)
    private String traceId;

    /** 用户输入的原始问题 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    /**
     * Agent 状态（状态机）
     *
     * <p>状态转换：
     * <pre>
     * PENDING → PLANNING → EXECUTING → REVIEWING → COMPLETED
     *                                             ↘ FAILED（任何阶段失败）
     * </pre>
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    /**
     * Planner 生成的调查计划（JSON 格式）
     *
     * <p>计划结构：
     * <pre>
     * {
     *   "goal": "分析订单取消功能影响",
     *   "tasks": [
     *     {"id": "T1", "type": "CODE_SEARCH", "query": "cancel order"}
     *   ],
          *   "expectedEvidence": ["OrderService", "OrderController"]
     * }
     * </pre>
     */
    @Column(columnDefinition = "JSON")
    private String plan;

    /** 最终输出的分析报告（Markdown 格式，含证据引用） */
    @Column(name = "final_answer", columnDefinition = "LONGTEXT")
    private String finalAnswer;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * Token 消耗统计
     *
     * <p>{"promptTokens": 1234, "completionTokens": 567, "totalTokens": 1801}
     * 用于成本追踪和 Token Budget 控制。
     */
    @Column(name = "token_usage", columnDefinition = "JSON")
    private String tokenUsage;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
        private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = RunStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RunStatus {
        PENDING,    // 已创建，等待执行
        PLANNING,   // Planner 正在生成调查计划
        EXECUTING,  // Executor 正在调用工具
        REVIEWING,  // Reviewer 正在检查证据完整性
        COMPLETED,  // 分析完成
        FAILED      // 失败（任何阶段）
    }
}
