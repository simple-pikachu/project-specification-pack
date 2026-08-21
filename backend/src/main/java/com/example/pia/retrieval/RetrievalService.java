package com.example.pia.retrieval;

import com.example.pia.graph.GraphService;
import com.example.pia.graph.domain.CodeSymbol;
import com.example.pia.graph.repository.CodeSymbolRepository;
import com.example.pia.retrieval.domain.CodeChunk;
import com.example.pia.retrieval.repository.CodeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 混合检索服务（Agent Tool 层的核心引擎）
 *
 * <p>【Agent 开发：为什么要混合检索？】
 *
 * <p>单一检索方式有明显局限：
 * <ul>
 *   <li>纯关键词搜索：找 "cancel" → 找到所有含 cancel 的代码，噪声多</li>
 *   <li>纯向量搜索：语义相近但不一定找到精确的符号</li>
 *   <li>纯图遍历：需要已知起点，无法从自然语言直接出发</li>
 * </ul>
 *
 * <p>混合策略（规格 04-code-intelligence.md §6）：
 * <pre>
 * 1. Exact Match（精确符号搜索）：用户输入了具体符号名？直接找
 *    优先级最高，命中就加入结果
 *
 * 2. Keyword Search（关键词搜索）：全限定名/简单名关键词匹配
 *    比 Exact 宽松，找到相关符号
 *
  * 3. Vector Search（语义搜索）：自然语言查询 → Embedding → Qdrant
 *    处理"取消订单相关的代码"这类语义查询
 *
 * 4. Graph Expansion（图扩展）：从找到的节点出发，沿关系图扩展
 *    找到直接相关的 caller/callee，丰富上下文
 *
 * 5. Rerank（重排序）：合并去重，按相关性评分排序
 * </pre>
 *
 * <p>这个组合确保：精确的符号能被精确找到，语义相关的代码也能被发现，
 * 同时通过图扩展补充调用链上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final CodeSymbolRepository symbolRepository;
    private final CodeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final GraphService graphService;

    /**
     * 混合检索入口（Agent Tool: code_search 和 rag_search 共用此方法）
     *
     * @param query      用户查询（可以是符号名、关键词、自然语言）
     * @param projectId  目标项目
     * @param topK       每种检索方式的最大返回数量
     * @return 去重后按相关性排序的 RetrievalResult 列表，含证据信息
     */
    @Transactional(readOnly = true)
    public List<RetrievalResult> retrieve(String query, String projectId, int topK) {
        log.debug("[检索] 开始混合检索: query='{}', projectId={}", query, projectId);

        Map<String, RetrievalResult> resultMap = new LinkedHashMap<>();

        // ── 步骤 1：精确符号匹配（优先级最高）──
        // 如果用户输入的是 "OrderService.cancel" 这样的全限定名，直接精确找
        symbolRepository.findByProjectIdAndQualifiedName(projectId, query)
            .ifPresent(symbol -> {
                RetrievalResult r = fromSymbol(symbol, "EXACT", 1.0);
                resultMap.put(symbol.getId(), r);
                log.debug("[检索] Exact Match: {}", symbol.getQualifiedName());
            });

        // ── 步骤 2：关键词符号搜索 ──
        // 用户输入 "cancel" → 找所有简单名含 cancel 的符号
        List<CodeSymbol> keywordMatches = symbolRepository.searchBySimpleName(projectId, query);
        keywordMatches.stream()
            .limit(topK)
            .forEach(symbol -> {
                resultMap.putIfAbsent(symbol.getId(),
                    fromSymbol(symbol, "KEYWORD", 0.8));
            });

        // ── 步骤 3：向量语义搜索 ──
        // 用户输入自然语言 → Embedding → 找语义相似的代码片段
        try {
            List<CodeChunk> semanticChunks = embeddingService.semanticSearch(query, projectId, topK);
            for (CodeChunk chunk : semanticChunks) {
                String key = chunk.getSymbolId() != null ? chunk.getSymbolId() : "chunk-" + chunk.getId();
                resultMap.putIfAbsent(key, fromChunk(chunk, "VECTOR", 0.7));
            }
        } catch (Exception e) {
            // Qdrant 不可用时降级（只用关键词搜索）
            log.warn("[检索] 向量搜索失败，降级为纯关键词模式: {}", e.getMessage());
        }

        // ── 步骤 4：图扩展（从找到的节点出发，扩展邻居）──
        // 找到 OrderService.cancel 后，顺便找它的 caller 和 callee
        List<String> foundSymbolIds = resultMap.values().stream()
            .filter(r -> r.symbolId() != null)
            .map(RetrievalResult::symbolId)
            .limit(3) // 只对 Top-3 做图扩展，避免结果爆炸
            .toList();

        for (String symbolId : foundSymbolIds) {
                        graphService.getNeighbors(projectId, symbolId, GraphService.Direction.BOTH)
                .stream()
                .limit(2) // 每个节点最多扩展 2 个邻居
                .forEach(neighbor -> {
                    String key = neighbor.neighbor().getId();
                    resultMap.putIfAbsent(key,
                        fromSymbol(neighbor.neighbor(), "GRAPH_EXPANSION", 0.5));
                });
        }

        // ── 步骤 5：按相关性评分排序（Rerank）──
        List<RetrievalResult> sorted = new ArrayList<>(resultMap.values());
        sorted.sort(Comparator.comparingDouble(RetrievalResult::relevanceScore).reversed());

        log.debug("[检索] 检索完成: {} 个结果", sorted.size());
        return sorted;
    }

    // ──── 证据构建 ────

    /**
     * 从 CodeSymbol 构建检索结果（含 Evidence 信息）
     *
     * <p>Evidence 是 Agent 分析报告中"结论的依据"，包含：
     * 文件路径 + 符号名 + 行号 + 代码摘录 + 置信度。
     * 规格 01-product-requirements.md FR-006 要求每个关键结论必须有 Evidence。
     */
        private RetrievalResult fromSymbol(CodeSymbol symbol, String source, double score) {
        // 从 chunk 中查找对应的代码摘录
        List<CodeChunk> chunks = chunkRepository
            .findByProjectIdAndFileId(symbol.getProjectId(), symbol.getFileId())
            .stream()
            .filter(c -> symbol.getId().equals(c.getSymbolId()))
            .toList();

        String excerpt = chunks.isEmpty() ? symbol.getSignature() :
            chunks.get(0).getContent().substring(0,
                Math.min(200, chunks.get(0).getContent().length())) + "...";

        return new RetrievalResult(
            symbol.getId(),
            symbol.getQualifiedName(),
            symbol.getSymbolType().name(),
            symbol.getFileId(),
            symbol.getStartLine(),
            symbol.getEndLine(),
            excerpt,
            source,
            score
        );
    }

    private RetrievalResult fromChunk(CodeChunk chunk, String source, double score) {
               return new RetrievalResult(
            chunk.getSymbolId(),
            chunk.getMetadata(), // 用 metadata 中的符号名
            chunk.getChunkType(),
            chunk.getFileId(),
            chunk.getStartLine(),
            chunk.getEndLine(),
            chunk.getContent().substring(0, Math.min(200, chunk.getContent().length())) + "...",
            source,
            score
        );
    }

    /**
     * 检索结果（同时也是 Agent Evidence 的数据来源）
     *
     * <p>每个 RetrievalResult 可以直接转换为规格 FR-006 要求的 Evidence 格式：
     * filePath + symbol + startLine + endLine + excerpt + confidence
     */
    public record RetrievalResult(
        String symbolId,        // 符号 ID（可能为 null，如 chunk 无对应符号）
        String qualifiedName,   // 全限定名（Evidence.symbol）
        String symbolType,      // 符号类型
        String fileId,          // 文件 ID（通过 ID 查文件路径 → Evidence.filePath）
        Integer startLine,      // 起始行（Evidence.startLine）
        Integer endLine,        // 结束行（Evidence.endLine）
        String excerpt,         // 代码摘录（Evidence.excerpt）
        String source,          // 检索来源：EXACT/KEYWORD/VECTOR/GRAPH_EXPANSION
        double relevanceScore   // 相关性评分（Evidence.confidence）
    ) {}
}
