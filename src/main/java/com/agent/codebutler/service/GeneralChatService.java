package com.agent.codebutler.service;

import com.agent.codebutler.config.AgentScopeProperties;
import com.agent.codebutler.dto.GeneralChatRequest;
import com.agent.codebutler.middleware.AgentTraceMiddleware;
import com.agent.codebutler.service.base.AbstractStreamingService;
import com.agent.codebutler.tools.MemoryTools;
import com.agent.codebutler.util.TextUtils;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 通用聊天服务 —— 不依赖代码仓库的自由对话
 */
@Service
public class GeneralChatService extends AbstractStreamingService {

    private final OperationRecordService operationRecordService;

    public GeneralChatService(HarnessAgent agent,
                              OperationRecordService operationRecordService,
                              MemoryTools memoryTools,
                              AgentTraceMiddleware agentTraceMiddleware,
                              AgentScopeProperties agentScopeProperties) {
        super(agent, memoryTools, agentTraceMiddleware, agentScopeProperties);
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
        boolean planMode = Boolean.TRUE.equals(request.getPlanMode());
        log.info("通用聊天: sessionId={}, userId={}, planMode={}", sessionId, userId, planMode);

        long startTime = System.currentTimeMillis();
        String agentUserId = userId != null ? "general-" + userId : "general-chat";

        // Plan Mode 前缀 + 通用聊天指令
        String planPrefix = planMode ? PLAN_MODE_PREFIX : "";
        String prefixedMessage = planPrefix +
                "[当前模式：通用聊天，不涉及代码仓库。请不要调用 search_code_knowledge、" +
                "index_code_knowledge、search_code_files、count_code_lines 等代码工具，直接用你的知识回答即可。]\n\n" + message;

        var ctx = buildRuntimeContext(sessionId, agentUserId);

        Flux<ServerSentEvent<String>> mainFlux = agent.streamEvents(new UserMessage(prefixedMessage), ctx)
                .flatMap(event -> {
                    if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                        return Mono.just(dataEvent(((TextBlockDeltaEvent) event).getDelta()));
                    } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                        return Mono.just(toolCallEvent(((ToolCallStartEvent) event).getToolCallName()));
                    }
                    return Mono.<ServerSentEvent<String>>empty();
                })
                .concatWith(Flux.just(doneEvent()));

        return executeStreamingSession(mainFlux, sessionId, userId,
                getTimeoutWithOffset(30), "聊天超时", "聊天",
                success -> operationRecordService.recordAsync(userId, "CHAT", null,
                        message, null, System.currentTimeMillis() - startTime, sessionId,
                        success ? "COMPLETED" : "FAILED",
                        TextUtils.estimateTokens(message)));
    }
}
