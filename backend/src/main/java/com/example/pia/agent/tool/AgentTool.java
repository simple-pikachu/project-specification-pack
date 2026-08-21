package com.example.pia.agent.tool;

/**
 * Agent Tool 接口（所有工具的统一契约）
 *
 * <p>【Agent 开发：Tool 是什么？】
 *
 * <p>Tool 是 Agent 与外部世界交互的唯一方式。
 * 规格文档（AGENTS.md）明确要求：
 * "禁止 LLM 直接访问文件系统、数据库或 Shell，所有外部能力必须通过 Tool/MCP Adapter。"
 *
 * <p>为什么要这样约束？
 * <ul>
 *   <li>安全：LLM 无法直接执行危险操作（删库、写文件、执行Shell）</li>
 *   <li>可追踪：每次 Tool 调用都记录在 tool_call 表，有完整审计日志</li>
 *   <li>可测试：Tool 有明确的 inputSchema 和 outputSchema，可以独立测试</li>
 *   <li>可限制：可以设置 timeout、retry、权限等约束</li>
 * </ul>
 *
 * <p>MVP 工具列表（规格 03-agent-design.md §5）：
 * <ul>
 *   <li>{@code code_search}：搜索代码符号（exact/keyword）</li>
 *   <li>{@code code_read}：读取文件指定行范围</li>
 *   <li>{@code graph_neighbors}：查询调用关系图邻居</li>
 *   <li>{@code schema_search}：查询数据库元数据（只读）</li>
 *   <li>{@code rag_search}：语义搜索代码片段</li>
 * </ul>
 *
 * <p>每个工具必须声明：
 * <ul>
 *   <li>name：工具名（LLM 调用时使用）</li>
 *   <li>description：给 LLM 看的工具描述（决定 LLM 是否选择这个工具）</li>
 *   <li>inputSchema：输入参数 JSON Schema（用于参数校验）</li>
 *   <li>execute()：实际执行逻辑</li>
  * </ul>
 */
public interface AgentTool {

    /** 工具名称（LLM 用此名称调用工具） */
    String getName();

    /**
     * 工具描述（给 LLM 看的）
     *
     * <p>描述质量直接影响 LLM 的工具选择决策。
     * 好的描述：说清楚"什么时候用"、"能做什么"、"不能做什么"。
     * 例如："搜索代码符号（类名、方法名）。用于找某个具体符号的位置。
     *       不支持自然语言查询，请用 rag_search 处理语义查询。"
     */
    String getDescription();

    /**
     * 输入参数 JSON Schema（字符串形式）
     *
     * <p>LLM 生成 Tool Call 时，Spring AI 会校验参数是否符合 Schema。
     * 不合规的参数会被拒绝，防止 LLM 传入格式错误的参数。
     */
    String getInputSchema();

    /**
     * 执行工具
     *
     * @param params   LLM 生成的参数（JSON 字符串，已通过 Schema 校验）
     * @param runId    当前 Agent Run ID（用于写入 tool_call 记录）
     * @param projectId 目标项目 ID
     * @return 工具执行结果（JSON 字符串，返回给 LLM 作为下一步决策依据）
     */
    String execute(String params, String runId, String projectId);

    /** 工具调用超时（秒），默认 10 秒 */
    default int getTimeoutSeconds() {
        return 10;
    }

    /**
     * 工具权限级别
     *
          * <p>MVP 只开放 READ 和 ANALYZE，禁止 WRITE/ADMIN 工具。
     */
    default Permission getPermission() {
        return Permission.READ;
    }

    enum Permission {
        READ,    // 只读（查询符号、读取文件）
        ANALYZE, // 分析（搜索、图遍历）
        WRITE,   // 写（修改代码——V0.3 才开放）
        ADMIN    // 管理（系统操作）
    }
}
