package com.example.pia.api;

import com.example.pia.agent.AgentRuntime;
import com.example.pia.agent.domain.AgentRun;
import com.example.pia.agent.repository.AgentRunRepository;
import com.example.pia.agent.repository.ToolCallRepository;
import com.example.pia.common.exception.PiaException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent 运行 REST API + SSE 流式输出
 *
 * <p>【Agent 开发：这是前端与 Agent 交互的入口】
 *
 * <p>API 设计：
 * <ul>
 *   <li>POST /api/agent/runs — 提交问题，获取 runId（202 Accepted，异步执行）</li>
 *   <li>GET /api/agent/runs/{runId}/events — SSE 订阅（前端实时接收进度）</li>
 *   <li>GET /api/agent/runs/{runId} — 查询运行结果（分析完成后查最终报告）</li>
 * </ul>
 *
 * <p>SSE（Server-Sent Events）是一种 HTTP 单向实时推送协议：
 * <ul>
 *   <li>服务端：返回 Content-Type: text/event-stream，持续写入 data: ... 行</li>
 *   <li>前端：通过 EventSource API 订阅，每条事件触发 onmessage 回调</li>
 *   <li>优势：比 WebSocket 简单，基于 HTTP，天然支持代理和负载均衡</li>
 * </ul>
 *
 * <p>规格 06-api-and-data-model.md 定义的 SSE 事件类型：
 * run.started / planning.started / tool.started / tool.completed /
 * review.completed / answer.delta / run.completed / run.failed
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRuntime agentRuntime;
    private final AgentRunRepository agentRunRepository;
    private final ToolCallRepository toolCallRepository;

    /**
     * POST /api/agent/runs — 创建 Agent Run（提交分析请求）
     *
     * <p>返回 202 Accepted：分析任务已提交，后台异步执行。
     * 前端拿到 runId 后，立即建立 SSE 连接订阅进度。
     */
    @PostMapping("/agent/runs")
    public ResponseEntity<RunCreatedResponse> createRun(
            @Valid @RequestBody CreateRunRequest request) {
        AgentRun run = agentRuntime.createRun(request.projectId(), request.query());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new RunCreatedResponse(run.getId(), run.getTraceId(),
                "Agent Run 已创建，请订阅 SSE 事件流获取进度"));
    }

    /**
     * GET /api/agent/runs/{runId}/events — SSE 事件流（Agent 执行进度实时推送）
     *
     * <p>前端通过 EventSource 订阅此接口：
     * <pre>
     * const es = new EventSource('/api/agent/runs/' + runId + '/events');
     * es.onmessage = (e) => {
     *   const event = JSON.parse(e.data);
     *   // 处理不同类型的事件：planning.started / tool.completed / answer.delta 等
     * };
     * </pre>
     *
     * <p>Spring MVC 识别到 {@code Flux<String>} 返回类型 + {@code MediaType.TEXT_EVENT_STREAM}，
     * 自动将其转换为 SSE 响应，每条字符串作为一个 SSE data 行推送。
     *
     * <p>这就是为什么 pom.xml 引入了 spring-boot-starter-webflux：
     * 即使主框架用 Spring MVC，WebFlux 的响应式类型（Flux）也能在 MVC 中使用。
     */
    @GetMapping(value = "/agent/runs/{runId}/events",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getEventStream(@PathVariable String runId) {
        return agentRuntime.getEventStream(runId);
    }

    /**
     * GET /api/agent/runs/{runId} — 查询 Agent Run 详情（分析完成后获取最终报告）
     */
    @GetMapping("/agent/runs/{runId}")
    public RunDetailResponse getRun(@PathVariable String runId) {
        AgentRun run = agentRunRepository.findById(runId)
            .orElseThrow(() -> PiaException.agentRunNotFound(runId));

        long toolCallCount = toolCallRepository.countByRunId(runId);

        return new RunDetailResponse(
            run.getId(), run.getProjectId(), run.getQuery(),
            run.getStatus().name(), run.getPlan(), run.getFinalAnswer(),
            toolCallCount, run.getStartedAt() != null ? run.getStartedAt().toString() : null,
            run.getFinishedAt() != null ? run.getFinishedAt().toString() : null,
            run.getErrorMessage()
        );
    }

    /**
     * GET /api/agent/runs/{runId}/tool-calls — 查询 Tool 调用列表（调试/追踪用）
     */
    @GetMapping("/agent/runs/{runId}/tool-calls")
    public List<Map<String, Object>> getToolCalls(@PathVariable String runId) {
        return toolCallRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
            .map(tc -> {
                // Map.of() 不允许 null value，且三元表达式 Long/int 类型不一致，改用 HashMap
                Map<String, Object> m = new HashMap<>();
                m.put("id", tc.getId() != null ? tc.getId() : "");
                m.put("toolName", tc.getToolName() != null ? tc.getToolName() : "");
                m.put("status", tc.getStatus() != null ? tc.getStatus().name() : "");
                m.put("latencyMs", tc.getLatencyMs() != null ? tc.getLatencyMs() : 0L);
                m.put("outputSummary", tc.getOutputSummary() != null ? tc.getOutputSummary() : "");
                return m;
            })
            .toList();
    }

    // ──── DTO ────

    record CreateRunRequest(
        @NotBlank(message = "projectId 不能为空") String projectId,
        @NotBlank(message = "query 不能为空")
        @Size(max = 2000, message = "问题长度不能超过 2000 字符") String query
    ) {}

    record RunCreatedResponse(String runId, String traceId, String message) {}

    record RunDetailResponse(
        String id, String projectId, String query, String status,
        String plan, String finalAnswer, long toolCallCount,
        String startedAt, String finishedAt, String errorMessage
    ) {}
}
