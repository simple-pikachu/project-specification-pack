package com.example.pia.retrieval.repository;

import com.example.pia.retrieval.domain.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 代码片段数据访问层
 */
@Repository
public interface CodeChunkRepository extends JpaRepository<CodeChunk, String> {

    List<CodeChunk> findByProjectId(String projectId);

    List<CodeChunk> findByProjectIdAndFileId(String projectId, String fileId);

    /** 通过 Qdrant point ID 反查 MySQL 元数据（向量搜索结果关联用） */
    Optional<CodeChunk> findByQdrantPointId(String qdrantPointId);

    /** 批量通过 Qdrant point IDs 反查（向量搜索批量关联） */
    List<CodeChunk> findByQdrantPointIdIn(List<String> qdrantPointIds);

    void deleteByProjectIdAndFileId(String projectId, String fileId);
}
