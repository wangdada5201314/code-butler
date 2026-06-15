package com.agent.codebutler.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Agent 执行追踪 Middleware — 将推理/工具调用/子 Agent 事件推送到前端
 * <p>
 * 使用 {@link InheritableThreadLocal} 持有回调函数，Service 层在调用 Agent 前设置回调、
 * 调用后清理。Middleware 在 onReasoning/onActing 中通过回调推送 {@link AgentTraceEvent}。
 * <p>
 * 面试价值：展示 Middleware 架构 + 可观测性 + 前端实时可视化的完整链路。
 */
public class AgentTraceMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceMiddleware.class);

    /**
     * 追踪事件回调（由 Service 层设置，指向 SSE sink）。
     * 使用 InheritableThreadLocal 确保子线程（工具执行线程）能访问回调。
     */
    private final InheritableThreadLocal<Consumer<AgentTraceEvent>> traceConsumer = new InheritableThreadLocal<>();

    /** 推理轮次计数器 (sessionId -> round) */
    private final Map<String, AtomicInteger> reasoningRounds = new ConcurrentHashMap<>();

    /** 推理计时器 (sessionId -> startTime) */
    private final Map<String, Instant> reasoningTimers = new ConcurrentHashMap<>();

    /**
     * 设置当前请求的追踪事件消费者（Service 层在 Agent 调用前调用）
     */
    public void setTraceConsumer(Consumer<AgentTraceEvent> consumer) {
        traceConsumer.set(consumer);
    }

    /**
     * 清理当前线程的追踪消费者（必须在 Agent 调用结束后清理，防止线程池复用串号）
     */
    public void clearTraceConsumer() {
        traceConsumer.remove();
    }

    // ════════════════════════════════════════════════════════
    //  1. 推理追踪
    // ════════════════════════════════════════════════════════

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        String sessionId = extractSessionId(agent);
        Instant start = Instant.now();
        reasoningTimers.put(sessionId, start);

        int round = reasoningRounds
                .computeIfAbsent(sessionId, k -> new AtomicInteger(0))
                .incrementAndGet();

        int toolsCount = input.tools() != null ? input.tools().size() : 0;
        emitTrace(AgentTraceEvent.of(AgentTraceEvent.Type.REASONING_START, "推理轮次 #" + round,
                Map.of("round", round, "toolsAvailable", toolsCount)));

        log.debug("[Trace] 推理开始: session={}, round={}, tools={}", sessionId, round, toolsCount);

        return next.apply(input).doOnComplete(() -> {
            Duration elapsed = Duration.between(start, Instant.now());
            reasoningTimers.remove(sessionId);

            emitTrace(AgentTraceEvent.of(AgentTraceEvent.Type.REASONING_END, "推理完成",
                    Map.of("round", round, "elapsedMs", elapsed.toMillis())));

            log.debug("[Trace] 推理完成: session={}, round={}, elapsed={}ms",
                    sessionId, round, elapsed.toMillis());
        }).doOnError(e -> {
            Duration elapsed = Duration.between(start, Instant.now());
            reasoningTimers.remove(sessionId);

            emitTrace(AgentTraceEvent.of(AgentTraceEvent.Type.REASONING_END, "推理异常",
                    Map.of("round", round, "elapsedMs", elapsed.toMillis(), "error", e.getMessage())));
        });
    }

    // ════════════════════════════════════════════════════════
    //  2. 工具调用追踪
    // ════════════════════════════════════════════════════════

    @Override
    public Flux<AgentEvent> onActing(Agent agent, ActingInput input,
                                      Function<ActingInput, Flux<AgentEvent>> next) {
        List<?> toolCalls = input.toolCalls();
        List<String> toolNames = new ArrayList<>();
        Map<String, Map<String, Object>> toolArgs = new LinkedHashMap<>();

        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (Object tc : toolCalls) {
                if (tc instanceof ToolUseBlock block) {
                    String toolName = block.getName();
                    toolNames.add(toolName);
                    Map<String, Object> args = block.getInput();
                    // 参数摘要：只记录 key 和短值预览
                    Map<String, Object> argSummary = new LinkedHashMap<>();
                    if (args != null) {
                        for (var entry : args.entrySet()) {
                            String val = String.valueOf(entry.getValue());
                            argSummary.put(entry.getKey(),
                                    val.length() > 100 ? val.substring(0, 100) + "..." : val);
                        }
                    }
                    toolArgs.put(toolName, argSummary);

                    emitTrace(AgentTraceEvent.of(AgentTraceEvent.Type.TOOL_CALL_START, toolName,
                            Map.of("args", argSummary)));
                }
            }
        }

        Instant start = Instant.now();
        return next.apply(input).doOnComplete(() -> {
            Duration elapsed = Duration.between(start, Instant.now());
            for (String name : toolNames) {
                emitTrace(AgentTraceEvent.of(AgentTraceEvent.Type.TOOL_CALL_END, name,
                        Map.of("elapsedMs", elapsed.toMillis(), "status", "success")));
            }
            log.debug("[Trace] 工具执行完成: count={}, elapsed={}ms", toolNames.size(), elapsed.toMillis());
        }).doOnError(e -> {
            Duration elapsed = Duration.between(start, Instant.now());
            for (String name : toolNames) {
                emitTrace(AgentTraceEvent.of(AgentTraceEvent.Type.TOOL_CALL_END, name,
                        Map.of("elapsedMs", elapsed.toMillis(), "status", "error", "error", e.getMessage())));
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  内部方法
    // ════════════════════════════════════════════════════════

    private void emitTrace(AgentTraceEvent event) {
        Consumer<AgentTraceEvent> consumer = traceConsumer.get();
        if (consumer != null) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                log.warn("[Trace] 追踪事件发送失败: {}", e.getMessage());
            }
        }
    }

    private String extractSessionId(Agent agent) {
        return agent.getName() != null ? agent.getName() : "unknown";
    }
}
