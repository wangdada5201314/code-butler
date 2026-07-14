package com.agent.codebutler.service;

import com.agent.codebutler.config.AgentScopeProperties;
import com.agent.codebutler.dto.CodeChatRequest;
import com.agent.codebutler.util.GitHubUtils;
import com.agent.codebutler.util.TextUtils;
import com.agent.codebutler.middleware.AgentTraceMiddleware;
import com.agent.codebutler.service.base.AbstractStreamingService;
import com.agent.codebutler.tools.MemoryTools;
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
 * 流式问答编排服务
 */
@Service
public class ChatService extends AbstractStreamingService {

    private final CodeScannerService codeScanner;
    private final GitService gitService;
    private final OperationRecordService operationRecordService;

    public ChatService(HarnessAgent agent,
                       CodeScannerService codeScanner,
                       GitService gitService,
                       OperationRecordService operationRecordService,
                       MemoryTools memoryTools,
                       AgentTraceMiddleware agentTraceMiddleware,
                       AgentScopeProperties agentScopeProperties) {
        super(agent, memoryTools, agentTraceMiddleware, agentScopeProperties);
        this.codeScanner = codeScanner;
        this.gitService = gitService;
        this.operationRecordService = operationRecordService;
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

        boolean isGitHub = GitHubUtils.isGitHubUrl(repoPath);

        // 本地路径校验（GitHub URL 跳过，由 MCP 工具远程读取）
        if (!isGitHub) {
            GitService.validateRepoPath(repoPath);
        }
        log.info("开始流式问答: sessionId={}, repoPath={}, isGitHub={}, userId={}", sessionId, repoPath, isGitHub, userId);

        long startTime = System.currentTimeMillis();
        String agentUserId = userId != null ? "chat-" + userId : "code-chat";
        boolean planMode = Boolean.TRUE.equals(request.getPlanMode());

        // 构建 prompt
        String prompt;
        try {
            prompt = buildPrompt(repoPath, question, isGitHub, planMode);
        } catch (Exception e) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("error").data("[ERROR] " + e.getMessage()).build(),
                    doneEvent());
        }
        var ctx = buildRuntimeContext(sessionId, agentUserId);

        Flux<ServerSentEvent<String>> mainFlux = agent.streamEvents(new UserMessage(prompt), ctx)
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
                getTimeoutWithOffset(30), "问答超时", "问答",
                success -> operationRecordService.recordAsync(userId, "CHAT", repoPath,
                        question, null, System.currentTimeMillis() - startTime, sessionId,
                        success ? "COMPLETED" : "FAILED",
                        TextUtils.estimateTokens(question)));
    }

    private String buildPrompt(String repoPath, String question, boolean isGitHub, boolean planMode) throws Exception {
        String planPrefix = planMode ? PLAN_MODE_PREFIX : "";

        if (isGitHub) {
            return planPrefix + String.format("""
                    你正在分析一个 GitHub 远程仓库：%s

                    请使用 MCP GitHub 工具来了解这个仓库：
                    1. 先用 list_directory 或 get_file_contents 查看仓库结构
                    2. 读取与用户问题相关的关键文件
                    3. 基于读取到的代码内容回答用户的问题

                    用户提问：%s

                    请基于仓库的实际代码内容回答问题，不要猜测。
                    """, repoPath, question);
        } else {
            String overview = codeScanner.getRepoOverview(repoPath);
            String gitStatus = gitService.getRepoStatus(repoPath);
            return planPrefix + String.format("""
                    以下是你正在分析的代码仓库信息：

                    %s

                    %s

                    用户提问：%s

                    请基于以上仓库上下文回答用户的问题。
                    """, overview, gitStatus, question);
        }
    }
}
