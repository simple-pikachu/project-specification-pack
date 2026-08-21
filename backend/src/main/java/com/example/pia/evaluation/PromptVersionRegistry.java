package com.example.pia.evaluation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 版本注册表
 *
 * <p>【Agent 开发：为什么 Prompt 需要版本管理？】
 *
 * <p>Prompt 是 Agent 行为的核心配置，改动 Prompt 等于改动 Agent 的"思维方式"。
 * 如果不做版本管理：
 * <ul>
 *   <li>不知道"是哪次 Prompt 修改导致了这次质量下降"</li>
 *   <li>无法回滚到上一个稳定版本</li>
 *   <li>无法对比不同 Prompt 版本在评估集上的表现</li>
 * </ul>
 *
 * <p>版本化的 Prompt 是 Evaluation 的基础——
 * 每次 Evaluation 运行时记录当前 Prompt 版本，
 * 之后可以按版本对比历史评估结果，追踪质量变化趋势。
 *
 * <p>规格 11-agent-development-guide.md §8 要求版本化：
 * system prompt / planner prompt / reviewer prompt / tool schema / workflow / model configuration
 *
 * <p>MVP 实现：内存存储（生产环境应存数据库）。
 */
@Slf4j
@Component
public class PromptVersionRegistry {

    /** 当前活跃 Prompt 版本（key=名称, value=版本号） */
    private final Map<String, String> currentVersions = new ConcurrentHashMap<>(Map.of(
        "PLANNER", "v1.0.0",
          "REVIEWER", "v1.0.0",
        "ANSWER_COMPOSER", "v1.0.0"
    ));

    /** Prompt 内容存储（key=名称+版本, value=Prompt 文本） */
    private final Map<String, String> promptStore = new ConcurrentHashMap<>();

    /**
     * 注册一个新版本的 Prompt
     *
     * <p>生产环境可以持久化到数据库，支持 Prompt 热更新。
     */
    public void register(String name, String version, String content) {
        String key = name + ":" + version;
        promptStore.put(key, content);
        currentVersions.put(name, version);
        log.info("[PromptRegistry] 注册 Prompt: name={}, version={}", name, version);
    }

    /**
     * 获取当前活跃版本的 Prompt
     *
     * <p>Agent 每次运行时通过此方法获取 Prompt，
     * 这样修改 Prompt 后不需要重启服务（如果 register 了新版本）。
     */
    public String getCurrent(String name, String defaultPrompt) {
        String version = currentVersions.getOrDefault(name, "default");
        String stored = promptStore.get(name + ":" + version);
        return stored != null ? stored : defaultPrompt;
       }

    /** 获取当前版本号（用于 Evaluation 记录） */
    public String getCurrentVersion(String name) {
        return currentVersions.getOrDefault(name, "v1.0.0");
    }

    /** 构建当前所有 Prompt 版本的快照（用于 Evaluation 报告） */
    public String getVersionSnapshot() {
        return currentVersions.toString();
    }
}
