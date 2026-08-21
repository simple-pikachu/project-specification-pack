package com.example.pia.agent;

import com.example.pia.agent.domain.AgentRun;
import com.example.pia.agent.repository.AgentRunRepository;
import com.example.pia.agent.tool.ToolRegistry;
import com.example.pia.common.exception.PiaException;
import com.example.pia.common.config.PiaProperties;
import com.example.pia.project.domain.Project;
import com.example.pia.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Runtime — 整个 Agent 工作流的编排引擎
 *
 * <p>【Agent 开发：这是整个系统的"指挥官"】
 *
 * <p>AgentRuntime 编排 Agent 工作流的完整生命周期：
 * <pre>
 * 1. 创建 AgentRun（记录用户问题）
 * 2. 意图分析（确定 TaskType → 选择工具集）
 * 3. Planner（LLM 生成调查计划）
 * 4. Executor（按计划调用 Tools 收集证据）
 * 5. Reviewer（检查证据完整性）
 *    - 证据不足 → 回到 Planner 重新规划（最多 5 次）
 *    - 证据充分 → 继续
 * 6. AnswerComposer（LLM 整合证据生成报告，流式输出）
 * 7. 标记 AgentRun 为 COMPLETED
 * </pre>
 *
 * <p>SSE 流式输出：
 * 使用 Project Reactor 的 {@link Sinks.Many} 作为 SSE 通道。
 * 每个 AgentRun 有独立的 Sink，前端通过 GET /api/agent/runs/{runId}/events 订阅。
 * Agent 执行过程中，每个阶段的进度都通过 Sink 推送给前端：
 * run.started → planning.started → tool.started → tool.completed →
 * review.completed → answer.delta → run.completed
 *
 * <p>{@code @Async} 异步执行：Agent 分析耗时 10-60 秒，必须异步，
 * HTTP 请求立即返回 runId，前端通过 SSE 接收后续进度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntime {

    private final AgentRunRepository agentRunRepository;
    private final ProjectRepository projectRepository;
    private final PlannerService plannerService;
    private final ExecutorService executorService;
    private final ReviewerService reviewerService;
    private final AnswerComposerService answerComposerService;
    private final PiaProperties piaProperties;

    /**
     * SSE 通道映射：runId → Sink
     *
     * <p>每个 AgentRun 有独立的 Sink，前端订阅对应的 SSE 流。
     * ConcurrentHashMap 保证线程安全（多个用户并发时互不影响）。
     * Run 完成后从 Map 中移除，释放内存。
     */
    private final ConcurrentHashMap<String, Sinks.Many<String>> runSinks = new ConcurrentHashMap<>();

    /**
     * 创建 Agent Run（HTTP 层调用，立即返回 runId）
     *
     * <p>这是 POST /api/agent/runs 接口的处理逻辑。
     * 创建记录后立即返回 runId，实际分析在 executeRun() 异步执行。
     */
    public AgentRun createRun(String projectId, String query) {
        // 校验项目存在且已索引
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> PiaException.projectNotFound(projectId));

        if (project.getStatus() != Project.ProjectStatus.INDEXED) {
            throw PiaException.indexNotReady(projectId);
        }

        // 创建 AgentRun 记录
        String runId = UUID.randomUUID().toString();
        AgentRun run = AgentRun.builder()
            .id(runId)
            .projectId(projectId)
            .traceId(UUID.randomUUID().toString())
            .query(query)
            .status(AgentRun.RunStatus.PENDING)
            .build();
        agentRunRepository.save(run);

        // 创建 SSE Sink（前端可以立即订阅，等待事件）
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        runSinks.put(runId, sink);

        // 异步启动 Agent 执行
        executeRun(runId, projectId, query, project.getName());

        return run;
    }

    /**
     * 获取 SSE 流（前端订阅用）
     *
     * <p>返回 Flux<String>，Spring MVC 会自动将其转换为 SSE 响应流。
     * 每个事件格式：data: {"type":"tool.started","data":"{...}"}
     */
    public reactor.core.publisher.Flux<String> getEventStream(String runId) {
        Sinks.Many<String> sink = runSinks.get(runId);
        if (sink == null) {
            // Run 已完成，SSE 流不再存在
            return reactor.core.publisher.Flux.empty();
        }
        return sink.asFlux();
    }

    /**
     * 异步执行 Agent 完整工作流
     *
     * <p>核心编排逻辑：Planner → Executor → Reviewer（循环）→ AnswerComposer
     */
    @Async("agentExecutor")
    protected void executeRun(String runId, String projectId, String query, String projectName) {
        log.info("[AgentRuntime] 开始执行 Agent Run: runId={}, query='{}'", runId, query);
        Sinks.Many<String> sink = runSinks.get(runId);

        try {
            // ── 更新状态 → PLANNING ──
            updateStatus(runId, AgentRun.RunStatus.PLANNING);
            publishEvent(sink, "run.started", "{\"runId\":\"" + runId + "\"}");
            publishEvent(sink, "planning.started", "{}");

            // ── 意图分析（简单规则判断 TaskType）──
            // Phase 5 可以用 LLM 做更精准的意图分类
            ToolRegistry.TaskType taskType = inferTaskType(query);

            // ── Planner：生成调查计划 ──
            String plan = plannerService.generatePlan(query, projectId, projectName, taskType);
            savePlan(runId, plan);
                        publishEvent(sink, "planning.completed", "{\"plan\": \"计划已生成\"}");

            // ── Executor + Reviewer 循环（最多 maxIterations 次）──
            updateStatus(runId, AgentRun.RunStatus.EXECUTING);
            List<ExecutorService.ToolCallResult> allResults = new ArrayList<>();
            int maxIter = piaProperties.getAgent().getMaxIterations();

            for (int iteration = 0; iteration < maxIter; iteration++) {
                log.info("[AgentRuntime] 第 {} 次规划-执行循环", iteration + 1);

                // Executor：按计划调用 Tools
                List<ExecutorService.ToolCallResult> newResults = executorService.executePlan(
                    plan, runId, projectId, event -> publishEvent(sink, event.type(), event.data())
                );
                allResults.addAll(newResults);

                // Reviewer：检查证据完整性
                updateStatus(runId, AgentRun.RunStatus.REVIEWING);
                                ReviewerService.ReviewResult review = reviewerService.review(
                    query, allResults, iteration + 1
                );
                publishEvent(sink, "review.completed",
                    "{\"needsMore\": " + review.needsMoreInvestigation() + "}");

                if (!review.needsMoreInvestigation()) {
                    // 证据充分，退出循环
                    log.info("[AgentRuntime] Reviewer 确认证据充分，退出循环");
                    break;
                }

                // 证据不足：Planner 重新规划（基于 Reviewer 的反馈）
                log.info("[AgentRuntime] Reviewer 要求补充调查，重新规划");
                plan = plannerService.generatePlan(
                    query + "\n\nPrevious review: " + review.reviewOutput(),
                    projectId, projectName, taskType
                );
            }

            // ── AnswerComposer：生成最终报告（流式 SSE）──
            String evidenceSummary = buildEvidenceSummary(allResults);
                        String finalAnswer = answerComposerService.composeAnswer(
                query, evidenceSummary,
                reviewerService.review(query, allResults, maxIter).reviewOutput(),
                runId,
                event -> publishEvent(sink, event.type(), event.data())
            );

            // ── 保存最终结果 ──
            AgentRun run = agentRunRepository.findById(runId).orElseThrow();
            run.setFinalAnswer(finalAnswer);
            run.setStatus(AgentRun.RunStatus.COMPLETED);
            run.setFinishedAt(LocalDateTime.now());
            agentRunRepository.save(run);

            publishEvent(sink, "run.completed", "{\"runId\":\"" + runId + "\"}");
            log.info("[AgentRuntime] Agent Run 完成: runId={}", runId);

        } catch (Exception e) {
            log.error("[AgentRuntime] Agent Run 失败: runId={}", runId, e);
            AgentRun run = agentRunRepository.findById(runId).orElse(null);
            if (run != null) {
                       run.setStatus(AgentRun.RunStatus.FAILED);
                run.setErrorMessage(e.getMessage());
                run.setFinishedAt(LocalDateTime.now());
                agentRunRepository.save(run);
            }
            publishEvent(sink, "run.failed",
                "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        } finally {
            // 标记 Sink 完成，前端 SSE 连接自动关闭
            if (sink != null) {
                sink.tryEmitComplete();
                runSinks.remove(runId);
            }
        }
    }

    // ──── 工具方法 ────

    private void publishEvent(Sinks.Many<String> sink, String type, String data) {
        if (sink == null) return;
        String event = "{\"type\":\"" + type + "\",\"data\":" + data + "}";
        sink.tryEmitNext(event);
    }

    private void updateStatus(String runId, AgentRun.RunStatus status) {
        agentRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(status);
            if (status == AgentRun.RunStatus.PLANNING || status == AgentRun.RunStatus.EXECUTING) {
                run.setStartedAt(LocalDateTime.now());
            }
            agentRunRepository.save(run);
        });
    }

    private void savePlan(String runId, String plan) {
        agentRunRepository.findById(runId).ifPresent(run -> {
            run.setPlan(plan);
            agentRunRepository.save(run);
        });
    }

    /**
     * 简单的意图分类（MVP 版本）
     *
     * <p>通过关键词判断任务类型，决定 Planner 使用哪套工具。
     * Phase 5 可以用 LLM 做分类，提高准确率。
     */
    private ToolRegistry.TaskType inferTaskType(String query) {
        String lower = query.toLowerCase();
        if (lower.contains("bug") || lower.contains("错误") || lower.contains("异常") ||
            lower.contains("失败") || lower.contains("问题")) {
            return ToolRegistry.TaskType.BUG_ANALYSIS;
        }
        if (lower.contains("影响") || lower.contains("impact") || lower.contains("修改")) {
                    return ToolRegistry.TaskType.IMPACT_ANALYSIS;
        }
        if (lower.contains("谁调用") || lower.contains("哪里") || lower.contains("在哪")) {
            return ToolRegistry.TaskType.CODE_NAVIGATION;
        }
        return ToolRegistry.TaskType.REQUIREMENT_ANALYSIS;
    }

    private String buildEvidenceSummary(List<ExecutorService.ToolCallResult> results) {
        StringBuilder sb = new StringBuilder();
        for (ExecutorService.ToolCallResult r : results) {
            if ("SUCCESS".equals(r.status())) {
                sb.append("## ").append(r.toolName()).append("\n");
                sb.append(r.output().length() > 2000
                    ? r.output().substring(0, 2000) + "..." : r.output()).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
