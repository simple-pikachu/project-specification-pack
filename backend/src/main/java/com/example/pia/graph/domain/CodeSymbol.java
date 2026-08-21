package com.example.pia.graph.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 代码符号实体（Code Graph 的"节点"）
 *
 * <p>【Agent 开发：什么是代码符号？】
 *
 * <p>代码符号是 Code Graph 的基本单元，代表代码中有意义的"命名实体"：
 * <ul>
 *   <li>CLASS：类（{@code public class OrderService}）</li>
 *   <li>INTERFACE：接口（{@code public interface OrderRepository}）</li>
 *   <li>METHOD：方法（{@code void cancel(String orderId)}）</li>
 *   <li>FIELD：字段（{@code private OrderMapper orderMapper}）</li>
 *   <li>ENDPOINT：HTTP 接口（{@code @PostMapping("/order/cancel")}）</li>
 *   <li>COMPONENT：Vue 组件（{@code OrderList.vue}）</li>
 * </ul>
 *
 * <p>有了符号表，Agent 可以：
 * <ul>
 *   <li>精确回答"OrderService 的 cancel 方法在第几行？"</li>
 *   <li>知道符号的类型（是方法还是类），而不只是文本字符串</li>
 *   <li>作为 Code Graph 的节点，与其他符号建立关系（CALLS/IMPLEMENTS 等）</li>
 * </ul>
 *
 * <p>对应数据库表 {@code code_symbol}。
 */
@Entity
@Table(name = "code_symbol", indexes = {
    @Index(name = "idx_symbol_project", columnList = "project_id"),
    @Index(name = "idx_symbol_file", columnList = "file_id"),
    @Index(name = "idx_symbol_type", columnList = "project_id, symbol_type"),
    @Index(name = "idx_symbol_name", columnList = "project_id, simple_name(100)")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSymbol {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "file_id", nullable = false, length = 36)
    private String fileId;

    /**
     * 符号类型
     *
     * <p>类型决定了：
     * <ul>
     *   <li>Phase 2 中如何解析（CLASS 和 METHOD 用不同的 AST 访问器）</li>
     *   <li>Agent 查询时的过滤条件（只找 ENDPOINT 类型的符号）</li>
     *   <li>Code Graph 的关系推断（ENDPOINT 类型的节点可以有 HTTP_CALLS 关系）</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
        @Column(name = "symbol_type", nullable = false, length = 64)
    private SymbolType symbolType;

    /**
     * 全限定名
     *
     * <p>例如：{@code com.example.order.OrderService.cancel}
     * 用于跨文件精确定位符号，避免同名符号混淆。
     */
    @Column(name = "qualified_name", nullable = false, columnDefinition = "TEXT")
    private String qualifiedName;

    /**
     * 简单名（不含包名）
     *
     * <p>例如：{@code cancel}
     * 用于快速关键词搜索（Agent 查"cancel 方法"时使用）。
     */
    @Column(name = "simple_name", nullable = false, length = 255)
    private String simpleName;

    /** 起始行号（1-based），构成 Evidence 的核心数据 */
    @Column(name = "start_line", nullable = false)
    private int startLine;

    /** 结束行号（1-based） */
    @Column(name = "end_line", nullable = false)
    private int endLine;

    /**
     * 方法/函数签名
     *
     * <p>例如：{@code public void cancel(String orderId) throws OrderNotFoundException}
     * 比简单名更精确，用于区分重载方法。
     */
    @Column(columnDefinition = "TEXT")
        private String signature;

    /** 访问修饰符：public/protected/private/package */
    @Column(length = 32)
    private String visibility;

    /** 是否静态方法/字段 */
    @Column(name = "is_static")
    private boolean isStatic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** 符号类型枚举 */
    public enum SymbolType {
        CLASS,       // Java 类
        INTERFACE,   // Java 接口
        ENUM,        // Java 枚举
        METHOD,      // 方法（包括构造方法）
        FIELD,       // 字段/属性
        ENDPOINT,    // HTTP 端点（@GetMapping 等注解标注）
        COMPONENT,   // Vue/React 组件
        FUNCTION,    // JS/TS 普通函数
        SQL_TABLE,   // SQL 表（从 Mapper 中提取）
        UNKNOWN
    }
}
