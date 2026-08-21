package com.example.pia.project.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 项目实体
 *
 * <p>对应数据库表 {@code project}，记录一个被导入分析的代码仓库。
 *
 * <p>【领域模型设计原则】
 * Controller 不写业务逻辑，Service 负责业务编排，
 * 实体只负责数据映射和基本校验。
 * 这样结构清晰，测试也好写。
 */
@Entity
@Table(name = "project")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    /** 源码类型：LOCAL（本地目录）/ GIT（Git 仓库） */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 64)
    private SourceType sourceType;

    @Column(name = "source_path", nullable = false, columnDefinition = "TEXT")
    private String sourcePath;

    @Column(name = "default_branch", length = 255)
    private String defaultBranch;

    /**
     * 项目状态机
     *
      * <p>状态转换：CREATED → INDEXING → INDEXED
     *                                 ↘ ERROR（失败时）
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ProjectStatus.CREATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SourceType {
        LOCAL,  // 本地目录
        GIT     // Git 仓库（Phase 1 实现）
    }

    public enum ProjectStatus {
        CREATED,   // 项目已创建，尚未索引
        INDEXING,  // 正在索引中
        INDEXED,   // 索引完成，可以提问
        ERROR      // 索引失败
    }
}
