package com.example.pia.graph.repository;

import com.example.pia.graph.domain.GraphEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 代码关系图边数据访问层
 *
 * <p>提供 Agent Tool（graph_neighbors）的底层图遍历能力。
 */
@Repository
public interface GraphEdgeRepository extends JpaRepository<GraphEdge, String> {

    /**
     * 查找某节点的所有出边（该节点调用/实现/继承了什么）
     *
     * <p>例如：source=OrderController.cancel 的 CALLS 出边
     * → 找到 OrderService.cancel（被调用的目标）
     */
    List<GraphEdge> findByProjectIdAndSourceNodeId(String projectId, String sourceNodeId);

    /**
     * 查找某节点的所有入边（谁调用/实现/继承了该节点）
     *
     * <p>例如：target=OrderService.cancel 的 CALLS 入边
     * → 找到所有调用 cancel 的 caller（OrderController等）
     * 这是影响分析的核心查询。
     */
    List<GraphEdge> findByProjectIdAndTargetN    List<GraphEdge> findByProjectIdAndTargetNodeId(String projectId, String targetNodeId);

    /**
     * 按关系类型查找出边
     *
     * <p>Agent 工具 graph_neighbors 的核心查询：
     * "OrderService 的所有 CALLS 出边是什么？"
     */
    List<GraphEdge> findByProjectIdAndSourceNodeIdAndRelationType(
        String projectId, String sourceNodeId, GraphEdge.RelationType relationType);

    /**
     * 按关系类型查找入边（谁调用了该节点？）
     */
    List<GraphEdge> findByProjectIdAndTargetNodeIdAndRelationType(
        String projectId, String targetNodeId, GraphEdge.RelationType relationType);

    /**
     * 查找两节点之间是否已存在某类型关系（避免重复插入）
     */
    boolean existsByProjectIdAndSourceNodeIdAndTargetNodeIdAndRelationType(
        String projectId, String sourceNodeId, String targetNodeId, GraphEdge.RelationType relationType);

    /**
     * 双向图遍历：查找节点的所有相邻节点（出边+入边）
     *
     * <p>用于 Agent 分析影响范围时的邻居节点查询。
     */
    @Query("SELECT e FROM GraphEdge e WHERE e.projectId = :projectId " +
           "AND (e.sourceNodeId = :nodeId OR e.targetNodeId = :nodeId)")
    List<GraphEdge> findAllEdgesForNode(@Param("projectId") String projectId,
                                        @Param("nodeId") String nodeId);

    /** 删除文件相关的所有边（文件重新索引时清除旧关系） */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM GraphEdge e WHERE e.projectId = :projectId " +
           "AND (e.sourceNodeId IN :symbolIds OR e.targetNodeId IN :symbolIds)")
    void deleteEdgesForSymbols(@Param("projectId") String projectId,
                               @Param("symbolIds") List<String> symbolIds);
}
