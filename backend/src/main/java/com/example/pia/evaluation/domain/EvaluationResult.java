package com.example.pia.evaluation.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 评估结果实体
 *
 * <p>记录每次 Evaluation 运行的结果，支持历史对比（检测 Agent 是否退化）。
 */
@Entity
@Table(name = "evaluation_result", indexes = {
    @Index(name = "idx_eval_result_case", columnList = "case_id"),
    @Index(name = "idx_eval_result_run", columnList = "agent_run_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "case_id", nullable = false, length = 36)
    private String caseId;

    /** 对应的 Agent Run ID（实际执行这道题的 Run） */
    @Column(name = "agent_run_id", length = 36)
    private String agentRunId;

    /** Prompt 版本号（便于对比不同 Prompt 版本的效果） */
    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    /** 是否通过（关键元素都命中） */
    @Column(name = "passed", nullable = false)
      private boolean passed;

    /**
     * 评分（0.0-1.0）
     *
     * <p>= 命中的期望元素数 / 期望元素总数
     * 例如：期望找到 3 个文件，实际找到了 2 个 → score = 0.67
     */
    @Column(precision = 5, scale = 4)
    private Double score;

    /**
     * 评估详情（JSON）
     *
     * <pre>
     * {
     *   "matchedSymbols": [...],
     *   "missingSymbols": [...],
     *   "evidenceCount": 3,
     *   "hallucinations": []
     * }
     * </pre>
     */
    @Column(columnDefinition = "JSON")
    private String details;

    /** 执行耗时（毫秒） */
    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
