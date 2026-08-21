package com.example.pia.retrieval;

import com.example.pia.retrieval.domain.CodeChunk;
import com.example.pia.retrieval.repository.CodeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Embedding 服务（将代码片段转为向量，写入 Qdrant）
 *
 * <p>【Agent 开发：Embedding 是什么？】
 *
 * <p>Embedding（嵌入）= 将文本转换为一个高维向量（如 1536 维的浮点数数组）。
 * 语义相近的代码在向量空间中距离也近，这就是向量检索的基础。
 *
 * <p>例如：
 * <pre>
 * "取消订单的逻辑"  →  [0.12, -0.34, 0.56, ...]（1536维向量）
 * "void cancel(String orderId)"  →  [0.13, -0.32, 0.54, ...]（语义相近，向量相近）
 * "用户登录验证"  →  [-0.71, 0.23, -0.45, ...]（语义不同，向量差距大）
 * </pre>
 *
 * <p>Spring AI 的 {@link VectorStore} 接口：
 * <ul>
  *   <li>统一封装了 Embedding 调用和向量存储写入</li>
 *   <li>调用 {@code vectorStore.add(documents)} 时，Spring AI 会：
 *     <ol>
 *       <li>调用 EmbeddingModel（如 OpenAI text-embedding-3-small）将文本转为向量</li>
 *       <li>将向量写入 Qdrant（通过 gRPC）</li>
 *     </ol>
 *   </li>
 *   <li>我们只需要在 MySQL 记录 Qdrant 的 point ID，用于后续关联</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    /**
     * Spring AI 向量存储接口（自动注入 Qdrant 实现）
     *
     * <p>VectorStore 是 Spring AI 的核心抽象，屏蔽了底层向量数据库的差异。
     * 配置在 application.yml 的 spring.ai.vectorstore.qdrant 节点，
     * Spring AI 会自动创建 QdrantVectorStore Bean 注入到这里。
     */
    private final VectorStore vectorStore;

    private final CodeChunkRepository chunkRepository;

    /**
     * 批量将代码片段 Embedding 并写入 Qdrant
     *
     * <p>处理流程：
     * <ol>
     *   <li>将 CodeChunk 转换为 Spring AI 的 {@link Document} 对象</li>
     *   <li>批量调用 vectorStore.add()（Spring AI 自动分批调用 Embedding API）</li>
     *   <li>将 Qdrant point ID 回写到 MySQL（code_chunk.qdrant_point_id）</li>
     * </ol>
     *
     * <p>批处理大小（BATCH_SIZE）：Embedding API 通常有单次请求的文本数量限制。
     * OpenAI text-embedding-3-small 允许每次最多 2048 个输入，但为稳定起见设为 50。
     */
    private static final int BATCH_SIZE = 50;

    @Transactional
    public void embedChunks(List<CodeChunk> chunks) {
        if (chunks.isEmpty()) return;

        log.info("[Embedding] 开始批量 Embedding: {} 个 chunk", chunks.size());
        int total = 0;

        // 分批处理，避免单次请求太大
        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            List<CodeChunk> batch = chunks.subList(i, Math.min(i + BATCH_SIZE, chunks.size()));
            embedBatch(batch);
            total += batch.size();
            log.debug("[Embedding] 进度: {}/{}", total, chunks.size());
        }

        log.info("[Embedding] Embedding 完成: {} 个 chunk", total);
    }

    private void embedBatch(List<CodeChunk> batch) {
                // 为每个 chunk 预分配 Qdrant point ID（我们自己管理 ID，便于关联）
        List<Document> documents = new ArrayList<>();

        for (CodeChunk chunk : batch) {
            String pointId = UUID.randomUUID().toString();
            chunk.setQdrantPointId(pointId);

            // Spring AI Document：content 是要 Embedding 的文本，metadata 用于过滤
            Document doc = new Document(
                pointId,        // 指定 Qdrant point ID（Spring AI 1.0 支持）
                chunk.getContent(),
                Map.of(
                    "chunkId", chunk.getId(),
                    "projectId", chunk.getProjectId(),
                    "fileId", chunk.getFileId(),
                    "symbolId", chunk.getSymbolId() != null ? chunk.getSymbolId() : "",
                    "chunkType", chunk.getChunkType() != null ? chunk.getChunkType() : "",
                    "startLine", chunk.getStartLine() != null ? chunk.getStartLine() : 0,
                                 "endLine", chunk.getEndLine() != null ? chunk.getEndLine() : 0
                )
            );
            documents.add(doc);
        }

        // 一次调用：Spring AI 自动调 Embedding API + 写入 Qdrant
        vectorStore.add(documents);

        // 将 qdrant_point_id 回写到 MySQL
        chunkRepository.saveAll(batch);
    }

    /**
     * 向量语义搜索
     *
     * <p>将用户查询文本转为向量，然后在 Qdrant 中找 Top-K 相似片段。
     *
     * @param query      自然语言查询（如"取消订单的逻辑"）
     * @param projectId  只搜索指定项目的代码
     * @param topK       返回最相似的 K 个结果
     * @return 按相似度降序的代码片段列表
     */
    public List<CodeChunk> semanticSearch(String query, String projectId, int topK) {
        // Spring AI VectorStore 的相似度搜索
        // FilterExpression 用于限制只搜索指定项目
        var results = vectorStore.similaritySearch(
            org.springframework.ai.vectorstore.SearchRequest.builder()
                .query(query)
                .topK(topK)
                   .filterExpression("projectId == '" + projectId + "'")
                .build()
        );

        // 通过 Qdrant point ID 从 MySQL 反查完整元数据
        List<String> pointIds = results.stream()
            .map(Document::getId)
            .toList();

        return chunkRepository.findByQdrantPointIdIn(pointIds);
    }
}
