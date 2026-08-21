package com.example.pia.evaluation;

import com.example.pia.agent.AgentRuntime;
import com.example.pia.agent.domain.AgentRun;
import com.example.pia.agent.repository.AgentRunRepository;
import com.example.pia.evaluation.domain.EvaluationCase;
import com.example.pia.evaluation.domain.EvaluationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Evaluation 服务
 *
 * <p>【Agent 开发：Evaluation 是持续质量的核心机制】
 *
 * <p>Evaluation 解决的问题：
 * "我修改了 Planner 的 Prompt，Agent 的整体质量是提高了还是降低了？"
 *
 * <p>如果没有 Evaluation，修改 Prompt 就是"盲飞"——
 * 改一个地方可能造成另一个地方退化，而你完全不知道。
 *
 * <p>Evaluation 流程：
 * <ol>
 *   <li>从 evaluation_case 表拿到所有用例</li>
  *   <li>对每个用例，用 AgentRuntime 真实执行一次（产生 agent_run）</li>
 *   <li>对比 agent_run 的 finalAnswer 与 expected（检查关键元素）</li>
 *   <li>计算各项指标（Task Success Rate / Evidence Coverage 等）</li>
 *   <li>与质量门禁阈值比较，输出通过/不通过</li>
 * </ol>
 *
 * <p>规格 08-testing-and-evaluation.md §4 的质量门禁（MVP）：
 * <ul>
 *   <li>Task Success Rate ≥ 0.80</li>
 *   <li>Evidence Coverage ≥ 0.95</li>
 *   <li>Unsupported Claim Rate ≤ 0.05</li>
 *   <li>Tool Success Rate ≥ 0.98</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final AgentRuntime agentRuntime;
    private final AgentRunRepository agentRunRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 质量门禁阈值（规格 08-testing-and-evaluation.md §4）
    private static final double MIN_TASK_SUCCESS_RATE = 0.80;
    private static final double MIN_EVIDENCE_COVERAGE = 0.95;

    /**
     * 运行评估套件（一次性跑所有用例）
     *
     * <p>每次修改 Prompt/Runtime 后调用此方法，验证质量没有退化。
          *
     * @param cases         评估用例列表
     * @param promptVersion 当前 Prompt 版本号（记录到结果中，便于版本对比）
     * @return 评估汇总报告
     */
    public EvalSuiteReport runSuite(List<EvaluationCase> cases, String promptVersion) {
        log.info("[Evaluation] 开始运行评估套件: {} 个用例, promptVersion={}", cases.size(), promptVersion);

        List<EvaluationResult> results = new ArrayList<>();
        int passed = 0;
        double totalScore = 0;

        for (EvaluationCase ec : cases) {
            log.debug("[Evaluation] 运行用例: {}", ec.getName());
            EvaluationResult result = runSingleCase(ec, promptVersion);
            results.add(result);
            if (result.isPassed()) passed++;
            totalScore += result.getScore() != null ? result.getScore() : 0;
        }

        double taskSuccessRate = cases.isEmpty() ? 0 : (double) passed / cases.size();
        double avgScore = cases.isEmpty() ? 0 : totalScore / cases.size();

        // 质量门禁检查
                boolean gatePass = taskSuccessRate >= MIN_TASK_SUCCESS_RATE;

        EvalSuiteReport report = new EvalSuiteReport(
            cases.size(), passed, taskSuccessRate, avgScore, gatePass, promptVersion,
            LocalDateTime.now(), results
        );

        log.info("[Evaluation] 评估完成: taskSuccessRate={}, gatePass={}",
            String.format("%.2f", taskSuccessRate), gatePass);
        return report;
    }

    /**
     * 运行单个评估用例
     *
     * <p>通过 AgentRuntime 真实执行问题，等待完成后评分。
     * 这是"真实 E2E 测试"，不 Mock 任何组件。
     */
    private EvaluationResult runSingleCase(EvaluationCase ec, String promptVersion) {
        long startTime = System.currentTimeMillis();
        String resultId = UUID.randomUUID().toString();

        try {
            // 通过 AgentRuntime 提交真实问题
            AgentRun run = agentRuntime.createRun(ec.getProjectId(), ec.getQuery());
            String runId = run.getId();

            // 等待 Agent Run 完成（最多 120 秒）
                        AgentRun completedRun = waitForCompletion(runId, 120);

            if (completedRun == null || completedRun.getStatus() != AgentRun.RunStatus.COMPLETED) {
                return buildFailResult(resultId, ec.getId(), runId, promptVersion,
                    System.currentTimeMillis() - startTime,
                    "Agent Run 未完成: " + (completedRun != null ? completedRun.getStatus() : "timeout"));
            }

            // 评分：检查 finalAnswer 中是否包含期望的关键元素
            double score = scoreAnswer(completedRun.getFinalAnswer(), ec.getExpected());
            boolean passed = score >= 0.7; // 70% 以上视为通过

            return EvaluationResult.builder()
                .id(resultId)
                .caseId(ec.getId())
                .agentRunId(runId)
                .promptVersion(promptVersion)
                .passed(passed)
                .score(score)
                .latencyMs(System.currentTimeMillis() - startTime)
                .build();

              } catch (Exception e) {
            log.error("[Evaluation] 用例执行失败: {}", ec.getName(), e);
            return buildFailResult(resultId, ec.getId(), null, promptVersion,
                System.currentTimeMillis() - startTime, e.getMessage());
        }
    }

    /**
     * 等待 Agent Run 完成（轮询，最多等待 maxSeconds 秒）
     */
    private AgentRun waitForCompletion(String runId, int maxSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Optional<AgentRun> runOpt = agentRunRepository.findById(runId);
            if (runOpt.isPresent()) {
                AgentRun run = runOpt.get();
                if (run.getStatus() == AgentRun.RunStatus.COMPLETED ||
                    run.getStatus() == AgentRun.RunStatus.FAILED) {
                    return run;
                }
            }
            TimeUnit.SECONDS.sleep(3); // 每 3 秒轮询一次
                }
        return agentRunRepository.findById(runId).orElse(null);
    }

    /**
     * 评分：检查 Agent 答案中是否包含期望的关键元素
     *
     * <p>评分逻辑（MVP 简化版）：
     * <ul>
     *   <li>检查 expectedSymbols 中的每个符号名是否出现在答案中</li>
     *   <li>检查 expectedFiles 中的每个文件名是否出现在答案中</li>
     *   <li>命中率 = 命中数 / 期望总数</li>
     * </ul>
     *
     * <p>Phase 5 可以用 LLM 做更精准的语义评分（G-Eval、Chain-of-Thought 评估）。
     */
    private double scoreAnswer(String answer, String expectedJson) {
        if (answer == null || answer.isBlank()) return 0.0;

        try {
            JsonNode expected = MAPPER.readTree(expectedJson);
            int totalExpected = 0;
            int matched = 0;
            String lowerAnswer = answer.toLowerCase();

            // 检查期望符号
            JsonNode symbols = expected.get("expectedSymbols");
            if (symbols != null && symbols.isArray()) {
                for (JsonNode sym : symbols) {
                    totalExpected++;
                                    if (lowerAnswer.contains(sym.asText().toLowerCase())) matched++;
                }
            }

            // 检查期望文件
            JsonNode files = expected.get("expectedFiles");
            if (files != null && files.isArray()) {
                for (JsonNode file : files) {
                    totalExpected++;
                    if (lowerAnswer.contains(file.asText().toLowerCase())) matched++;
                }
            }

            // 检查概念关键词
            JsonNode concepts = expected.get("expectedConcepts");
            if (concepts != null && concepts.isArray()) {
                for (JsonNode concept : concepts) {
                    totalExpected++;
                    if (lowerAnswer.contains(concept.asText().toLowerCase())) matched++;
                }
            }

            // 检查必须不含的内容（幻觉检测）
            JsonNode mustNot = expected.get("mustNotContain");
            if (mustNot != null && mustNot.isArray()) {
                               for (JsonNode bad : mustNot) {
                    if (lowerAnswer.contains(bad.asText().toLowerCase())) {
                        // 包含幻觉内容，扣分
                        return Math.max(0, totalExpected > 0 ? (double) matched / totalExpected - 0.3 : 0);
                    }
                }
            }

            return totalExpected == 0 ? 1.0 : (double) matched / totalExpected;

        } catch (Exception e) {
            log.warn("[Evaluation] 评分解析失败: {}", e.getMessage());
            return 0.0;
        }
    }

    private EvaluationResult buildFailResult(String id, String caseId, String runId,
                                             String promptVersion, long latency, String reason) {
        return EvaluationResult.builder()
            .id(id).caseId(caseId).agentRunId(runId)
            .promptVersion(promptVersion).passed(false).score(0.0)
            .details("{\"error\": \"" + reason + "\"}")
                       .latencyMs(latency).build();
    }

    /**
     * 评估套件报告
     *
     * <p>包含汇总指标和每个用例的详细结果，
     * 供 CI/CD 系统判断是否允许发布。
     */
    public record EvalSuiteReport(
        int totalCases,
        int passedCases,
        double taskSuccessRate,   // 通过率（≥0.80 才算过质量门禁）
        double avgScore,          // 平均评分
        boolean gatePass,         // 质量门禁是否通过
        String promptVersion,
        LocalDateTime runAt,
        List<EvaluationResult> results
    ) {}
}
