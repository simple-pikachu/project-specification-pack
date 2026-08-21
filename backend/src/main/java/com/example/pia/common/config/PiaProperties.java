package com.example.pia.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PIA 自定义配置属性
 *
 * <p>对应 application.yml 中 {@code pia:} 前缀的配置项。
 * 通过 {@code @ConfigurationProperties} 自动绑定，强类型，避免硬编码。
 *
 * <p>【为什么把配置抽成 Properties 类？】
 * Agent 行为参数（最大 Tool 调用次数、Token 预算等）是运行时可调整的，
 * 不应硬编码在业务代码里。通过配置文件管理，方便不同环境调整。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pia")
public class PiaProperties {

    private Agent agent = new Agent();
    private Indexing indexing = new Indexing();
    private Security security = new Security();

    @Data
    public static class Agent {
        /** Agent 单次运行最大 Tool 调用次数，防止无限循环 */
        private int maxToolCalls = 30;
        /** Planner 最大重规划次数（证据不足时 Planner 会重新规划） */
        private int maxIterations = 5;
        /** 单次 LLM 调用 token 预算 */
        private int tokenBudget = 16000;
        /** Tool 调用默认超时（秒） */
        private int toolTimeoutSeconds = 10;
    }

    @Data
    public static class Indexing {
        /** 支持解析的文件扩展名列表 */
        private List<String> supportedExtensions = List.of(
            ".java", ".kt", ".ts", ".js", ".vue",
            ".sql", ".yaml", ".yml", ".json", ".md"
        );
        /** 单文件最大解析大小（MB），超过跳过 */
        private int maxFileSizeMb = 10;
    }

    @Data
    public static class Security {
        /** 允许访问的项目根目录列表（防路径穿越攻击） */
        private List<String> allowedRoots = List.of(
            System.getProperty("user.home") + "/projects"
        );
    }
}
