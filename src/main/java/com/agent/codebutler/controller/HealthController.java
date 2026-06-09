package com.agent.codebutler.controller;

import com.agent.codebutler.dto.ApiResponse;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查控制器
 * 提供轻量级应用健康检查（Actuator /actuator/health 的补充）
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HarnessAgent agent;

    public HealthController(HarnessAgent agent) {
        this.agent = agent;
    }

    @GetMapping("/health")
    public ApiResponse<Object> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "agent", agent.getName()
        ));
    }
}
