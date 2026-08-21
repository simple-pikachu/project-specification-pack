package com.example.pia.api;

import com.example.pia.graph.GraphService;
import com.example.pia.graph.domain.CodeSymbol;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Code Graph 查询 REST API
 *
 * <p>【Agent 开发：这个 Controller 是 Agent Tool 的 HTTP 入口】
 *
 * <p>Agent 的 graph_neighbors Tool 内部会调用这些接口，
 * 从 Code Graph 中获取符号和关系信息，作为分析证据。
 *
 * <p>路径设计：
 * <ul>
 *   <li>GET /api/projects/{projectId}/symbols?keyword=cancel — 搜索符号</li>
 *   <li>GET /api/projects/{projectId}/symbols/{symbolId}/neighbors — 查询邻居节点</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    /**
     * GET /api/projects/{projectId}/symbols — 搜索代码符号
     *
     * <p>支持两种模式：
     * <ul>
     *   <li>qualifiedName 参数：精确匹配（如 "com.example.OrderService.cancel"）</li>
          *   <li>keyword 参数：关键词模糊搜索（如 "cancel"）</li>
     * </ul>
     */
    @GetMapping("/symbols")
    public List<SymbolResponse> searchSymbols(
            @PathVariable String projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String qualifiedName) {

        if (qualifiedName != null) {
            return graphService.findByQualifiedName(projectId, qualifiedName)
                .map(s -> List.of(SymbolResponse.from(s)))
                .orElse(List.of());
        }
        if (keyword != null) {
            return graphService.searchByName(projectId, keyword).stream()
                .map(SymbolResponse::from)
                .toList();
        }
        return List.of();
    }

    /**
     * GET /api/projects/{projectId}/symbols/{symbolId}/neighbors — 查询节点邻居
     *
     * <p>Agent 工具 graph_neighbors 的 HTTP 实现。
     * 返回指定节点的所有相邻节点及关系类型，用于调用链分析和影响分析。
     *
         * @param direction OUTGOING / INCOMING / BOTH（默认 BOTH）
     */
    @GetMapping("/symbols/{symbolId}/neighbors")
    public List<NeighborResponse> getNeighbors(
            @PathVariable String projectId,
            @PathVariable String symbolId,
            @RequestParam(defaultValue = "BOTH") String direction) {

        GraphService.Direction dir = GraphService.Direction.valueOf(direction.toUpperCase());
        return graphService.getNeighbors(projectId, symbolId, dir).stream()
            .map(NeighborResponse::from)
            .toList();
    }

    // ──── 响应 DTO ────

    record SymbolResponse(
        String id,
        String qualifiedName,
        String simpleName,
        String symbolType,
        String fileId,
        int startLine,
        int endLine,
        String signature,
        String visibility
    ) {
        static SymbolResponse from(CodeSymbol s) {
            return new SymbolResponse(
                         s.getId(), s.getQualifiedName(), s.getSimpleName(),
                s.getSymbolType().name(), s.getFileId(),
                s.getStartLine(), s.getEndLine(),
                s.getSignature(), s.getVisibility()
            );
        }
    }

    record NeighborResponse(
        SymbolResponse neighbor,
        String relationType,
        String direction,
        Double confidence
    ) {
        static NeighborResponse from(GraphService.NeighborResult r) {
            return new NeighborResponse(
                SymbolResponse.from(r.neighbor()),
                r.relationType().name(),
                r.direction(),
                r.confidence()
            );
        }
    }
}
