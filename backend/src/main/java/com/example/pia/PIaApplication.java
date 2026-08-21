package com.example.pia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * PIA (Project Intelligence Agent) 应用启动入口
 *
 * <p>【Agent 开发：应用结构概览】
 *
 * <p>本项目实现了一个完整的"代码分析 Agent 平台"，架构分为以下层次：
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │                     用户 / Web UI                        │
 * └─────────────────────────┬───────────────────────────────┘
 *                           │ HTTP / SSE（实时流式输出）
 * ┌─────────────────────────▼───────────────────────────────┐
 * │                    REST API 层 (api/)                    │
 * │      接收用户问题，返回 Agent Run ID，推送执行进度        │
 * └─────────────────────────┬───────────────────────────────┘
 *                           │
 * ┌─────────────────────────▼───────────────────────────────┐
 * │                 Agent Runtime (agent/)                   │
 * │  Planner（规划）→ Executor（执行工具）→ Reviewer（审查） │
 * │  这是 Agent 的"大脑"，负责决策和协调                     │
 * └────────────────┬────────────────────┬────────────────────┘
 *                  │                    │
 * ┌────────────────▼──────┐ ┌──────────▼──────────────────┐
 * │     Tool 层 (tool/)    │ │    Code Intelligence 层     │
 * │  code_search, graph,  │ │  parser/ graph/ retrieval/  │
 * │  schema, git, rag...  │ │  AST 解析、符号提取、检索    │
 * └────────────────┬──────┘ └──────────┬──────────────────┘
 *                  │                   │
 * ┌────────────────▼────────────────────▼──────────────────┐
 * │                 Knowledge Layer（知识层）                │
 * │   MySQL（结构化）+ Qdrant（向量）+ Redis（缓存）          │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>@EnableAsync 启用异步执行：Agent 分析任务耗时较长（几秒到几十秒），
 * 必须异步执行，否则 HTTP 线程会被长时间占用。
 */
@SpringBootApplication
@EnableAsync
public class PiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PiaApplication.class, args);
    }
}
