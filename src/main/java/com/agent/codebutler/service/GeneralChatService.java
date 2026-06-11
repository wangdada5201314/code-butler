package com.agent.codebutler.service;

import com.agent.codebutler.dto.GeneralChatRequest;
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

import java.time.Duration;
import java.util.UUID;

/**
 * 通用聊天服务 —— 不依赖代码仓库的自由对话
 */
@Service
public class GeneralChatService {

    private static final Logger log = LoggerFactory.getLogger(GeneralChatService.class);

    private final HarnessAgent agent;
    private final OperationRecordService operationRecordService;

    @Value("${agentscope.call-timeout-seconds:120}")
    private int agentCallTimeoutSeconds;

    public GeneralChatService(HarnessAgent agent,
                              OperationRecordService operationRecordService) {
        this.agent = agent;
        this.operationRecordService = operationRecordService;
    }

    /**
     * 执行通用流式对话
     */
    public Flux<ServerSentEvent<String>> streamChat(GeneralChatRequest request, Long userId) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "general-" + UUID.randomUUID().toString().substring(0, 8);

        String message = request.getMessage();
        log.info("通用聊天: sessionId={}, userId={}", sessionId, userId);

        operationRecordService.recordAsync(userId, "CHAT", null,
                message, null, 0, sessionId, "COMPLETED",
                UsageService.estimateTokens(message));

        String agentUserId = userId != null ? "general-" + userId : "general-chat";

        return Mono.fromCallable(() -> {
                    RuntimeContext ctx = RuntimeContext.builder()
                            .sessionId(sessionId)
                            .userId(agentUserId)
                            .build();

                    return agent.streamEvents(new UserMessage(message), ctx);
                })
                .flatMapMany(flux -> flux
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
                        })
                )
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
                });
    }

    private String sanitizeSseError(String msg) {
        if (msg == null) return "未知错误";
        return msg.replace("\n", " ").replace("\r", "");
    }
}
