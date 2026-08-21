package com.example.pia.common.security;

import com.example.pia.common.exception.PiaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * PathSecurityValidator 单元测试
 *
 * <p>路径安全是安全红线，必须有完整的测试覆盖。
 * 使用 {@code @TempDir} 创建临时目录，测试完自动清理。
 */
class PathSecurityValidatorTest {

    private final PathSecurityValidator validator = new PathSecurityValidator();

    @Test
    @DisplayName("正常路径：在项目根目录内，应返回规范化路径")
    void validateAndResolve_normalPath_shouldReturnResolvedPath(@TempDir Path tempDir)
            throws IOException {
        // Given：在临时目录下创建一个真实文件
        Path subFile = tempDir.resolve("src/OrderService.java");
        Files.createDirectories(subFile.getParent());
        Files.createFile(subFile);

        // When
                Path result = validator.validateAndResolve(tempDir.toString(), "src/OrderService.java");

        // Then
        assertThat(result).isEqualTo(subFile.toRealPath());
    }

    @Test
    @DisplayName("路径穿越：使用 ../，应抛出 PATH_TRAVERSAL 异常")
    void validateAndResolve_dotDotPath_shouldThrowPathTraversal(@TempDir Path tempDir) {
        // When & Then：../etc/passwd 是经典路径穿越攻击
        assertThatThrownBy(() ->
            validator.validateAndResolve(tempDir.toString(), "../../etc/passwd")
        )
        .isInstanceOf(PiaException.class)
        .hasMessageContaining("非法路径");
    }

    @Test
    @DisplayName("安全校验：路径在根目录内，应返回 true")
    void isSafe_pathInRoot_shouldReturnTrue(@TempDir Path tempDir) {
        Path safe = tempDir.resolve("backend/src/Main.java");
        assertThat(validator.isSafe(tempDir.toString(), safe)).isTrue();
    }

    @Test
    @DisplayName("安全校验：路径在根目录外，应返回 false")
        void isSafe_pathOutsideRoot_shouldReturnFalse(@TempDir Path tempDir) {
        Path outside = tempDir.getParent().resolve("other-project/secret.key");
        assertThat(validator.isSafe(tempDir.toString(), outside)).isFalse();
    }
}
