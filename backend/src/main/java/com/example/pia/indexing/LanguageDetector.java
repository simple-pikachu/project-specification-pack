package com.example.pia.indexing;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/**
 * 编程语言检测器
 *
 * <p>【Agent 开发：为什么要检测语言？】
 *
 * <p>不同语言需要不同的 AST Parser（Phase 2）：
 * <ul>
 *   <li>Java → JavaParser（提取类/方法/调用关系）</li>
 *   <li>TypeScript/JavaScript/Vue → Tree-sitter（提取组件/API调用）</li>
 *   <li>SQL → SQL Parser（提取表名/字段操作）</li>
 * </ul>
 *
 * <p>Phase 1 先做语言检测和文件索引，Phase 2 再按语言分发给对应 Parser。
 * 这样实现了关注点分离：Scanner 只负责"知道有什么文件"，Parser 负责"理解文件内容"。
 */
@Component
public class LanguageDetector {

    /** 文件扩展名 → 语言枚举的映射表 */
    private static final Map<String, Language> EXTENSION_MAP = Map.ofEntries(
        Map.entry(".java", Language.JAVA),
        Map.entry(".kt", Language.KOTLIN),
        Map.entry(".ts", Language.TYPESCRIPT),
        Map.entry(".tsx", Language.TYPESCRIPT),
        Map.entry(".js", Language.JAVASCRIPT),
        Map.entry(".jsx", Language.JAVASCRIPT),
                Map.entry(".vue", Language.VUE),
        Map.entry(".sql", Language.SQL),
        Map.entry(".yaml", Language.YAML),
        Map.entry(".yml", Language.YAML),
        Map.entry(".json", Language.JSON),
        Map.entry(".md", Language.MARKDOWN),
        Map.entry(".xml", Language.XML),
        Map.entry(".properties", Language.PROPERTIES)
    );

    /**
     * 检测文件语言
     *
     * @param filePath 文件路径（只使用文件名部分）
     * @return 检测到的语言，无法识别返回 {@link Language#UNKNOWN}
     */
    public Language detect(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return Language.UNKNOWN;
        }
        String ext = fileName.substring(dotIndex);
        return EXTENSION_MAP.getOrDefault(ext, Language.UNKNOWN);
    }

    /**
     * 判断文件是否值得索引
     *
     * <p>跳过 UNKNOWN 语言的文件（如二进制文件、图片等），
     * 避免浪费存储和解析时间。
     */
        public boolean isIndexable(Path filePath) {
        return detect(filePath) != Language.UNKNOWN;
    }

    /**
     * 支持的编程语言枚举
     *
     * <p>命名与数据库 code_symbol.symbol_type 保持一致，
     * 便于 Phase 2 按语言路由到对应 Parser。
     */
    public enum Language {
        JAVA,         // Spring Boot 后端
        KOTLIN,       // 可选的 Kotlin 后端
        TYPESCRIPT,   // Vue/React 前端（含 .tsx）
        JAVASCRIPT,   // 纯 JS
        VUE,          // Vue 单文件组件（SFC）
        SQL,          // 数据库迁移脚本、存储过程
        YAML,         // 配置文件（application.yml 等）
        JSON,         // package.json、tsconfig 等
        MARKDOWN,     // 项目文档
        XML,          // Maven pom.xml、Spring XML
        PROPERTIES,   // application.properties
        UNKNOWN       // 无法识别或不支持（跳过索引）
    }
}
