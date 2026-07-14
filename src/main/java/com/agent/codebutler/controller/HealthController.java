package com.agent.codebutler.controller;

import com.agent.codebutler.dto.ApiResponse;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * <p>
 * 提供应用级健康检查，验证核心组件连通性：
 * <ul>
 *     <li>MySQL — 执行 SELECT 1 验证数据库连接（可选，测试环境可跳过）</li>
 *     <li>Agent — 验证 HarnessAgent 已初始化</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final HarnessAgent agent;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public HealthController(HarnessAgent agent) {
        this.agent = agent;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "UP");
        details.put("agent", agent.getName());

        // MySQL 连通性检查（可选，测试环境可能未配置）
        if (jdbcTemplate != null) {
            String mysqlStatus = "UP";
            try {
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            } catch (Exception e) {
                mysqlStatus = "DOWN";
                details.put("status", "DEGRADED");
                log.warn("[Health] MySQL 检查失败: {}", e.getMessage());
            }
            details.put("mysql", mysqlStatus);
        } else {
            details.put("mysql", "N/A");
        }

        return ApiResponse.success(details);
    }
}
