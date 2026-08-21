package com.example.pia.retrieval.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 代码片段实体（RAG 的基本检索单元）
 *
 * <p>【Agent 开发：为什么要"切片"代码？】
 *
 * <p>LLM 的上下文窗口有限（如 128K tokens），无法把整个项目的源码全塞进去。
 * 解决方案：把代码切分成小块（Chunk），只把与当前问题最相关的几块发给 LLM。
 *
 * <p>切片策略（规格文档 04-code-intelligence.md 第 5 节）：
 * <ul>
 *   <li>优先按语义边界切：class、method、function、component</li>
 *   <li>不按固定字符数切（固定字符切会破坏代码结构，降低检索质量）</li>
 *   <li>每个 chunk 携带元数据（文件路径、行号、符号名），构成 Evidence</li>
 * </ul>
 *
 * <p>存储架构：
 * <ul>
 *   <li>MySQL code_chunk 表：存元数据（文件路径、行号、符号名、chunk 类型）</li>
 *   <li>Qdrant：存向量（通过 qdrant_point_id 关联）</li>
 * </ul>
 *
 * <p>检索流程：
 * <pre>
 * 用户问题 → Embedding → 向量 → Qdrant 相似度搜索 → qdrant_point_id 列表
 * → MySQL JOIN code_chunk → 得到完整元数据（文件路径、行号）
 * → 组装成 Evidence（Agent 的分析证据）
 * </pre>
 */
@Entity
@Table(name = "code_chunk", indexes = {
    @Index(name = "idx_chunk_project", columnList = "project_id"),
        @Index(name = "idx_chunk_file", columnList = "file_id"),
    @Index(name = "idx_chunk_symbol", columnList = "symbol_id"),
    @Index(name = "idx_chunk_qdrant", columnList = "qdrant_point_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeChunk {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "file_id", nullable = false, length = 36)
    private String fileId;

    /** 对应的代码符号 ID（如果 chunk 对应一个具体方法/类） */
    @Column(name = "symbol_id", length = 36)
    private String symbolId;

    /** 代码原文内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Qdrant 中的 point ID
     *
     * <p>Qdrant 存储向量时会分配一个 UUID（point ID）。
     * 我们在 MySQL 中存储这个 ID，便于：
     * <ul>
     *   <li>向量搜索后通过 point ID 反查 MySQL 获取元数据</li>
      *   <li>重新 Embedding 时能更新对应的 Qdrant point</li>
     * </ul>
     */
    @Column(name = "qdrant_point_id", length = 36)
    private String qdrantPointId;

    /**
     * 片段类型
     *
     * <p>类型影响 Agent 如何呈现这段代码：
     * METHOD chunk → 展示为"方法定义"
     * ENDPOINT chunk → 展示为"API 接口"
     */
    @Column(name = "chunk_type", length = 64)
    private String chunkType;

    /** 起始行号（构成 Evidence 的核心字段） */
    @Column(name = "start_line")
    private Integer startLine;

    /** 结束行号 */
    @Column(name = "end_line")
    private Integer endLine;

    /**
     * 附加元数据（JSON）
     *
     * <p>同步存储到 Qdrant payload，支持向量搜索时的元数据过滤：
     * <pre>
     * {
     *   "language": "JAVA",
     *   "module": "order",
     *   "symbolName": "OrderService.cancel",
     *   "filePath": "backend/src/main/java/..."
     * }
     * </pre>
     * 例如：只搜索 Java 文件中的代码 → 向量搜索时加 language=JAVA 过滤
     */
    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
