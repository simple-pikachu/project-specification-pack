package com.example.pia.agent.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Tool 调用记录实体
 *
 * <p>【Agent 开发：为什么每个 Tool 调用都要记录？】
 *
 * <p>规格文档要求"所有 Tool 可追踪"——这是 Agent 与普通代码最大的不同：
 * <ul>
 *   <li>普通代码：函数调用是内部的，出错看日志</li>
 *   <li>Agent 的 Tool 调用：必须记录到数据库，因为：
 *     <ol>
 *       <li>可审计：出了事能查到 Agent 做了什么（安全要求）</li>
 *       <li>可复盘：分析某次 Agent Run 的质量问题</li>
 *       <li>可评估：统计各工具的成功率和耗时</li>
 *       <li>可调试：复现 Agent 某次调用的完整上下文</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>每次 Tool 调用会记录：工具名、输入参数、输出摘要、耗时、状态。
 * 注意：只存输出"摘要"，不存完整结果（完整结果可能很大，且含代码全文）。
 */
@Entity
@Table(name = "tool_call", indexes = {
    @Index(name = "idx_tool_run", columnList = "run_id"),
    @Index(name = "idx_tool_name", columnList = "tool_name")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    @Id
    @Column(length = 36)
    private String id;

        @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    /** 工具名称（code_search / graph_neighbors / schema_search / git_search / rag_search） */
    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    /** 工具调用的输入参数（JSON） */
    @Column(name = "input_params", columnDefinition = "JSON")
    private String inputParams;

    /** 输出结果摘要（不存完整结果，只存摘要） */
    @Column(name = "output_summary", columnDefinition = "TEXT")
    private String outputSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ToolCallStatus status;

    /** 耗时（毫秒），用于性能分析 */
    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
        protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ToolCallStatus.PENDING;
    }

    public enum ToolCallStatus {
        PENDING,  // 等待执行
        SUCCESS,  // 执行成功
        FAILED,   // 执行失败
        TIMEOUT   // 超时
    }
}
