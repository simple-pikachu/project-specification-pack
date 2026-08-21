package com.example.pia.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池配置
 *
 * <p>【Agent 开发：为什么需要异步线程池？】
 *
 * <p>Agent 分析一次用户问题，可能需要：
 * <ul>
 *   <li>调用 LLM 规划（1-3秒）</li>
 *   <li>调用代码搜索工具（0.1-0.5秒 × 5-10次）</li>
 *   <li>调用 LLM 生成答案（2-5秒）</li>
 * </ul>
 * 总耗时可能达到 30-60 秒。
 *
 * <p>如果同步执行，HTTP 线程会被阻塞 30-60 秒，服务器并发能力极低。
 * 使用异步线程池：HTTP 请求立即返回 runId，Agent 在独立线程池中执行，
 * 前端通过 SSE 接收实时进度推送。
 *
 * <p>这就是 {@code @EnableAsync} + {@code @Async("agentExecutor")} 的组合模式。
 */
@Configuration
public class AsyncConfig {

    /**
     * Agent 执行专用线程池
     *
     * <p>独立线程池的原因：Agent 任务 CPU 消耗低但 IO 等待高（等 LLM 响应），
     * 与普通 HTTP 请求线程池分离，避免相互影响。
     */
    @Bean("agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：平时保持 5 个线程处理 Agent 任务
        executor.setCorePoolSize(5);
        // 最大线程数：高并发时最多 20 个并发 Agent
        executor.setMaxPoolSize(20);
        // 队列容量：超过 20 个并发时，最多排队 100 个
        executor.setQueueCapacity(100);
        // 线程名前缀：便于在日志/线程转储中识别 Agent 线程
        executor.setThreadNamePrefix("agent-");
        // 关闭时等待任务完成（避免 Agent 任务被强制中断）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
