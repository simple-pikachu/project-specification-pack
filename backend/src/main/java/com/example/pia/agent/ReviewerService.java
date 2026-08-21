package com.example.pia.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reviewer 服务 — Agent 的"质检"层
 *
 * <p>【Agent 开发：Reviewer 是什么？为什么需要它？】
 *
 * <p>Reviewer 是 PIA 与普通 LLM 问答最大的区别之一。
 * 普通 ChatGPT：用户问 → LLM 答，没有检查环节，可能有幻觉。
 * PIA Agent：收集证据 → Reviewer 审查 → 确保结论有据可依。
 *
 * <p>Reviewer 的 7 项检查（规格 03-agent-design.md §6）：
 * <ol>
 *   <li>每个重要结论是否有 Evidence？</li>
 *   <li>Evidence 是否真的支持结论？（不能断章取义）</li>
 *   <li>是否遗漏前端/后端/DB 的任一层？</li>
 *   <li>是否区分了"事实"和"建议"？</li>
 *   <li>是否存在未验证假设？</li>
 *   <li>是否出现虚构路径/方法？</li>
 *   <li>是否明确了索引缺失？</li>
 * </ol>
 *
 * <p>如果 Reviewer 发现证据不足（例如：缺少后端 Controller 的证据），
 * 它会输出 needsMoreInvestigation=true，触发 Planner 重新规划（最多 5 次循环）。
 *
 * <p>这个"质检→重新规划"的循环机制，让 Agent 能自我纠错，
 * 避免带着残缺证据输出不可靠的结论。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewerService {

    private final ChatClient chatClient;

    /**
     * 审查证据完整性
     *
     * @param userQuery     原始用户问题
     * @param toolResults   Executor 收集的所有 Tool 调用结果
     * @param iterationCount 当前是第几次规划-执行循环
     * @return 审查结果：是否需要更多调查 + 审查意见
     */
    public ReviewResult review(String userQuery, List<ExecutorService.ToolCallResult> toolResults,
                               int iterationCount) {
        log.info("[Reviewer] 开始证据审查: 共 {} 个 Tool 结果, 第 {} 次循环",
            toolResults.size(), iterationCount);

        // 构建证据摘要（将所有 Tool 结果汇总给 Reviewer LLM）
        StringBuilder evidenceSummary = new StringBuilder();
        for (ExecutorService.ToolCallResult result : toolResults) {
            evidenceSummary.append("## Tool: ").append(result.toolName())
                .append(" (").append(result.status()).append(")\n");
            evidenceSummary.append(truncate(result.output(), 1000)).append("\n\n");
        }

                String systemPrompt = """
            You are a code analysis reviewer. Your job is to check if the gathered evidence
            is sufficient to answer the user's question.

            Check these 7 points:
            1. Does every major conclusion have supporting evidence (file path + line number)?
            2. Is the evidence actually relevant to the conclusion?
            3. Are all layers covered: frontend, backend, database?
            4. Are facts clearly separated from recommendations?
            5. Are there unverified assumptions?
            6. Are there any fabricated file paths or method names?
            7. Is it clear when the index is incomplete?

            Output JSON:
            {
              "needsMoreInvestigation": true/false,
              "missingEvidence": ["what is missing and why"],
              "suggestedNextQueries": ["specific follow-up tool queries"],
              "reviewSummary": "brief review assessment"
                   }

            If this is iteration >= 3, be more lenient (accept partial evidence to avoid infinite loops).
            Current iteration: %d
            """.formatted(iterationCount);

        String userPrompt = """
            User question: %s

            Evidence gathered:
            %s

            Is this evidence sufficient? What's missing?
            """.formatted(userQuery, evidenceSummary);

        String reviewOutput = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content();

        log.debug("[Reviewer] 审查结果: {}", reviewOutput);

        // 解析 Reviewer 输出
        boolean needsMore = reviewOutput.contains("\"needsMoreInvestigation\": true");
        // 第 3 次以后强制终止循环（防止无限重规划）
        if (iterationCount >= 3) needsMore = false;

        return new ReviewResult(needsMore, reviewOutput, evidenceSummary.toString());
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** 审查结果 */
    public record ReviewResult(
        boolean needsMoreInvestigation,
        String reviewOutput,
        String evidenceSummary
    ) {}
}
