package com.example.pia.agent;

import com.example.pia.agent.domain.AgentRun;
import com.example.pia.agent.repository.AgentRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Answer Composer 服务 — Agent 的"输出"层
 *
 * <p>【Agent 开发：AnswerComposer 是什么？】
 *
 * <p>AnswerComposer 是 Agent 工作流的最后一步。
 * Reviewer 确认证据充分后，AnswerComposer 调用 LLM 将证据整理成：
 * 规格定义的标准结构化报告（见 03-agent-design.md §7）
 *
 * <p>最终报告结构：
 * <ol>
 *   <li>需求理解（Requirement Understanding）</li>
 *   <li>当前行为（Current Behavior）</li>
 *   <li>影响分析（Impact Analysis）</li>
 *   <li>证据（Evidence）— 每条含文件路径+行号+代码摘录</li>
 *   <li>实现方案（Implementation Plan）</li>
 *   <li>API 变更（API Changes）</li>
 *   <li>数据库变更（Database Changes）</li>
 *   <li>代码变更（Code Changes）</li>
 *   <li>测试方案（Test Plan）</li>
  *   <li>风险（Risks）</li>
 *   <li>待确认事项（Open Questions）</li>
 * </ol>
 *
 * <p>规格 11-agent-development-guide §7 要求的最终回答规则：
 * <ul>
 *   <li>先给结论</li>
 *   <li>再给证据（具体文件路径 + 行号）</li>
 *   <li>再给方案</li>
 *   <li>最后给风险和待确认项</li>
 *   <li>禁止"我认为可能……"而没有说明依据</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerComposerService {

    private final ChatClient chatClient;
    private final AgentRunRepository agentRunRepository;

    /**
     * 生成最终分析报告（流式 SSE 输出）
     *
     * <p>使用 Spring AI 的流式输出（Streaming）：
     * LLM 生成 token → 立即通过 SSE 推送给前端
     * 用户看到"打字机效果"，不需要等待全部完成。
     *
     * @param userQuery      原始用户问题
     * @param evidenceSummary Executor 收集的证据汇总
     * @param reviewOutput   Reviewer 的审查意见
     * @param runId          Agent Run ID
     * @param eventPublisher SSE 推送器
     * @return 完整的最终报告文本
     */
    public String composeAnswer(String userQuery, String evidenceSummary,
                                                                String reviewOutput, String runId,
                                Consumer<ExecutorService.SseEvent> eventPublisher) {
        log.info("[AnswerComposer] 开始生成最终报告: runId={}", runId);

        String systemPrompt = """
            You are a software analysis expert generating a structured analysis report.

            CRITICAL RULES:
            - Every conclusion MUST cite specific evidence (file path + line numbers)
            - Format: "Based on [file:line], we can confirm that..."
            - NEVER say "I think" or "possibly" without citing evidence
            - Clearly separate FACTS (from evidence) from RECOMMENDATIONS
            - If evidence is missing for something, say so explicitly

            Output a comprehensive report in Markdown with these sections:
            ## 需求理解 (Requirement Understanding)
            ## 当前行为 (Current Behavior)
            ## 影响分析 (Impact Analysis)
            ### 前端影响 (Frontend Impact)
              ### 后端影响 (Backend Impact)
            ### 数据库影响 (Database Impact)
            ## 实现方案 (Implementation Plan)
            ## API 变更 (API Changes)
            ## 数据库变更 (Database Changes)
            ## 修改文件 (Files to Change)
            ## 测试方案 (Test Plan)
            ## 风险 (Risks)
            ## 待确认事项 (Open Questions)
            ## 证据引用 (Evidence References)
            """;

        String userPrompt = """
            User question: %s

            Evidence gathered by investigation:
            %s

            Reviewer assessment:
            %s

            Generate the complete analysis report.
            """.formatted(userQuery, evidenceSummary, reviewOutput);

        // 流式调用：每个 token 到达时推送 SSE 事件给前端
        StringBuilder fullAnswer = new StringBuilder();

        chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .stream()
            .content()
            .doOnNext(chunk -> {
                            fullAnswer.append(chunk);
                // 每个文本块通过 SSE 推送给前端（前端实现打字机效果）
                eventPublisher.accept(new ExecutorService.SseEvent("answer.delta",
                    "{\"delta\": \"" + escapeJson(chunk) + "\"}"));
            })
            .blockLast(); // 等待流结束（在异步线程中阻塞是安全的）

        log.info("[AnswerComposer] 报告生成完成: {} 字", fullAnswer.length());
        return fullAnswer.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
