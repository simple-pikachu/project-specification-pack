package com.example.pia.graph.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 代码关系图边实体（Code Graph 的"关系"）
 *
 * <p>【Agent 开发：什么是 Code Graph 边？】
 *
 * <p>Code Graph = 节点（CodeSymbol）+ 边（GraphEdge）。
 *
 * <p>边记录了符号之间的关系，这是让 Agent 能做影响分析的核心数据：
 * <pre>
 * 关系类型（relation_type）示例：
 *
 *   CALLS：OrderController.cancel() ──CALLS──→ OrderService.cancel()
 *   意义：如果修改 OrderService.cancel()，OrderController.cancel() 会受影响
 *
 *   IMPLEMENTS：OrderServiceImpl ──IMPLEMENTS──→ OrderService（接口）
 *   意义：查找"实现了某接口的类"
 *
 *   EXTENDS：VipOrderService ──EXTENDS──→ OrderService
 *   意义：继承关系影响分析
 *
 *   HTTP_CALLS：前端 src/api/order.ts:cancelOrder ──HTTP_CALLS──→ 后端 OrderController.cancel
 *   意义：前后端关联！Agent 能回答"取消按钮点击后走哪个后端接口？"
 *
 *   QUERIES：OrderService.getOrder() ──QUERIES──→ orders（数据库表）
 *   意义：数据库影响分析
 * </pre>
 *
 * <p>有了这些边，Agent 可以做图遍历：
 * "修改 OrderService.cancel() 会影响什么？"
 * → 找所有 target=OrderService.cancel 的 CALLS 边
 * → 得到 OrderController.cancel（调用方）
 * → 再沿调用链向上遍历，得到完整影响链
 *
 * <p>对应数据库表 {@code graph_edge}。
 */
@Entity
@Table(name = "graph_edge", indexes = {
    @Index(name = "idx_edge_project", columnList = "project_id"),
    @Index(name = "idx_edge_source", columnList = "project_id, source_node_id"),
    @Index(name = "idx_edge_target", columnList = "project_id, target_node_id"),
    @Index(name = "idx_edge_type", columnList = "project_id, relation_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdge {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** 边的起点：发起调用/继承/实现的符号 */
    @Column(name = "source_node_id", nullable = false, length = 36)
    private String sourceNodeId;

    /** 边的终点：被调用/被继承/被实现的符号 */
    @Column(name = "target_node_id", nullable = false, length = 36)
    private String targetNodeId;

        /**
     * 关系类型
     *
     * <p>规格文档 04-code-intelligence.md 第 3 节定义的完整关系类型：
     * CONTAINS / IMPORTS / CALLS / EXTENDS / IMPLEMENTS /
     * USES_TYPE / HTTP_CALLS / MAPS_TO / QUERIES / UPDATES / DEPENDS_ON / ROUTES_TO
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 64)
    private RelationType relationType;

    /**
     * 置信度（0.0-1.0）
     *
     * <p>AST 静态解析的边置信度为 1.0（确定性关系）。
     * 动态分析或模糊匹配的边置信度较低（如 HTTP_CALLS 的路径匹配）。
     * Agent 在引用证据时可以用置信度过滤低质量关系。
     */
    @Column(precision = 5, scale = 4)
    private Double confidence;

    /**
     * 附加元数据（JSON 格式）
     *
     * <p>不同关系类型的附加信息：
     * <ul>
     *   <li>HTTP_CALLS：{method: "POST", path: "/order/cancel"}</li>
     *   <li>CALLS：{callSite: "OrderController.java:88"}（调用发生的位置）</li>
     * </ul>
     */
    @Column(columnDefinition = "JSON")
    private String metadata;

        @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** 关系类型枚举（与规格文档 04-code-intelligence.md 对齐） */
    public enum RelationType {
        CONTAINS,    // 文件包含符号（file → class/method）
        IMPORTS,     // 导入关系
        CALLS,       // 方法调用
        EXTENDS,     // 继承
        IMPLEMENTS,  // 实现接口
        USES_TYPE,   // 使用类型（参数/返回值/局部变量）
        HTTP_CALLS,  // 前端 API 调用后端 Endpoint（跨端关联）
        MAPS_TO,     // MyBatis Mapper 方法 → SQL
        QUERIES,     // 方法查询数据库表（读）
        UPDATES,     // 方法更新数据库表（写）
        DEPENDS_ON,  // 依赖关系（pom.xml 依赖）
        ROUTES_TO    // 路由（前端路由 → 组件）
    }
}
