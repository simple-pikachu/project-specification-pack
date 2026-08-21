package com.example.pia.indexing;

import com.example.pia.common.config.PiaProperties;
import com.example.pia.common.security.PathSecurityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 文件扫描器
 *
 * <p>【Agent 开发：文件扫描在 Code Intelligence 中的作用】
 *
 * <p>Agent 理解代码的第一步：知道项目里有哪些文件。
 * FileScanner 负责：
 * <ol>
 *   <li>递归遍历项目目录，找到所有源码文件</li>
 *   <li>计算每个文件的 SHA-256 哈希（用于增量索引：只重新解析变更的文件）</li>
 *   <li>跳过无意义的目录（target、node_modules、.git 等）</li>
 *   <li>做路径安全校验（防止软链接逃逸到项目目录外）</li>
 * </ol>
 *
 * <p>扫描结果 {@link ScannedFile} 是 Phase 2（AST 解析）的输入，
 * 也是数据库 project_file 表的数据来源。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileScanner {

    private final LanguageDetector languageDetector;
    private final PathSecurityValidator pathSecurityValidator;
    private final PiaProperties piaProperties;

    /**
     * 应该跳过的目录名（这些目录不包含业务源码）
     *
     * <p>不扫描这些目录的原因：
     * - target/: Maven 编译输出，不是源码
     * - node_modules/: npm 依赖包，不是项目代码
     * - .git/: Git 元数据，不是源码
     * - dist/: 前端构建输出，不是源码
     */
    private static final Set<String> SKIP_DIRS = Set.of(
        "target", "node_modules", ".git", "dist", ".idea",
        ".gradle", "build", "__pycache__", ".vscode", ".mvn"
    );

    /**
     * 扫描项目目录，返回所有可索引文件列表
     *
     * @param projectRoot 项目根目录绝对路径
     * @return 扫描到的文件列表，每个元素包含路径、语言、哈希、大小
     * @throws IOException 目录不存在或无读取权限时抛出
     */
    public List<ScannedFile> scan(String projectRoot) throws IOException {
         Path rootPath = Paths.get(projectRoot).toAbsolutePath().normalize();
        if (!Files.isDirectory(rootPath)) {
            throw new IOException("项目根目录不存在或不是目录: " + projectRoot);
        }

        log.info("开始扫描项目目录: {}", rootPath);
        List<ScannedFile> result = new ArrayList<>();
        long maxSizeBytes = (long) piaProperties.getIndexing().getMaxFileSizeMb() * 1024 * 1024;

        // Files.walk 递归遍历所有文件和目录
        try (Stream<Path> walk = Files.walk(rootPath)) {
            walk
                // 只处理普通文件（跳过目录、软链接等）
                .filter(Files::isRegularFile)
                // 跳过应忽略的目录下的文件
                .filter(path -> !isInSkipDir(path, rootPath))
                // 跳过语言不支持的文件
                .filter(languageDetector::isIndexable)
                // 路径安全校验（防软链接逃逸）
                .filter(path -> pathSecurityValidator.isSafe(projectRoot, path))
                .forEach(path -> {
                    try {
                                                long size = Files.size(path);
                        if (size > maxSizeBytes) {
                            log.debug("跳过超大文件({} MB): {}", size / 1024 / 1024,
                                rootPath.relativize(path));
                            return;
                        }

                        String relativePath = rootPath.relativize(path).toString()
                            .replace('\\', '/'); // Windows 路径分隔符统一为 /
                        String hash = computeHash(path);
                        LanguageDetector.Language language = languageDetector.detect(path);

                        result.add(new ScannedFile(relativePath, language, hash, size));
                    } catch (IOException e) {
                        log.warn("读取文件失败，跳过: {}", path, e);
                    }
                });
        }

        log.info("扫描完成: 共发现 {} 个可索引文件", result.size());
        return result;
    }

    /**
     * 检查路径是否在需要跳过的目录内
     *
     * <p>例如：项目根为 /project，路径为 /project/target/classes/Foo.class
     * 则 relativize 后第一级目录为 "target"，应该跳过。
     */
    private boolean isInSkipDir(Path path, Path root) {
        try {
            Path relative = root.relativize(path);
            // 检查路径的每一级目录名是否在跳过列表中
            for (Path component : relative) {
                if (SKIP_DIRS.contains(component.toString().toLowerCase())) {
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            return true; // 无法相对化，视为不安全，跳过
        }
        return false;
    }

    /**
     * 计算文件 SHA-256 哈希值
     *
     * <p>哈希值用于增量索引：如果文件哈希没变，就不需要重新解析，
     * 大大减少全量重新索引的时间。
     */
    private String computeHash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hashBytes = digest.digest(bytes);
                     return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 扫描结果：单个文件的元数据
     *
     * <p>使用 Java Record（不可变数据类），适合作为数据载体。
     *
     * @param relativePath 相对于项目根目录的路径（使用 / 分隔符）
     * @param language     检测到的编程语言
     * @param hash         文件内容的 SHA-256 哈希
     * @param sizeBytes    文件大小（字节）
     */
    public record ScannedFile(
        String relativePath,
        LanguageDetector.Language language,
        String hash,
        long sizeBytes
    ) {}
}
