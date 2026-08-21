package com.example.pia.indexing.domain;

import com.example.pia.indexing.LanguageDetector;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 项目文件实体，对应数据库表 {@code project_file}
 *
 * <p>记录项目中每个源码文件的元数据（路径、语言、哈希、大小）。
 * 文件哈希是增量索引的关键：只有哈希变化的文件才需要重新解析。
 */
@Entity
@Table(name = "project_file")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectFile {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** 相对于项目根目录的路径，使用 / 分隔符（跨平台一致） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String path;

    /** 编程语言，对应 Phase 2 的 Parser 路由依据 */
    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private LanguageDetector.Language language;

    /** 文件内容 SHA-256 哈希，用于增量索引判断文件是否变更 */
    @Column(name = "file_hash", length = 64)
    private String fileHash;
    
    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "last_indexed_at")
    private LocalDateTime lastIndexedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
