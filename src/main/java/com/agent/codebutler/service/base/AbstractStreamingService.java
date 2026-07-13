package com.agent.codebutler.service.base;

import com.agent.codebutler.config.AgentScopeProperties;
import com.agent.codebutler.middleware.AgentTraceEvent;
import com.agent.codebutler.middleware.AgentTraceMiddleware;
import com.agent.codebutler.tools.MemoryTools;
import com.agent.codebutler.util.TextUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * SSE 流式 Agent 调用的抽象基类
 * <p>
 * 封装公共 SSE 管道：trace 事件桥接、超时处理、错误恢复、资源清理。
 * 子类只需构建 Agent 事件 Flux，调用 {@link #executeStreamingSession} 即可获得完整的 SSE 输出流。
 * <p>
 * 消除 ChatService / CodeReviewService / GeneralChatService 中 ~50 行重复的 SSE 管道代码。
 */
public abstract class AbstractStreamingService {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected final HarnessAgent agent;
    protected final MemoryTools memoryTools;
    protected final AgentTraceMiddleware agentTraceMiddleware;
    protected final AgentScopeProperties agentScopeProperties;

    protected AbstractStreamingService(HarnessAgent agent,
                                       MemoryTools memoryTools,
                                       AgentTraceMiddleware agentTraceMiddleware,
                                       AgentScopeProperties agentScopeProperties) {
        this.agent = agent;
        this.memoryTools = memoryTools;
        this.agentTraceMiddleware = agentTraceMiddleware;
        this.agentScopeProperties = agentScopeProperties;
    }

    // ════════════════════════════════════════════════════════
    //  SSE 管道核心
    // ════════════════════════════════════════════════════════

    /**
     * 执行完整的流式 Agent 会话
     * <p>
     * 封装 SSE 管道：trace 事件桥接 → merge 主流 → 超时 → 错误恢复 → 清理。
     * 子类构建好 Agent 事件 Flux 后调用此方法。
     *
     * @param mainFlux        Agent 事件转换后的 SSE 流（子类负责构建）
     * @param sessionId       会话 ID（用于日志）
     * @param userId          用户 ID（用于 MemoryTools 设置和清理，可为 null）
     * @param timeoutSeconds  总超时秒数（含缓冲）
     * @param timeoutMessage  超时错误消息
     * @param errorContext    错误日志上下文描述
     * @return 完整的 SSE 事件流（含 trace、超时、错误处理）
     */
    protected Flux<ServerSentEvent<String>> executeStreamingSession(
            Flux<ServerSentEvent<String>> mainFlux,
            String sessionId,
            Long userId,
            int timeoutSeconds,
            String timeoutMessage,
            String errorContext) {

        // 设置 ThreadLocal（在 boundedElastic 线程上执行）
        if (userId != null) {
            memoryTools.setUserId(userId);
        }

        // Trace 事件桥接
        Sinks.Many<AgentTraceEvent> traceSink = Sinks.many().multicast().onBackpressureBuffer();
        agentTraceMiddleware.setTraceConsumer(event -> traceSink.tryEmitNext(event));

        Flux<ServerSentEvent<String>> traceFlux = traceSink.asFlux()
                .map(event -> {
                    try {
                        String json = OBJECT_MAPPER.writeValueAsString(event);
                        return ServerSentEvent.<String>builder().event("trace").data(json).build();
                    } catch (Exception e) {
                        return ServerSentEvent.<String>builder().build();
                    }
                });

        return Flux.merge(mainFlux, traceFlux)
                .timeout(Duration.ofSeconds(timeoutSeconds),
                        Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("error").data("[ERROR] " + timeoutMessage).build(),
                                ServerSentEvent.<String>builder()
                                        .event("done").data("[DONE]").build()))
                .onErrorResume(e -> {
                    log.error("流式{}异常: sessionId={}", errorContext, sessionId, e);
                    String safeMsg = TextUtils.sanitizeSseError(e.getMessage());
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error").data("[ERROR] " + safeMsg).build(),
                            ServerSentEvent.<String>builder()
                                    .event("done").data("[DONE]").build());
                })
                .doFinally(signal -> {
                    if (userId != null) memoryTools.clearUserId();
                    agentTraceMiddleware.clearTraceConsumer();
                });
    }

    /**
     * 使用默认超时（callTimeoutSeconds + 30）的便捷版本
     */
    protected Flux<ServerSentEvent<String>> executeStreamingSession(
            Flux<ServerSentEvent<String>> mainFlux,
            String sessionId,
            Long userId,
            String errorContext) {
        return executeStreamingSession(mainFlux, sessionId, userId,
                agentScopeProperties.getCallTimeoutSeconds() + 30,
                "请求超时", errorContext);
    }

    // ════════════════════════════════════════════════════════
    //  公共辅助方法
    // ════════════════════════════════════════════════════════

    /**
     * 构建 RuntimeContext
     */
    protected RuntimeContext buildRuntimeContext(String sessionId, String userId) {
        return RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();
    }

    /**
     * 创建工具调用通知 SSE 事件
     */
    protected ServerSentEvent<String> toolCallEvent(String toolName) {
        return ServerSentEvent.<String>builder()
                .event("tool")
                .data("[调用工具: " + toolName + "]")
                .build();
    }

    /**
     * 创建完成标记 SSE 事件
     */
    protected ServerSentEvent<String> doneEvent() {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build();
    }

    /**
     * 创建数据 SSE 事件（文本增量）
     */
    protected ServerSentEvent<String> dataEvent(String data) {
        return ServerSentEvent.<String>builder()
                .data(data)
                .build();
    }

    /**
     * 获取超时秒数（callTimeout + offset）
     */
    protected int getTimeoutWithOffset(int offsetSeconds) {
        return agentScopeProperties.getCallTimeoutSeconds() + offsetSeconds;
    }

    /**
     * Plan Mode 前缀指令（ChatService 和 GeneralChatService 共用）
     */
    protected static final String PLAN_MODE_PREFIX = """
            ## Plan Mode 已激活

            在执行任何操作前，你必须：
            1. **先分析用户意图**：理清用户想要什么
            2. **制定执行计划**：列出具体步骤，用编号 1. 2. 3. 标识
            3. **逐步执行**：每步执行后，使用 --- 分隔线标注进度
            4. **最终总结**：汇总执行结果

            重要：先输出 ## 执行计划 标题，再逐步执行。
            不要跳过规划直接执行！

            """;
}
