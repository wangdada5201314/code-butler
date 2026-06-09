package com.agent.codebutler.service;

import com.agent.codebutler.dto.CodeChatRequest;
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
 * 流式问答编排服务
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final HarnessAgent agent;
    private final CodeScannerService codeScanner;
    private final GitService gitService;

    @Value("${agentscope.call-timeout-seconds:120}")
    private int agentCallTimeoutSeconds;

    public ChatService(HarnessAgent agent,
                       CodeScannerService codeScanner,
                       GitService gitService) {
        this.agent = agent;
        this.codeScanner = codeScanner;
        this.gitService = gitService;
    }

    /**
     * 执行流式问答，返回标准 SSE 事件流
     */
    public Flux<ServerSentEvent<String>> streamChat(CodeChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "chat-" + UUID.randomUUID().toString().substring(0, 8);

        String repoPath = request.getRepoPath();
        String question = request.getQuestion();

        GitService.validateRepoPath(repoPath);
        log.info("开始流式问答: sessionId={}, repoPath={}", sessionId, repoPath);

        return Mono.fromCallable(() -> {
                    String overview = codeScanner.getRepoOverview(repoPath);
                    String gitStatus = gitService.getRepoStatus(repoPath);

                    String prompt = String.format("""
                        以下是你正在分析的代码仓库信息：

                        %s

                        %s

                        用户提问：%s

                        请基于以上仓库上下文回答用户的问题。
                        """, overview, gitStatus, question);

                    RuntimeContext ctx = RuntimeContext.builder()
                            .sessionId(sessionId)
                            .userId("code-chat")
                            .build();

                    return agent.streamEvents(new UserMessage(prompt), ctx);
                })
                .flatMapMany(flux -> flux
                        .map(event -> {
                            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                                String delta = ((TextBlockDeltaEvent) event).getDelta();
                                return ServerSentEvent.<String>builder()
                                        .data(delta)
                                        .build();
                            } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                                String toolName = ((ToolCallStartEvent) event).getToolCallName();
                                return ServerSentEvent.<String>builder()
                                        .event("tool")
                                        .data("[调用工具: " + toolName + "]")
                                        .build();
                            }
                            return null;
                        })
                        .filter(e -> e != null)
                        .concatWith(Flux.just(
                                ServerSentEvent.<String>builder()
                                        .event("done")
                                        .data("[DONE]")
                                        .build()))
                        .onErrorResume(e -> {
                            log.error("流式问答异常: sessionId={}", sessionId, e);
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
                    log.error("流式问答启动失败: repoPath={}", request.getRepoPath(), e);
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
