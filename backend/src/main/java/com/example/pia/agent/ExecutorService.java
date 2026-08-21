package com.example.pia.agent;

import com.example.pia.agent.domain.ToolCall;
import com.example.pia.agent.repository.ToolCallRepository;
import com.example.pia.agent.tool.AgentTool;
import com.example.pia.agent.tool.ToolRegistry;
import com.example.pia.common.config.PiaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Executor 服务 — Agent 的"行动"层
 *
 * <p>【Agent 开发：Executor 是什么？】
 *
 * <p>Executor 接收 Planner 生成的调查计划，然后按计划逐步执行 Tool 调用。
 * 每次 Tool 调用都会：
 * <ol>
 *   <li>调用对应的 {@link AgentTool} 执行实际查询</li>
 *   <li>将调用记录写入 {@code tool_call} 表（追踪）</li>
 *   <li>通过 SSE 事件推送进度给前端</li>
 *   <li>将结果累积到 Evidence 列表</li>
 * </ol>
 *
 * <p>执行约束（规格 01-product-requirements.md）：
 * <ul>
 *   <li>最大 Tool 调用次数：30（防止无限循环）</li>
 *   <li>单个 Tool 超时：10 秒</li>
 *   <li>所有 Tool 调用必须写入追踪日志</li>
 * </ul>
 *
 * <p>Executor 不做 LLM 调用，只做 Tool 调用。
 * LLM 的思考在 Planner（规划）和 Reviewer/AnswerComposer（综合）阶段完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorService {

    private final ToolRegistry toolRegistry;
    private final ToolCallRepository toolCallRepository;
    private final PiaProperties piaProperties;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 执行调查计划中的所有任务
     *
     * @param plan      Planner 生成的 JSON 计划
     * @param runId     Agent Run ID（用于追踪）
     * @param projectId 目标项目
     * @param eventPublisher SSE 事件推送器（每步完成后推送给前端）
     * @return 所有 Tool 调用的结果摘要列表（作为 Evidence 汇总）
     */
    public List<ToolCallResult> executePlan(String plan, String runId,
                                                String projectId,
                                            Consumer<SseEvent> eventPublisher) {
        List<ToolCallResult> results = new ArrayList<>();

        try {
            JsonNode planNode = MAPPER.readTree(plan);
            JsonNode tasks = planNode.get("tasks");
            if (tasks == null || !tasks.isArray()) {
                log.warn("[Executor] 计划中没有 tasks: {}", plan);
                return results;
            }

            int maxCalls = piaProperties.getAgent().getMaxToolCalls();
            int callCount = 0;

            for (JsonNode task : tasks) {
                if (callCount >= maxCalls) {
                    log.warn("[Executor] 达到最大 Tool 调用次数({})，终止执行", maxCalls);
                    break;
                }

                String toolName = task.has("toolName") ? task.get("toolName").asText() : "";
                                String description = task.has("description") ? task.get("description").asText() : "";
                String params = task.has("params") ? task.get("params").toString() : "{}";

                // 推送"工具开始执行"事件给前端（前端显示进度）
                eventPublisher.accept(new SseEvent("tool.started",
                    """
                    {"toolName": "%s", "description": "%s"}
                    """.formatted(toolName, description)));

                // 执行 Tool（带超时控制）
                ToolCallResult result = executeWithTrace(toolName, params, runId, projectId);
                results.add(result);
                callCount++;

                // 推送"工具执行完成"事件（含结果摘要）
                eventPublisher.accept(new SseEvent("tool.completed",
                    """
                    {"toolName": "%s", "status": "%s", "latencyMs": %d}
                    """.formatted(toolName, result.status(), result.latencyMs())));

                  log.debug("[Executor] Tool '{}' 完成: {}ms, status={}", toolName,
                    result.latencyMs(), result.status());
            }

        } catch (Exception e) {
            log.error("[Executor] 执行计划失败", e);
        }

        return results;
    }

    /**
     * 执行单个 Tool 调用，同时写入追踪记录
     *
     * <p>这个方法体现了"所有 Tool 可追踪"的规格要求：
     * 无论 Tool 执行成功还是失败，都会在 tool_call 表写入记录。
     * 这样运维人员可以通过查数据库追查 Agent 做了什么。
     */
    private ToolCallResult executeWithTrace(String toolName, String params,
                                            String runId, String projectId) {
        long startTime = System.currentTimeMillis();
        String callId = UUID.randomUUID().toString();

        // 先写入"执行中"状态的 tool_call 记录
        ToolCall toolCallRecord = ToolCall.builder()
            .id(callId)
            .runId(runId)
            .toolName(toolName)
            .inputParams(params)
            .status(ToolCall.ToolCallStatus.PENDING)
                   .build();
        toolCallRepository.save(toolCallRecord);

        try {
            AgentTool tool = toolRegistry.getTool(toolName);
            int timeoutSec = piaProperties.getAgent().getToolTimeoutSeconds();

            // 使用 ExecutorService + Future 实现超时控制
            // 注意：此处必须用全限定名，因为本类名也叫 ExecutorService
            java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> future = executor.submit(() -> tool.execute(params, runId, projectId));

            String output;
            try {
                output = future.get(timeoutSec, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                toolCallRecord.setStatus(ToolCall.ToolCallStatus.TIMEOUT);
                toolCallRecord.setErrorMessage("Tool 执行超时（>" + timeoutSec + "s）");
                toolCallRepository.save(toolCallRecord);
                               long latency = System.currentTimeMillis() - startTime;
                return new ToolCallResult(toolName, "TIMEOUT", "", latency);
            } finally {
                executor.shutdown();
            }

            // 写入成功状态
            long latency = System.currentTimeMillis() - startTime;
            toolCallRecord.setStatus(ToolCall.ToolCallStatus.SUCCESS);
            toolCallRecord.setOutputSummary(truncate(output, 500));
            toolCallRecord.setLatencyMs(latency);
            toolCallRepository.save(toolCallRecord);

            return new ToolCallResult(toolName, "SUCCESS", output, latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("[Executor] Tool '{}' 执行失败", toolName, e);

            toolCallRecord.setStatus(ToolCall.ToolCallStatus.FAILED);
            toolCallRecord.setErrorMessage(e.getMessage());
            toolCallRecord.setLatencyMs(latency);
            toolCallRepository.save(toolCallRecord);

            return new ToolCallResult(toolName, "FAILED", "{\"error\":\"" + e.getMessage() + "\"}", latency);
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** Tool 调用结果 */
    public record ToolCallResult(
        String toolName,
        String status,
        String output,
        long latencyMs
    ) {}

    /** SSE 事件（通过 Controller 推送给前端） */
    public record SseEvent(String type, String data) {}
}
