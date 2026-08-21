package com.example.pia.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 安全拦截器
 *
 * <p>【Phase 6：所有 API 请求的安全检查入口】
 *
 * <p>拦截器在 Controller 之前执行，负责：
 * <ol>
 *   <li>Prompt Injection 检测（拦截恶意请求参数）</li>
 *   <li>Rate Limit 检查（防止 LLM Token 被打穿）</li>
 *   <li>请求上下文记录（用于审计日志的 IP/User-Agent）</li>
 * </ol>
 *
 * <p>规格 10-security.md 要求的防护措施全部在此统一入口实现，
 * 避免分散在各 Controller 中产生遗漏。
 *
 * <p>需在 {@link WebMvcConfig} 中注册才生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityInterceptor implements HandlerInterceptor {

    private final PromptInjectionGuard promptInjectionGuard;
    private final RateLimiter rateLimiter;
    private final PersistentAuditLogger auditLogger;
        private final ProjectAuthorizationService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String clientIp = getClientIp(request);
        String userId = authService.getCurrentUserId();
        String uri = request.getRequestURI();

        // ── 1. Rate Limit（针对 Agent Run 创建接口）──
        if (uri.endsWith("/agent/runs") && "POST".equals(request.getMethod())) {
            if (!rateLimiter.isAgentRunAllowed(userId)) {
                log.warn("[Security] Rate limit 触发: user={} ip={}", userId, clientIp);
                auditLogger.logSecurityViolation(userId, "RATE_LIMIT_EXCEEDED",
                    Map.of("uri", uri), clientIp);
                response.setStatus(429);
                response.getWriter().write("{\"code\":\"RATE_LIMIT\",\"message\":\"请求过于频繁，请稍后再试\"}");
                return false;
            }
        }

        // ── 2. Prompt Injection 检测（针对包含 query 参数的 POST 请求）──
        // 只检查 URL 参数（Body 的检测在 Controller 层通过 @RequestBody 完成）
        String queryParam = request.getParameter("query");
        if (queryParam != null && promptInjectionGuard.isSuspicious(queryParam)) {
            log.warn("[Security] 疑似 Prompt Injection: user={} ip={} query={}",
                userId, clientIp, queryParam.substring(0, Math.min(50, queryParam.length())));
            auditLogger.logSecurityViolation(userId, "PROMPT_INJECTION_ATTEMPT",
                Map.of("uri", uri, "queryPreview",
                    queryParam.substring(0, Math.min(50, queryParam.length()))), clientIp);
            response.setStatus(400);
            response.getWriter().write("{\"code\":\"SUSPICIOUS_INPUT\",\"message\":\"请求内容包含可疑指令\"}");
            return false;
        }

        return true;
    }

    /**
     * 获取真实客户端 IP（支持代理和负载均衡场景）
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For 格式：client, proxy1, proxy2
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        return xRealIp != null ? xRealIp : request.getRemoteAddr();
    }
}
