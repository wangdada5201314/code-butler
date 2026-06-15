package com.agent.codebutler.service;

import com.agent.codebutler.dto.GeneralChatRequest;
import com.agent.codebutler.middleware.AgentTraceEvent;
import com.agent.codebutler.middleware.AgentTraceMiddleware;
import com.agent.codebutler.tools.MemoryTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.UUID;

/**
 * 通用聊天服务 —— 不依赖代码仓库的自由对话
 */
@Service
public class GeneralChatService {

    private static final Logger log = LoggerFactory.getLogger(GeneralChatService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HarnessAgent agent;
    private final OperationRecordService operationRecordService;
    private final MemoryTools memoryTools;
    private final AgentTraceMiddleware agentTraceMiddleware;

    @Value("${agentscope.call-timeout-seconds:120}")
    private int agentCallTimeoutSeconds;

    public GeneralChatService(HarnessAgent agent,
                              OperationRecordService operationRecordService,
                              MemoryTools memoryTools,
                              AgentTraceMiddleware agentTraceMiddleware) {
        this.agent = agent;
        this.operationRecordService = operationRecordService;
        this.memoryTools = memoryTools;
        this.agentTraceMiddleware = agentTraceMiddleware;
    }

    /**
     * 执行通用流式对话
     */
    public Flux<ServerSentEvent<String>> streamChat(GeneralChatRequest request, Long userId) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "general-" + UUID.randomUUID().toString().substring(0, 8);

        String message = request.getMessage();
        boolean planMode = Boolean.TRUE.equals(request.getPlanMode());
        log.info("通用聊天: sessionId={}, userId={}, planMode={}", sessionId, userId, planMode);

        operationRecordService.recordAsync(userId, "CHAT", null,
                message, null, 0, sessionId, "COMPLETED",
                UsageService.estimateTokens(message));

        String agentUserId = userId != null ? "general-" + userId : "general-chat";

        return Mono.fromCallable(() -> {
                    // 在 boundedElastic 线程上设置 ThreadLocal，确保工具调用时能读到
                    if (userId != null) {
                        memoryTools.setUserId(userId);
                    }

                    // Trace 事件流：通过 Sink 桥接中间件的回调到 SSE 输出
                    Sinks.Many<AgentTraceEvent> traceSink = Sinks.many().multicast().onBackpressureBuffer();
                    agentTraceMiddleware.setTraceConsumer(event -> traceSink.tryEmitNext(event));

                    RuntimeContext ctx = RuntimeContext.builder()
                            .sessionId(sessionId)
                            .userId(agentUserId)
                            .build();

                    // Plan Mode 前缀
                    String planPrefix = planMode ? """
                            ## Plan Mode 已激活

                            在执行任何操作前，你必须：
                            1. **先分析用户意图**：理清用户想要什么
                            2. **制定执行计划**：列出具体步骤，用编号 1. 2. 3. 标识
                            3. **逐步执行**：每步执行后，使用 --- 分隔线标注进度
                            4. **最终总结**：汇总执行结果

                            重要：先输出 ## 执行计划 标题，再逐步执行。
                            不要跳过规划直接执行！

                            """ : "";

                    // 通用聊天：明确告知 Agent 不要使用代码相关工具
                    String prefixedMessage = planPrefix +
                            "[当前模式：通用聊天，不涉及代码仓库。请不要调用 search_code_knowledge、" +
                            "index_code_knowledge、search_code_files、count_code_lines 等代码工具，直接用你的知识回答即可。]\n\n" + message;

                    Flux<ServerSentEvent<String>> traceFlux = traceSink.asFlux()
                            .map(event -> {
                                try {
                                    String json = OBJECT_MAPPER.writeValueAsString(event);
                                    return ServerSentEvent.<String>builder().event("trace").data(json).build();
                                } catch (Exception e) {
                                    return ServerSentEvent.<String>builder().build();
                                }
                            });

                    Flux<ServerSentEvent<String>> mainFlux = agent.streamEvents(new UserMessage(prefixedMessage), ctx)
                            .flatMap(event -> {
                                if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                                    String delta = ((TextBlockDeltaEvent) event).getDelta();
                                    return Mono.just(ServerSentEvent.<String>builder()
                                            .data(delta)
                                            .build());
                                } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                                    String toolName = ((ToolCallStartEvent) event).getToolCallName();
                                    return Mono.just(ServerSentEvent.<String>builder()
                                            .event("tool")
                                            .data("[调用工具: " + toolName + "]")
                                            .build());
                                }
                                return Mono.<ServerSentEvent<String>>empty();
                            })
                            .concatWith(Flux.just(
                                    ServerSentEvent.<String>builder()
                                            .event("done")
                                            .data("[DONE]")
                                            .build()))
                            .onErrorResume(e -> {
                                log.error("通用聊天异常: sessionId={}", sessionId, e);
                                String safeMsg = sanitizeSseError(e.getMessage());
                                return Flux.just(
                                        ServerSentEvent.<String>builder()
                                                .event("error")
                                                .data("[ERROR] " + safeMsg)
                                                .build(),
                                        ServerSentEvent.<String>builder()
                                                .event("done")
                                                .data("[DONE]")
                                                .build());
                            });

                    return Flux.merge(mainFlux, traceFlux);
                })
                .flatMapMany(flux -> flux)
                .timeout(Duration.ofSeconds(agentCallTimeoutSeconds + 30),
                        Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("error")
                                        .data("[ERROR] 请求超时")
                                        .build(),
                                ServerSentEvent.<String>builder()
                                        .event("done")
                                        .data("[DONE]")
                                        .build()))
                .onErrorResume(e -> {
                    log.error("通用聊天启动失败: sessionId={}", sessionId, e);
                    String safeMsg = sanitizeSseError(e.getMessage());
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data("[ERROR] " + safeMsg)
                                    .build(),
                            ServerSentEvent.<String>builder()
                                    .event("done")
                                    .data("[DONE]")
                                    .build());
                })
                .doFinally(signal -> {
                    memoryTools.clearUserId();
                    agentTraceMiddleware.clearTraceConsumer();
                });
    }

    private String sanitizeSseError(String msg) {
        if (msg == null) return "未知错误";
        return msg.replace("\n", " ").replace("\r", "");
    }
}
