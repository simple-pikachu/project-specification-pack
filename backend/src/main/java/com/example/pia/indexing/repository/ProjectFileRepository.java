package com.example.pia.indexing.repository;

import com.example.pia.indexing.domain.ProjectFile;
import com.example.pia.indexing.LanguageDetector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 项目文件数据访问层
 */
@Repository
public interface ProjectFileRepository extends JpaRepository<ProjectFile, String> {

    /** 查询项目下所有文件（用于增量索引：对比已有记录和新扫描结果） */
    List<ProjectFile> findByProjectId(String projectId);

    /** 按路径查询（判断文件是新增还是已存在） */
    Optional<ProjectFile> findByProjectIdAndPath(String projectId, String path);

    /** 按语言查询（Phase 2 按语言分批解析时使用） */
    List<ProjectFile> findByProjectIdAndLanguage(String projectId, LanguageDetector.Language language);

    /** 统计项目文件总数（用于索引状态展示） */
    long countByProjectId(String projectId);

    /** 按哈希查找（检测重复文件，未来优化用） */
    @Query("SELECT f FROM ProjectFile f WHERE f.projectId = :projectId AND f.fileHash = :hash")
    List<ProjectFile> findByProjectIdAndHash(String projectId, String hash);
}
