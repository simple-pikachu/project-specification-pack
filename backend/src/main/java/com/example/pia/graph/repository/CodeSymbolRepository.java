package com.example.pia.graph.repository;

import com.example.pia.graph.domain.CodeSymbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 代码符号数据访问层
 *
 * <p>提供 Agent Tool（code_search）的底层数据查询能力。
 */
@Repository
public interface CodeSymbolRepository extends JpaRepository<CodeSymbol, String> {

    /** 按全限定名精确查找（Agent 确认符号存在时使用） */
    Optional<CodeSymbol> findByProjectIdAndQualifiedName(String projectId, String qualifiedName);

    /** 按简单名模糊搜索（用户输入 "cancel" 时搜索所有含 cancel 的符号） */
    @Query("SELECT s FROM CodeSymbol s WHERE s.projectId = :projectId " +
           "AND LOWER(s.simpleName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<CodeSymbol> searchBySimpleName(@Param("projectId") String projectId,
                                                          @Param("name") String name);

    /** 按符号类型查找（只找 ENDPOINT 类型，用于 API 搜索） */
    List<CodeSymbol> findByProjectIdAndSymbolType(String projectId, CodeSymbol.SymbolType symbolType);

    /** 按文件查找（Phase 2 重新索引时清空旧符号再重建） */
    List<CodeSymbol> findByProjectIdAndFileId(String projectId, String fileId);

    /** 查询项目所有符号（构建符号索引时使用） */
    List<CodeSymbol> findByProjectId(String projectId);

    /** 删除项目所有符号（全量重建时清空） */
    void deleteByProjectId(String projectId);

    /** 统计项目符号总数（索引状态展示） */
    long countByProjectId(String projectId);

    /** 删除某文件的所有符号（增量索引：文件变更时重新解析） */
    void deleteByProjectIdAndFileId(String projectId, String fileId);
}
             