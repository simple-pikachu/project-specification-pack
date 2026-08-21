package com.example.pia.common.exception;

/**
 * PIA 业务异常基类
 *
 * <p>区分"业务异常"（可预期、有明确错误码）和"系统异常"（RuntimeException）。
 * Agent 分析过程中的可预期错误（项目不存在、索引未完成、路径越权等）
 * 应抛出 PiaException，由全局异常处理器统一转换为标准错误响应。
 */
public class PiaException extends RuntimeException {

    private final String errorCode;

    public PiaException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PiaException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // ──── 常用错误码常量 ────

    public static PiaException projectNotFound(String projectId) {
        return new PiaException("PROJECT_NOT_FOUND", "项目不存在: " + projectId);
    }

    public static PiaException indexNotReady(String projectId) {
        return new PiaException("INDEX_NOT_READY", "项目尚未完成索引，请等待索引完成后再提问: " + projectId);
    }

    public static PiaException pathTraversal(String path) {
        return new PiaException("PATH_TRAVERSAL", "非法路径访问，不允许访问项目根目录以外的文件: " + path);
    }

    public static PiaException toolTimeout(String toolName) {
        return new PiaException("TOOL_TIMEOUT", "工具调用超时: " + toolName);
    }

    public static PiaException agentRunNotFound(String runId) {
        return new PiaException("RUN_NOT_FOUND", "Agent Run 不存在: " + runId);
    }
}
