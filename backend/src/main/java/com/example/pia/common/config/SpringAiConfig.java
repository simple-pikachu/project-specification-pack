package com.example.pia.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置
 *
 * <p>【Agent 开发：Spring AI ChatClient 是什么？】
 *
 * <p>{@link ChatClient} 是 Spring AI 对 LLM 的统一访问接口（类似 Spring Data 之于数据库）。
 * 底层支持 OpenAI、Azure OpenAI、Anthropic、Ollama 等多种 LLM 提供商，
 * 通过 application.yml 配置切换，业务代码无需修改。
 *
 * <p>使用方式：
 * <pre>
 * chatClient.prompt()
 *   .system("你是一个代码分析 Agent...")
 *   .user("订单取消功能影响哪些代码？")
 *   .call()
 *   .content()    // 同步返回完整文本
 *
 * // 或流式输出：
 * chatClient.prompt()...stream().content()  // 返回 Flux<String>
 * </pre>
 *
 * <p>为什么创建这个 Bean 而不直接注入 ChatModel？
 * ChatClient 是 ChatModel 的高级封装，提供：
 * <ul>
 *   <li>流式 API（.stream()）</li>
 *   <li>默认 System Prompt（.defaultSystem()）</li>
 *   <li>Tool 注入（.tools()）</li>
 *   <li>Advisor（拦截器，可用于日志、重试等）</li>
 * </ul>
 */
@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
