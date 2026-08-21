package com.example.pia.graph.parser;

import com.example.pia.graph.domain.CodeSymbol;
import com.example.pia.graph.domain.GraphEdge;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Java 源码 AST 解析器
 *
 * <p>【Agent 开发：AST 解析是什么，为什么重要？】
 *
 * <p>AST（Abstract Syntax Tree，抽象语法树）是对源代码结构的精确描述。
 *
 * <p>普通文本搜索："查找所有包含 cancel 的行" → 误报多、语义不明
 * AST 解析："提取所有名为 cancel 的方法，以及它们的参数类型、返回类型、调用关系"
 * → 精确、有结构、可推理
 *
 * <p>本类使用 JavaParser 库将 Java 源文件解析为 AST，然后：
 * <ol>
 *   <li>遍历 AST 节点，提取类/接口/方法/字段等符号（→ CodeSymbol 表）</li>
 *   <li>识别注解（@GetMapping/@PostMapping 等），提取 HTTP Endpoint（→ CodeSymbol ENDPOINT 类型）</li>
 *   <li>提取方法内的调用表达式，建立 CALLS 关系（→ GraphEdge 表）</li>
 * </ol>
 *
 * <p>解析结果就是 Code Graph 的节点和边，供 Agent 的 code_search 和 graph_neighbors 工具使用。
 */
@Slf4j
@Component
public class JavaAstParser {

    private static final JavaParser JAVA_PARSER = new JavaParser();

    /** Spring MVC HTTP 方法注解，用于识别 Endpoint */
    private static final Set<String> HTTP_ANNOTATIONS = Set.of(
        "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
        "PatchMapping", "RequestMapping"
    );

    /**
     * 解析单个 Java 文件，返回提取到的符号和关系
     *
     * @param filePath    Java 文件绝对路径
     * @param fileId      文件在数据库中的 ID（用于关联 code_symbol.file_id）
     * @param projectId   项目 ID
     * @return 解析结果，包含符号列表和关系列表
     */
    public ParsedResult parse(Path filePath, String fileId, String projectId) {
                List<CodeSymbol> symbols = new ArrayList<>();
        List<PendingEdge> pendingEdges = new ArrayList<>();

        try {
            ParseResult<CompilationUnit> result = JAVA_PARSER.parse(filePath);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                log.warn("Java 文件解析失败: {} - {}", filePath, result.getProblems());
                return new ParsedResult(symbols, pendingEdges);
            }

            CompilationUnit cu = result.getResult().get();
            // 获取包名（用于构建全限定名）
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getName().asString())
                .orElse("");

