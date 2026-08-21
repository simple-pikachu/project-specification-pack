package com.example.pia.common.security;

import com.example.pia.common.exception.PiaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径安全校验器
 *
 * <p>【Agent 开发：为什么这是安全红线？】
 *
 * <p>Agent 需要读取项目源码文件，但如果不做路径校验，攻击者可以构造
 * 恶意路径（如 {@code ../../etc/passwd}）让 Agent 读取服务器敏感文件。
 *
 * <p>这类攻击叫"路径穿越（Path Traversal）"，是 OWASP Top 10 常见漏洞。
 *
 * <p>防御原则：所有文件访问路径必须经过 canonical path 规范化，
 * 然后验证是否以项目根目录开头。规范化会解析 {@code ..} 和软链接，
 * 使攻击者无法通过相对路径跳出项目目录。
 *
 * <p>规格文档 10-security.md 第 4 节要求：
 * <pre>
 * requestedPath startsWith projectRoot
 * 禁止：../，absolute path outside root，symlink escape
 * </pre>
 */
@Slf4j
@Component
public class PathSecurityValidator {

    /**
     * 验证请求的文件路径是否在项目根目录内
     *
     * @param projectRoot 项目根目录（绝对路径）
     * @param requestedPath 请求访问的文件路径（可以是相对或绝对）
     * @return 规范化后的绝对路径（安全的，可以直接使用）
     * @throws PiaException 路径越权时抛出，包含 PATH_TRAVERSAL 错误码
     */
    public Path validateAndResolve(String projectRoot, String requestedPath) {
        try {
            // canonical path：解析所有 .., ., 软链接，得到真实绝对路径
            Path rootCanonical = Paths.get(projectRoot).toRealPath();
            Path requestedCanonical = rootCanonical.resolve(requestedPath).normalize().toRealPath();

            // 核心安全检查：请求路径必须以项目根目录开头
            if (!requestedCanonical.startsWith(rootCanonical)) {
                log.warn("路径穿越攻击尝试: projectRoot={}, requested={}, resolved={}",
                    projectRoot, requestedPath, requestedCanonical);
                throw PiaException.pathTraversal(requestedPath);
            }

            return requestedCanonical;
        } catch (IOException e) {
            // 文件不存在时 toRealPath() 会抛异常，用 toAbsolutePath 降级处理
            Path rootAbs = Paths.get(projectRoot).toAbsolutePath().normalize();
            Path requestedAbs = rootAbs.resolve(requestedPath).normalize();

            if (!requestedAbs.startsWith(rootAbs)) {
                throw PiaException.pathTraversal(requestedPath);
            }
            return requestedAbs;
        }
    }

    /**
     * 仅验证路径合法性（不要求文件存在）
     *
     * <p>用于索引阶段：文件扫描时路径是从文件系统遍历来的，一定存在，
     * 但需要验证不被软链接欺骗出项目目录。
     */
    public boolean isSafe(String projectRoot, Path filePath) {
        try {
            Path rootCanonical = Paths.get(projectRoot).toAbsolutePath().normalize();
            Path fileNormalized = filePath.toAbsolutePath().normalize();
            return fileNormalized.startsWith(rootCanonical);
        } catch (Exception e) {
            return false;
        }
    }
}
