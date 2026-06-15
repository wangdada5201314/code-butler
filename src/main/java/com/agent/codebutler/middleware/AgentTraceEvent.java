package com.agent.codebutler.middleware;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Agent 执行追踪事件
 * <p>
 * 由 {@link AgentTraceMiddleware} 在推理/工具调用/子 Agent 调度时生成，
 * 通过 SSE 推送到前端，驱动 AgentTimeline 可视化。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentTraceEvent(
        Type type,
        long timestamp,
        String name,
        Map<String, Object> data
) {
    public enum Type {
        REASONING_START,
        REASONING_END,
        TOOL_CALL_START,
        TOOL_CALL_END,
        SUBAGENT_START,
        SUBAGENT_END,
        PLAN_UPDATE
    }

    public static AgentTraceEvent of(Type type, String name, Map<String, Object> data) {
        return new AgentTraceEvent(type, Instant.now().toEpochMilli(), name, data);
    }

    public static AgentTraceEvent of(Type type, String name) {
        return new AgentTraceEvent(type, Instant.now().toEpochMilli(), name, null);
    }
}
