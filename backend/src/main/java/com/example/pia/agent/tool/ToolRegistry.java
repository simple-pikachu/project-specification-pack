package com.example.pia.agent.tool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tool 注册中心
 *
 * <p>【Agent 开发：Tool Registry 的作用】
 *
 * <p>Agent 需要知道"我能用哪些工具？每个工具有什么能力？"
 * ToolRegistry 就是工具目录，负责：
 * <ol>
 *   <li>自动发现所有实现了 {@link AgentTool} 接口的 Bean</li>
 *   <li>按任务类型动态返回相关工具列表（规格 11-agent-development-guide §5）</li>
 *   <li>执行 Tool 调用并记录追踪信息</li>
 * </ol>
 *
 * <p>规格要求："不要把所有工具描述塞给模型。根据任务动态暴露相关工具。"
 * 原因：工具描述占用 Token，工具太多会混淆 LLM 的决策。
 * Requirement Analysis 任务只需要 code_search/graph_neighbors/schema_search，
 * Bug Analysis 任务需要额外的 git_search。
 *
 * <p>Spring 自动注入所有 {@link AgentTool} 实现，ToolRegistry 统一管理。
 * 新增工具只需实现 AgentTool 接口并添加 @Component，无需修改 Registry。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    /** Spring 自动注入所有实现了 AgentTool 的 Bean */
    private final List<AgentTool> allTools;

    private Map<String, AgentTool> toolMap;

    @PostConstruct
    public void init() {
        toolMap = allTools.stream()
            .collect(Collectors.toMap(AgentTool::getName, Function.identity()));
        log.info("[ToolRegistry] 已注册 {} 个工具: {}", toolMap.size(), toolMap.keySet());
    }

    /**
     * 按名称获取工具
     *
     * @throws IllegalArgumentException 工具不存在时
     */
    public AgentTool getTool(String name) {
        AgentTool tool = toolMap.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在: " + name);
        }
        return tool;
    }

    /**
     * 按任务类型返回适合的工具列表（动态工具暴露）
     *
     * <p>规格 11-agent-development-guide §5 的实现。
     * 减少无关工具对 LLM 决策的干扰，同时节省 Token。
     */
    public List<AgentTool> getToolsForTask(TaskType taskType) {
        return switch (taskType) {
                  case REQUIREMENT_ANALYSIS -> getByNames(
                "code_search", "graph_neighbors", "schema_search", "rag_search"
            );
            case BUG_ANALYSIS -> getByNames(
                "code_search", "code_read", "graph_neighbors", "schema_search", "rag_search"
            );
            case CODE_NAVIGATION -> getByNames(
                "code_search", "graph_neighbors", "rag_search"
            );
            case IMPACT_ANALYSIS -> getByNames(
                "code_search", "graph_neighbors", "schema_search"
            );
        };
    }

    /**
     * 返回所有工具的描述（用于 Planner Prompt 构建）
     *
     * <p>格式化为 LLM 能理解的工具列表描述，
     * 包含 name、description、inputSchema。
     */
    public String buildToolDescriptions(List<AgentTool> tools) {
        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : tools) {
            sb.append("## ").append(tool.getName()).append("\n");
                        sb.append(tool.getDescription().strip()).append("\n");
            sb.append("Input Schema:\n```json\n").append(tool.getInputSchema()).append("\n```\n\n");
        }
        return sb.toString();
    }

    private List<AgentTool> getByNames(String... names) {
        return java.util.Arrays.stream(names)
            .filter(toolMap::containsKey)
            .map(toolMap::get)
            .toList();
    }

    /** 任务类型枚举，对应不同的工具子集 */
    public enum TaskType {
        REQUIREMENT_ANALYSIS,  // 需求影响分析
        BUG_ANALYSIS,          // Bug 根因分析
        CODE_NAVIGATION,       // 代码问答/导航
        IMPACT_ANALYSIS        // 影响范围分析
    }
}
