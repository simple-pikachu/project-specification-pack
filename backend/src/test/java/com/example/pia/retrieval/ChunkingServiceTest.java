package com.example.pia.retrieval;

import com.example.pia.graph.domain.CodeSymbol;
import com.example.pia.retrieval.domain.CodeChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * ChunkingService 单元测试
 *
 * <p>验证按符号边界切片的正确性：
 * 每个方法/类生成一个 chunk，内容对应源文件的正确行范围。
 */
class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService();

    @Test
    @DisplayName("按方法边界切片：每个方法生成一个 chunk，内容包含对应代码")
    void chunkSymbols_methods_shouldGenerateOneChunkPerMethod(@TempDir Path tempDir)
            throws IOException {
        // Given：写入一个有两个方法的 Java 文件
        Path javaFile = tempDir.resolve("OrderService.java");
        Files.writeString(javaFile, """
            package com.example;
                     public class OrderService {
                public void cancel(String id) {
                    // cancel logic
                }
                public String getStatus(String id) {
                    return "PENDING";
                }
            }
            """);

        // 模拟 Phase 2 提取的符号
        List<CodeSymbol> symbols = List.of(
            buildSymbol("com.example.OrderService.cancel", "cancel", CodeSymbol.SymbolType.METHOD, 3, 5),
            buildSymbol("com.example.OrderService.getStatus", "getStatus", CodeSymbol.SymbolType.METHOD, 6, 8)
        );

        // When
        List<CodeChunk> chunks = chunkingService.chunkSymbols(
            symbols, "proj-001", "file-001",
            "OrderService.java", tempDir.toString()
        );

        // Then：两个方法 → 两个 chunk
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getContent()).contains("cancel");
                assertThat(chunks.get(1).getContent()).contains("getStatus");
    }

    @Test
    @DisplayName("行号边界：chunk 内容应精确对应指定行范围")
    void chunkSymbols_lineRange_shouldExtractCorrectLines(@TempDir Path tempDir)
            throws IOException {
        Path javaFile = tempDir.resolve("Foo.java");
        Files.writeString(javaFile, """
            line1
            line2
            line3
            line4
            line5
            """);

        List<CodeSymbol> symbols = List.of(
            buildSymbol("Foo.method", "method", CodeSymbol.SymbolType.METHOD, 2, 4)
        );

        List<CodeChunk> chunks = chunkingService.chunkSymbols(
            symbols, "proj-001", "file-001",
            "Foo.java", tempDir.toString()
        );

        assertThat(chunks).hasSize(1);
        String content = chunks.get(0).getContent();
        assertThat(content).contains("line2").contains("line3").contains("line4");
                assertThat(content).doesNotContain("line1").doesNotContain("line5");
    }

    @Test
    @DisplayName("metadata：chunk 应包含符号名和文件路径（用于 Evidence）")
    void chunkSymbols_metadata_shouldContainSymbolInfo(@TempDir Path tempDir)
            throws IOException {
        Path javaFile = tempDir.resolve("Svc.java");
        Files.writeString(javaFile, "public class Svc { public void foo() {} }");

        List<CodeSymbol> symbols = List.of(
            buildSymbol("com.example.Svc.foo", "foo", CodeSymbol.SymbolType.METHOD, 1, 1)
        );

        List<CodeChunk> chunks = chunkingService.chunkSymbols(
            symbols, "proj-001", "file-001", "Svc.java", tempDir.toString()
        );

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getMetadata()).contains("com.example.Svc.foo");
        assertThat(chunks.get(0).getMetadata()).contains("Svc.java");
    }

    // ──── 工具方法 ────

        private CodeSymbol buildSymbol(String qualifiedName, String simpleName,
                                   CodeSymbol.SymbolType type, int start, int end) {
        return CodeSymbol.builder()
            .id(java.util.UUID.randomUUID().toString())
            .projectId("proj-001")
            .fileId("file-001")
            .qualifiedName(qualifiedName)
            .simpleName(simpleName)
            .symbolType(type)
            .startLine(start)
            .endLine(end)
            .build();
    }
}
