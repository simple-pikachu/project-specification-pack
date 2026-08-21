package com.example.pia.graph;

import com.example.pia.graph.domain.CodeSymbol;
import com.example.pia.graph.parser.JavaAstParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * JavaAstParser 单元测试
 *
 * <p>通过写入真实 Java 代码片段到临时文件，验证 AST 解析的准确性。
 * 这是 Agent Code Intelligence 层的核心能力测试。
 */
class JavaAstParserTest {

    private final JavaAstParser parser = new JavaAstParser();
    private static final String PROJECT_ID = "proj-001";
    private static final String FILE_ID = "file-001";

    @Test
    @DisplayName("解析类声明：应提取类名、行号、类型")
    void parse_classDeclaration_shouldExtractClass(@TempDir Path tempDir) throws IOException {
        Path javaFile = writeJavaFile(tempDir, "OrderService.java", """
                        package com.example.order;

            public class OrderService {
                private String name;

                public void cancel(String orderId) {
                    System.out.println("cancel " + orderId);
                }
            }
            """);

        JavaAstParser.ParsedResult result = parser.parse(javaFile, FILE_ID, PROJECT_ID);

        // 应提取到类、字段、方法
        assertThat(result.symbols()).isNotEmpty();

        // 验证类符号
        CodeSymbol classSymbol = result.symbols().stream()
            .filter(s -> s.getSymbolType() == CodeSymbol.SymbolType.CLASS)
            .findFirst()
            .orElseThrow();
        assertThat(classSymbol.getQualifiedName()).isEqualTo("com.example.order.OrderService");
        assertThat(classSymbol.getSimpleName()).isEqualTo("OrderService");
        assertThat(classSymbol.getStartLine()).isGreaterThan(0);
    }

    @Test
    @DisplayName("解析方法声明：应提取方法名、签名、行号")
        void parse_methodDeclaration_shouldExtractMethod(@TempDir Path tempDir) throws IOException {
        Path javaFile = writeJavaFile(tempDir, "OrderService.java", """
            package com.example;

            public class OrderService {
                public void cancel(String orderId) {}
                public String getStatus(String orderId) { return ""; }
            }
            """);

        JavaAstParser.ParsedResult result = parser.parse(javaFile, FILE_ID, PROJECT_ID);

        List<CodeSymbol> methods = result.symbols().stream()
            .filter(s -> s.getSymbolType() == CodeSymbol.SymbolType.METHOD)
            .toList();

        assertThat(methods).hasSize(2);
        assertThat(methods).anyMatch(m -> m.getSimpleName().equals("cancel"));
        assertThat(methods).anyMatch(m -> m.getSimpleName().equals("getStatus"));
    }

    @Test
    @DisplayName("解析 Endpoint：@PostMapping 注解的方法应被识别为 ENDPOINT 类型")
        void parse_postMappingAnnotation_shouldExtractEndpoint(@TempDir Path tempDir) throws IOException {
        Path javaFile = writeJavaFile(tempDir, "OrderController.java", """
            package com.example;

            import org.springframework.web.bind.annotation.*;

            @RestController
            public class OrderController {
                @PostMapping("/order/cancel")
                public void cancel(String orderId) {}

                public void internalMethod() {}
            }
            """);

        JavaAstParser.ParsedResult result = parser.parse(javaFile, FILE_ID, PROJECT_ID);

        // 应有一个 ENDPOINT 类型（cancel）和一个 METHOD 类型（internalMethod）
        long endpointCount = result.symbols().stream()
            .filter(s -> s.getSymbolType() == CodeSymbol.SymbolType.ENDPOINT)
            .count();
        assertThat(endpointCount).isEqualTo(1);

        CodeSymbol endpoint = result.symbols().stream()
                        .filter(s -> s.getSymbolType() == CodeSymbol.SymbolType.ENDPOINT)
            .findFirst().orElseThrow();
        assertThat(endpoint.getSimpleName()).isEqualTo("cancel");
    }

    @Test
    @DisplayName("解析调用关系：方法内的调用应产生 PendingEdge")
    void parse_methodCall_shouldProducePendingEdge(@TempDir Path tempDir) throws IOException {
        Path javaFile = writeJavaFile(tempDir, "OrderController.java", """
            package com.example;

            public class OrderController {
                public void cancel() {
                    orderService.cancel("123");
                    log.info("done");
                }
            }
            """);

        JavaAstParser.ParsedResult result = parser.parse(javaFile, FILE_ID, PROJECT_ID);

        // 应产生调用关系的 PendingEdge
        assertThat(result.pendingEdges()).isNotEmpty();
        assertThat(result.pendingEdges()).anyMatch(e ->
            e.calledMethodName().equals("cancel")
        );
    }

    @Test
    @DisplayName("解析接口：应识别为 INTERFACE 类型")
    void parse_interface_shouldExtractInterface(@TempDir Path tempDir) throws IOException {
        Path javaFile = writeJavaFile(tempDir, "OrderRepository.java", """
            package com.example;

            public interface OrderRepository {
                Order findById(String id);
            }
            """);

        JavaAstParser.ParsedResult result = parser.parse(javaFile, FILE_ID, PROJECT_ID);

        CodeSymbol iface = result.symbols().stream()
            .filter(s -> s.getSymbolType() == CodeSymbol.SymbolType.INTERFACE)
            .findFirst().orElseThrow();
        assertThat(iface.getSimpleName()).isEqualTo("OrderRepository");
    }

    private Path writeJavaFile(Path dir, String fileName, String content) throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}
