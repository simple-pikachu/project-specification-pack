package com.example.pia.indexing;

import com.example.pia.common.config.PiaProperties;
import com.example.pia.common.security.PathSecurityValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * FileScanner 单元测试
 *
 * <p>使用 @TempDir 构造真实的临时文件结构，验证扫描结果的正确性。
 * 不启动 Spring 容器，纯单元测试，毫秒级完成。
 */
class FileScannerTest {

    private final FileScanner scanner = new FileScanner(
        new LanguageDetector(),
        new PathSecurityValidator(),
        defaultProperties()
    );

    @Test
    @DisplayName("基本扫描：能正确识别 Java 和 Vue 文件")
    void scan_basicFiles_shouldDetectLanguages(@TempDir Path tempDir) throws IOException {
        // Given：构造典型的前后端项目结构
                createFile(tempDir, "backend/src/OrderService.java", "public class OrderService {}");
        createFile(tempDir, "frontend/src/views/OrderList.vue", "<template></template>");
        createFile(tempDir, "README.md", "# PIA");

        // When
        List<FileScanner.ScannedFile> files = scanner.scan(tempDir.toString());

        // Then
        assertThat(files).hasSize(3);
        assertThat(files).anyMatch(f ->
            f.relativePath().contains("OrderService.java") &&
            f.language() == LanguageDetector.Language.JAVA
        );
        assertThat(files).anyMatch(f ->
            f.relativePath().contains("OrderList.vue") &&
            f.language() == LanguageDetector.Language.VUE
        );
    }

    @Test
    @DisplayName("跳过 target 目录：Maven 编译输出不应该被索引")
    void scan_shouldSkipTargetDirectory(@TempDir Path tempDir) throws IOException {
        // Given：同时有 src 和 target 目录
                createFile(tempDir, "src/main/java/OrderService.java", "public class OrderService {}");
        createFile(tempDir, "target/classes/OrderService.class", "binary"); // 编译输出，应跳过

        // When
        List<FileScanner.ScannedFile> files = scanner.scan(tempDir.toString());

        // Then：.class 是 UNKNOWN 语言，已被 LanguageDetector 过滤
        // target 目录也在 SKIP_DIRS 中，双重保险
        assertThat(files).allMatch(f -> !f.relativePath().startsWith("target"));
    }

    @Test
    @DisplayName("哈希一致性：相同内容的文件哈希应该相同")
    void scan_sameContent_shouldHaveSameHash(@TempDir Path tempDir) throws IOException {
        // Given
        String content = "public class Foo {}";
        createFile(tempDir, "Foo1.java", content);
        createFile(tempDir, "Foo2.java", content);

        // When
        List<FileScanner.ScannedFile> files = scanner.scan(tempDir.toString());

        // Then
        assertThat(files).hasSize(2);
        String hash1 = files.get(0).hash();
            String hash2 = files.get(1).hash();
        assertThat(hash1).isEqualTo(hash2); // 相同内容，哈希相同
        assertThat(hash1).hasSize(64);       // SHA-256 十六进制 = 64 字符
    }

    @Test
    @DisplayName("路径分隔符：Windows 路径应统一转换为 /")
    void scan_windowsPath_shouldUseForwardSlash(@TempDir Path tempDir) throws IOException {
        createFile(tempDir, "src/main/java/OrderService.java", "class X {}");

        List<FileScanner.ScannedFile> files = scanner.scan(tempDir.toString());

        assertThat(files).allMatch(f -> !f.relativePath().contains("\\"));
    }

    // ──── 工具方法 ────

    private void createFile(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath.replace('/', java.io.File.separatorChar));
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private PiaProperties defaultProperties() {
        PiaProperties props = new PiaProperties();
         // 使用默认值（maxFileSizeMb=10，supportedExtensions=默认列表）
        return props;
    }
}
