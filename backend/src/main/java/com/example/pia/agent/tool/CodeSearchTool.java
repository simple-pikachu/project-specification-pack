package com.example.pia.agent.tool;

import com.example.pia.retrieval.RetrievalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * code_search Tool 实现
 *
 * <p>【Agent 开发：这是 Agent 最常用的工具】
 *
 * <p>当 Agent（Planner）决定"我需要找 OrderService 相关的代码"时，
 * 它会生成一个 Tool Call：
 * <pre>
 * {
 *   "tool": "code_search",
 *   "params": {"query": "OrderService", "limit": 10}
 * }
 * </pre>
 *
 * <p>Executor 捕获这个调用，转发给 CodeSearchTool.execute()，
 * execute() 调用 RetrievalService 做混合检索，
 * 返回结果给 LLM 作为下一步规划的依据。
 *
 * <p>Tool 是 Agent 与 Code Intelligence 层的桥梁：
 * LLM 通过 Tool 调用从知识库中"读"证据，而不是直接访问数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeSearchTool implements AgentTool {

    private final RetrievalService retrievalService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "code_search";
    }

    @Override
    public String getDescription() {
        return """
            搜索项目中的代码符号（类、方法、接口、Endpoint 等）。
            支持精确符号名搜索（如 "OrderService.cancel"）和关键词搜索（如 "cancel"）。
            返回匹配符号的全限定名、文件路径、行号和代码摘录。
            不支持自然语言语义查询，语义查询请用 rag_search。
            """;
    }

    @Override
    public String getInputSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "搜索关键词或符号全限定名"
                },
                "limit": {
                  "type": "integer",
                  "description": "最大返回数量，默认 10",
                  "default": 10
                }
              },
              "required": ["query"]
            }
            """;
    }

  @Override
    public String execute(String params, String runId, String projectId) {
        try {
            JsonNode node = MAPPER.readTree(params);
            String query = node.get("query").asText();
            int limit = node.has("limit") ? node.get("limit").asInt() : 10;

            log.debug("[Tool:code_search] query='{}', limit={}, runId={}", query, limit, runId);

            List<RetrievalService.RetrievalResult> results =
                retrievalService.retrieve(query, projectId, limit);

            // 构建返回给 LLM 的结果（JSON 格式，简洁但信息完整）
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < results.size(); i++) {
                RetrievalService.RetrievalResult r = results.get(i);
                sb.append("""
                    {
                      "qualifiedName": "%s",
                      "symbolType": "%s",
                      "startLine": %d,
                      "endLine": %d,
                                            "excerpt": "%s",
                      "source": "%s",
                      "relevanceScore": %.2f
                    }
                    """.formatted(
                    esc(r.qualifiedName()), esc(r.symbolType()),
                    r.startLine() != null ? r.startLine() : 0,
                    r.endLine() != null ? r.endLine() : 0,
                    esc(truncate(r.excerpt(), 300)),
                    esc(r.source()), r.relevanceScore()
                ));
                if (i < results.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();

        } catch (Exception e) {
            log.error("[Tool:code_search] 执行失败", e);
            return "{\"error\": \"code_search 执行失败: " + esc(e.getMessage()) + "\"}";
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    @Override
    public Permission getPermission() {
        return Permission.ANALYZE;
    }
}
