package com.example.pia.retrieval;

import com.example.pia.graph.domain.CodeSymbol;
import com.example.pia.retrieval.domain.CodeChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 代码切片服务
 *
 * <p>【Agent 开发：切片策略决定 RAG 质量】
 *
 * <p>切片是 RAG（Retrieval-Augmented Generation）管道的第一步。
 * 切片质量直接影响：
 * <ol>
 *   <li>向量检索的精度（切太大→噪声多；切太小→上下文丢失）</li>
 *   <li>Evidence 的可读性（一个 chunk = 一段完整的代码逻辑）</li>
 *   <li>LLM 上下文利用率（每个 chunk 都是有意义的代码片段）</li>
 * </ol>
 *
 * <p>本类实现的策略（规格 04-code-intelligence.md §5）：
 * <ul>
 *   <li>按符号边界切：每个 CodeSymbol（方法/类/字段）生成一个 chunk</li>
 *   <li>读取原始文件对应行范围的代码作为 chunk 内容</li>
 *   <li>chunk 包含元数据（文件路径、行号、符号名、语言），用于 Evidence 构建</li>
 * </ul>
 *
 * <p>不实现固定字符数切片，原因：
 * 固定字符数会把一个方法切成两半，导致向量表示混乱，检索质量差。
 */
@Slf4j
@Service
public class ChunkingService {

    /**
     * 将代码符号列表切片为 CodeChunk 列表
     *
     * <p>每个有行号的符号（方法、类、接口等）对应一个 chunk。
     * chunk 的内容是从源文件中读取的对应行范围的代码。
     *
     * @param symbols    该文件的代码符号列表
     * @param projectId  项目 ID
     * @param fileId     文件 ID
     * @param filePath   文件的相对路径（用于构建 metadata）
     * @param projectRoot 项目根目录（用于读取文件内容）
     * @return 切片结果列表
     */
    public List<CodeChunk> chunkSymbols(List<CodeSymbol> symbols,
                                        String projectId,
                                        String fileId,
                                        String filePath,
                                        String projectRoot) {
        List<CodeChunk> chunks = new ArrayList<>();

        // 读取文件全部行（只读一次，避免每个符号单独读文件）
        List<String> fileLines = readFileLines(projectRoot + "/" + filePath);
        if (fileLines.isEmpty()) {
            return chunks;
        }

        for (CodeSymbol symbol : symbols) {
            // 只对有行号的符号生成 chunk（字段等太短的符号跳过）
            if (symbol.getStartLine() <= 0 || symbol.getEndLine() <= 0) {
                continue;
            }
            // 字段太短不值得单独 chunk（通常 1-3 行）
            if (symbol.getSymbolType() == CodeSymbol.SymbolType.FIELD &&
                (symbol.getEndLine() - symbol.getStartLine()) < 2) {
                continue;
            }

            // 提取对应行范围的代码内容
            String content = extractLines(fileLines,
                symbol.getStartLine(), symbol.getEndLine());
            if (content.isBlank()) {
                continue;
            }

            // 构建 metadata JSON（同步到 Qdrant payload，支持过滤搜索）
            String metadata = buildMetadata(symbol, filePath);

            CodeChunk chunk = CodeChunk.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .fileId(fileId)
                .symbolId(symbol.getId())
                .content(content)
                   .chunkType(symbol.getSymbolType().name())
                .startLine(symbol.getStartLine())
                .endLine(symbol.getEndLine())
                .metadata(metadata)
                .build();

            chunks.add(chunk);
        }

        log.debug("切片完成: {} → {} 个 chunk", filePath, chunks.size());
        return chunks;
    }

    /**
     * 提取文件指定行范围的代码（1-based 行号）
     *
     * <p>注意：JavaParser 返回的行号是 1-based（第一行是 1），
     * Java List 是 0-based，所以需要减 1。
     */
    private String extractLines(List<String> lines, int startLine, int endLine) {
        int start = Math.max(0, startLine - 1);
        int end = Math.min(lines.size(), endLine);
        if (start >= end) return "";
        return String.join("\n", lines.subList(start, end));
    }

    private List<String> readFileLines(String absolutePath) {
        try {
            return Files.readAllLines(Paths.get(absolutePath));
        } catch (IOException e) {
                        log.warn("读取文件失败: {}", absolutePath, e);
            return List.of();
        }
    }

    /**
     * 构建 chunk 的 metadata JSON 字符串
     *
     * <p>这个 metadata 会同步写入 Qdrant 的 payload，
     * 支持向量搜索时按语言、模块、符号类型过滤，提高检索精度。
     */
    private String buildMetadata(CodeSymbol symbol, String filePath) {
        // 从文件路径推断模块名（如 backend/src/.../order/OrderService.java → order）
        String module = inferModule(filePath);

        return """
            {
              "symbolName": "%s",
              "symbolType": "%s",
              "filePath": "%s",
              "module": "%s",
              "startLine": %d,
              "endLine": %d
            }
            """.formatted(
            escapeJson(symbol.getQualifiedName()),
            symbol.getSymbolType().name(),
            escapeJson(filePath),
            escapeJson(module),
            symbol.getStartLine(),
            symbol.getEndLine()
        );
    }

        private String inferModule(String filePath) {
        String[] parts = filePath.replace('\\', '/').split("/");
        // 取包名中的最后一个有意义的部分
        for (int i = parts.length - 2; i >= 0; i--) {
            String part = parts[i];
            if (!List.of("java", "main", "src", "backend", "frontend", "kotlin").contains(part)) {
                return part;
            }
        }
        return "unknown";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
