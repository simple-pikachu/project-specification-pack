package com.example.pia.evaluation.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 评估用例实体
 *
 * <p>【Agent 开发：为什么需要 Evaluation？】
 *
 * <p>Agent 不像普通代码，改了一行 Prompt 就可能导致整体质量下降。
 * Evaluation（评估）是防止"Agent 退化"的核心机制：
 *
 * <pre>
 * 每次修改 Prompt/Runtime 后：
 * 1. 自动跑 evaluation_case 表中的所有用例
 * 2. 对比 Agent 实际输出与 expected（标准答案）
 * 3. 计算 Evidence Coverage、Task Success Rate 等指标
 * 4. 如果指标低于质量门禁 → 阻断发布
 * </pre>
 *
 * <p>规格 08-testing-and-evaluation.md §2 要求至少 100 个用例，
 * 覆盖 5 个分类：Code Navigation / API Mapping / Impact / Bug / Requirement
 *
 * <p>对应数据库表 {@code evaluation_case}。
 */
@Entity
@Table(name = "evaluation_case", indexes = {
    @Index(name = "idx_eval_project", columnList = "project_id"),
    @Index(name = "idx_eval_category", columnList = "category")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationCase {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** 用例名称，如 "UC-001-order-cancel-impact" */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * 用例分类（规格 08-testing-and-evaluation.md §2）
     *
     * <p>5 类各 20 个用例 = 100 个总用例（MVP 最低要求）
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private EvalCategory category;

    /** 用户输入的自然语言问题 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    /**
     * 期望输出（JSON 格式）
     *
     * <p>不要求 Agent 输出与 expected 完全相同（LLM 输出有随机性），
     * 而是检查期望的"关键元素"是否存在：
     * <pre>
     * {
     *   "expectedSymbols": ["OrderService.cancel", "OrderController"],
     *   "expectedFiles": ["OrderService.java", "OrderController.java"],
     *   "expectedConcepts": ["事务", "状态机"],
     *   "minEvidenceCount": 3,
     *   "mustNotContain": ["虚构方法名"]
     * }
     * </pre>
 */
    @Column(nullable = false, columnDefinition = "JSON")
    private String expected;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** 评估用例分类 */
    public enum EvalCategory {
        CODE_NAVIGATION,    // "这个方法谁调用？"
        API_MAPPING,        // "这个前端 API 对应哪个后端接口？"
        IMPACT_ANALYSIS,    // "修改这个类会影响什么？"
        BUG_ANALYSIS,       // "订单支付后状态没有更新，原因是？"
        REQUIREMENT_ANALYSIS // "给订单增加取消功能需要改哪些地方？"
    }
}
