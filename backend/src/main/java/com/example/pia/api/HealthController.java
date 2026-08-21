package com.example.pia.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 自定义健康检查接口
 *
 * <p>除 Spring Actuator 的 /actuator/health 外，
 * 提供一个简洁的 /api/health 接口，方便前端和运维快速确认服务状态。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "pia-server",
            "timestamp", LocalDateTime.now().toString()
        );
    }
}
