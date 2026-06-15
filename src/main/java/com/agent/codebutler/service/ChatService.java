package com.agent.codebutler.service;

import com.agent.codebutler.dto.CodeChatRequest;
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
 * 流式问答编排服务
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HarnessAgent agent;
    private final CodeScannerService codeScanner;
    private final GitService gitService;
    private final OperationRecordService operationRecordService;
    private final MemoryTools memoryTools;
    private final AgentTraceMiddleware agentTraceMiddleware;

    @Value("${agentscope.call-timeout-seconds:120}")
    private int agentCallTimeoutSeconds;

    public ChatService(HarnessAgent agent,
                       CodeScannerService codeScanner,
                       GitService gitService,
                       OperationRecordService operationRecordService,
                       MemoryTools memoryTools,
                       AgentTraceMiddleware agentTraceMiddleware) {
        this.agent = agent;
        this.codeScanner = codeScanner;
        this.gitService = gitService;
        this.operationRecordService = operationRecordService;
        this.memoryTools = memoryTools;
        this.agentTraceMiddleware = agentTraceMiddleware;
    }

    /**
     * 执行流式问答，返回标准 SSE 事件流
     *
     * @param request 问答请求
     * @param userId  当前登录用户 ID（可为 null）
     */
    public Flux<ServerSentEvent<String>> streamChat(CodeChatRequest request, Long userId) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "chat-" + UUID.randomUUID().toString().substring(0, 8);

        String repoPath = request.getRepoPath();
        String question = request.getQuestion();

        boolean isGitHub = GitHubService.isGitHubUrl(repoPath);

        // 本地路径校验（GitHub URL 跳过，由 MCP 工具远程读取）
        if (!isGitHub) {
            GitService.validateRepoPath(repoPath);
        }
        log.info("开始流式问答: sessionId={}, repoPath={}, isGitHub={}, userId={}", sessionId, repoPath, isGitHub, userId);

        // 记录操作历史（流式场景在开始时记录，状态标记为 COMPLETED）
        operationRecordService.recordAsync(userId, "CHAT", repoPath,
                question, null, 0, sessionId, "COMPLETED",
                UsageService.estimateTokens(question));

        // 使用实际用户 ID 绑定 Agent 记忆，让 AI 逐步了解用户的提问风格和关注点
        String agentUserId = userId != null ? "chat-" + userId : "code-chat";

        boolean planMode = Boolean.TRUE.equals(request.getPlanMode());

        return Mono.fromCallable(() -> {
                    // 在 boundedElastic 线程上设置 ThreadLocal，确保工具调用时能读到
                    if (userId != null) {
                        memoryTools.setUserId(userId);
                    }

                    // Trace 事件流：通过 Sink 桥接中间件的回调到 SSE 输出
                    Sinks.Many<AgentTraceEvent> traceSink = Sinks.many().multicast().onBackpressureBuffer();
                    agentTraceMiddleware.setTraceConsumer(event -> traceSink.tryEmitNext(event));

                    String prompt;

                    // Plan Mode 前缀指令
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

                    if (isGitHub) {
                        // GitHub 仓库：提示 Agent 使用 MCP 工具读取远程文件
                        prompt = planPrefix + String.format("""
                            你正在分析一个 GitHub 远程仓库：%s

                            请使用 MCP GitHub 工具来了解这个仓库：
                            1. 先用 list_directory 或 get_file_contents 查看仓库结构
                            2. 读取与用户问题相关的关键文件
                            3. 基于读取到的代码内容回答用户的问题

                            用户提问：%s

                            请基于仓库的实际代码内容回答问题，不要猜测。
                            """, repoPath, question);
                    } else {
                        // 本地仓库：使用本地扫描 + git 状态
                        String overview = codeScanner.getRepoOverview(repoPath);
                        String gitStatus = gitService.getRepoStatus(repoPath);

                        prompt = planPrefix + String.format("""
                            以下是你正在分析的代码仓库信息：

                            %s

                            %s

                            用户提问：%s

                            请基于以上仓库上下文回答用户的问题。
                            """, overview, gitStatus, question);
                    }

                    RuntimeContext ctx = RuntimeContext.builder()
                            .sessionId(sessionId)
                            .userId(agentUserId)
                            .build();

                    Flux<ServerSentEvent<String>> traceFlux = traceSink.asFlux()
                            .map(event -> {
                                try {
                                    String json = OBJECT_MAPPER.writeValueAsString(event);
                                    return ServerSentEvent.<String>builder().event("trace").data(json).build();
                                } catch (Exception e) {
                                    return ServerSentEvent.<String>builder().build();
                                }
                            });

                    Flux<ServerSentEvent<String>> mainFlux = agent.streamEvents(new UserMessage(prompt), ctx)
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
                                // 跳过其他事件类型（思考、完成等），不发出任何元素
                                return Mono.<ServerSentEvent<String>>empty();
                            })
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