            // 使用 Visitor 模式遍历 AST
            cu.accept(new SymbolExtractorVisitor(packageName, fileId, projectId, symbols, pendingEdges), null);

        } catch (IOException e) {
            log.error("读取 Java 文件失败: {}", filePath, e);
        }

                log.debug("解析完成: {} → {} 符号, {} 待处理关系", filePath.getFileName(),
            symbols.size(), pendingEdges.size());
        return new ParsedResult(symbols, pendingEdges);
    }

    /**
     * AST 符号提取访问器（Visitor 模式）
     *
     * <p>【Visitor 模式说明】
     * JavaParser 的 AST 是一棵树，VoidVisitorAdapter 让我们"访问"每一种节点类型。
     * 重写 visit(ClassOrInterfaceDeclaration) 就能处理所有类和接口，
     * 重写 visit(MethodDeclaration) 就能处理所有方法，以此类推。
     * 不需要手动遍历树，JavaParser 会自动调用对应的 visit 方法。
     */
    private static class SymbolExtractorVisitor extends VoidVisitorAdapter<Void> {

        private final String packageName;
        private final String fileId;
        private final String projectId;
        private final List<CodeSymbol> symbols;
        private final List<PendingEdge> pendingEdges;
        private String currentClassName = "";

        SymbolExtractorVisitor(String packageName, String fileId, String projectId,
                                       List<CodeSymbol> symbols, List<PendingEdge> pendingEdges) {
            this.packageName = packageName;
            this.fileId = fileId;
            this.projectId = projectId;
            this.symbols = symbols;
            this.pendingEdges = pendingEdges;
        }

        /**
         * 访问类或接口声明
         *
         * <p>提取类名、访问修饰符，并识别是 CLASS 还是 INTERFACE 类型。
         */
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            currentClassName = packageName.isEmpty()
                ? n.getNameAsString()
                : packageName + "." + n.getNameAsString();

            CodeSymbol symbol = CodeSymbol.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .fileId(fileId)
                .symbolType(n.isInterface() ? CodeSymbol.SymbolType.INTERFACE : CodeSymbol.SymbolType.CLASS)
                       .qualifiedName(currentClassName)
                .simpleName(n.getNameAsString())
                .startLine(n.getRange().map(r -> r.begin.line).orElse(0))
                .endLine(n.getRange().map(r -> r.end.line).orElse(0))
                .visibility(n.getAccessSpecifier().asString())
                .createdAt(LocalDateTime.now())
                .build();
            symbols.add(symbol);

            // 继续访问类的子节点（方法、字段等）
            super.visit(n, arg);
        }

        /**
         * 访问方法声明
         *
         * <p>提取方法名、签名、行号，以及方法上的 HTTP 注解（Endpoint）。
         */
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            String methodQualifiedName = currentClassName + "." + n.getNameAsString();
            String signature = n.getDeclarationAsString(false, false, false);

            // 判断是否是 HTTP Endpoint（有 Spring MVC 注解）
            CodeSymbol.SymbolType symbolType = CodeSymbol.SymbolType.METHOD;
                    String httpPath = null;

            for (AnnotationExpr annotation : n.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if (HTTP_ANNOTATIONS.contains(annotationName)) {
                    symbolType = CodeSymbol.SymbolType.ENDPOINT;
                    // 尝试提取路径值（如 @GetMapping("/order/cancel") → /order/cancel）
                    if (annotation.isSingleMemberAnnotationExpr()) {
                        httpPath = annotation.asSingleMemberAnnotationExpr()
                            .getMemberValue().toString().replace("\"", "");
                    }
                    break;
                }
            }

            CodeSymbol symbol = CodeSymbol.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .fileId(fileId)
                .symbolType(symbolType)
                .qualifiedName(methodQualifiedName)
                   .simpleName(n.getNameAsString())
                .startLine(n.getRange().map(r -> r.begin.line).orElse(0))
                .endLine(n.getRange().map(r -> r.end.line).orElse(0))
                .signature(signature)
                .visibility(n.getAccessSpecifier().asString())
                .isStatic(n.isStatic())
                .createdAt(LocalDateTime.now())
                .build();
            symbols.add(symbol);

            // 提取方法体内的调用关系（CALLS 边）
            n.findAll(MethodCallExpr.class).forEach(callExpr -> {
                // 记录"当前方法调用了 xxx 方法"
                // 注意：此时无法确定被调用方法的完整 ID（需要符号解析），
                // 所以用 PendingEdge 记录调用名，后续 GraphService 做符号关联
                pendingEdges.add(new PendingEdge(
                    methodQualifiedName,
                    callExpr.getNameAsString(),
                    callExpr.getScope().map(Object::toString).orElse(""),
                    GraphEdge.RelationType.CALLS,
                             callExpr.getRange().map(r -> r.begin.line).orElse(0)
                ));
            });

            super.visit(n, arg);
        }

        /**
         * 访问字段声明
         *
         * <p>提取字段名和类型（用于识别依赖注入关系）。
         */
        @Override
        public void visit(FieldDeclaration n, Void arg) {
            n.getVariables().forEach(var -> {
                String fieldQualifiedName = currentClassName + "." + var.getNameAsString();
                CodeSymbol symbol = CodeSymbol.builder()
                    .id(UUID.randomUUID().toString())
                    .projectId(projectId)
                    .fileId(fileId)
                    .symbolType(CodeSymbol.SymbolType.FIELD)
                    .qualifiedName(fieldQualifiedName)
                    .simpleName(var.getNameAsString())
                    .startLine(n.getRange().map(r -> r.begin.line).orElse(0))
                    .endLine(n.getRange().map(r -> r.end.line).orElse(0))
                          .visibility(n.getAccessSpecifier().asString())
                    .isStatic(n.isStatic())
                    .createdAt(LocalDateTime.now())
                    .build();
                symbols.add(symbol);
            });
            super.visit(n, arg);
        }
    }

    /** 解析结果容器 */
    public record ParsedResult(
        List<CodeSymbol> symbols,
        List<PendingEdge> pendingEdges
    ) {}

    /**
     * 待处理的调用关系
     *
     * <p>AST 解析时只能知道"调用了方法名 X"，但不知道 X 对应哪个类的方法（需要类型推断）。
     * PendingEdge 暂存调用信息，由 GraphService 在所有符号入库后做匹配解析。
     */
    public record PendingEdge(
        String callerQualifiedName, // 调用方全限定名
        String calledMethodName,    // 被调用方法名（不含类名）
        String calledScope,         // 调用作用域（如 "orderService"）
        GraphEdge.RelationType relationType,
        int callSiteLine            // 调用发生的行号
    ) {}
}
