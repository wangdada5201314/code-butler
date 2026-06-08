package com.agent.codebutler.controller;

import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.dto.CodeChatRequest;
import com.agent.codebutler.dto.CodeReviewResult;
import com.agent.codebutler.service.CodeScannerService;
import com.agent.codebutler.service.GitService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/code")
@Validated
public class CodeButlerController {

    private static final Logger log = LoggerFactory.getLogger(CodeButlerController.class);
    private static final Set<String> VALID_DOC_TYPES = Set.of(
            "README", "CHANGELOG", "API", "ARCHITECTURE", "CONTRIBUTING");

    private final HarnessAgent agent;
    private final CodeScannerService codeScanner;
    private final GitService gitService;

    @Value("${agentscope.call-timeout-seconds:120}")
    private int agentCallTimeoutSeconds;

    public CodeButlerController(HarnessAgent agent,
                                CodeScannerService codeScanner,
                                GitService gitService) {
        this.agent = agent;
        this.codeScanner = codeScanner;
        this.gitService = gitService;
    }

    @GetMapping("/health")
    public ApiResponse<Object> health() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "agent", agent.getName()
        ));
    }

    @PostMapping("/review")
    public ApiResponse<CodeReviewResult> review(
            @RequestParam @NotBlank(message = "仓库路径不能为空") String repoPath,
            @RequestParam(defaultValue = "10") int maxFiles) {

        GitService.validateRepoPath(repoPath);
        String sessionId = "review-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("开始代码审查: sessionId={}, repoPath={}", sessionId, repoPath);

        try {
            String overview = codeScanner.getRepoOverview(repoPath);
            String gitStatus = gitService.getRepoStatus(repoPath);
            String gitChanges = gitService.getStagedDiff(repoPath);

            String prompt = String.format("""
                    请对这个代码仓库进行一次全面的代码审查。

                    %s

                    %s

                    %s

                    请从以下几个方面给出审查意见：
                    1. 代码质量和规范性
                    2. 潜在的 Bug 和安全漏洞
                    3. 性能优化建议
                    4. 架构改进建议
                    5. 文档完善度

                    请用中文回复，结构清晰，每个问题标注严重程度（🔴严重 🟡建议 🟢优化）。
                    """, overview, gitStatus, gitChanges);

            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId)
                    .userId("code-reviewer")
                    .build();

            String result = agent.call(new UserMessage(prompt), ctx)
                    .timeout(Duration.ofSeconds(agentCallTimeoutSeconds))
                    .block()
                    .getContent().stream()
                    .filter(TextBlock.class::isInstance)
                    .map(cb -> ((TextBlock) cb).getText())
                    .collect(Collectors.joining("\n"));

            log.info("代码审查完成: sessionId={}", sessionId);

            CodeReviewResult reviewResult = CodeReviewResult.builder()
                    .sessionId(sessionId)
                    .repoPath(repoPath)
                    .overview(overview)
                    .review(result)
                    .build();

            return ApiResponse.success(reviewResult);

        } catch (Exception e) {
            log.error("代码审查失败: sessionId={}, repoPath={}", sessionId, repoPath, e);
            CodeReviewResult errorResult = CodeReviewResult.builder()
                    .sessionId(sessionId)
                    .repoPath(repoPath)
                    .review("审查失败: " + e.getMessage())
                    .build();
            return ApiResponse.error(500, "代码审查失败: " + e.getMessage(), errorResult);
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody CodeChatRequest request) {

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
                                String safeDelta = ((TextBlockDeltaEvent) event).getDelta()
                                        .replace("\n", "\ndata: ");
                                return "data: " + safeDelta + "\n\n";
                            } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                                return "data: [调用工具: " +
                                        ((ToolCallStartEvent) event).getToolCallName() + "]\n\n";
                            }
                            return "";
                        })
                        .filter(s -> !s.isEmpty())
                        .concatWith(Flux.just("data: [DONE]\n\n"))
                        .onErrorResume(e -> {
                            log.error("流式问答异常: sessionId={}", sessionId, e);
                            return Flux.just(
                                    "data: [ERROR] " + sanitizeSseError(e.getMessage()) + "\n\n",
                                    "data: [DONE]\n\n");
                        })
                )
                .timeout(Duration.ofSeconds(agentCallTimeoutSeconds + 30),
                        Flux.just("data: [ERROR] 请求超时\n\n", "data: [DONE]\n\n"))
                .onErrorResume(e -> {
                    log.error("流式问答启动失败: repoPath={}", repoPath, e);
                    return Flux.just(
                            "data: [ERROR] " + sanitizeSseError(e.getMessage()) + "\n\n",
                            "data: [DONE]\n\n");
                });
    }

    @PostMapping("/docs")
    public ApiResponse<Object> generateDocs(
            @RequestParam @NotBlank(message = "仓库路径不能为空") String repoPath,
            @RequestParam(defaultValue = "README") String docType) {

        GitService.validateRepoPath(repoPath);

        if (!isValidDocType(docType)) {
            return ApiResponse.error(400, "不支持的文档类型: " + docType
                    + "，可选: " + String.join(", ", VALID_DOC_TYPES));
        }

        String sessionId = "docs-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("开始文档生成: sessionId={}, repoPath={}, docType={}", sessionId, repoPath, docType);

        try {
            String overview = codeScanner.getRepoOverview(repoPath);

            String prompt = String.format("""
                    请为以下代码仓库生成 %s 文档。

                    %s

                    要求：
                    - 语言：中文
                    - 格式：标准 Markdown
                    - 内容完整、结构清晰
                    - 如果是 README：包含项目简介、技术栈、快速开始、目录结构、API 说明
                    - 如果是其他文档类型：按照该类型的标准格式生成
                    """, docType, overview);

            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId)
                    .userId("doc-generator")
                    .build();

            String result = agent.call(new UserMessage(prompt), ctx)
                    .timeout(Duration.ofSeconds(agentCallTimeoutSeconds))
                    .block()
                    .getContent().stream()
                    .filter(TextBlock.class::isInstance)
                    .map(cb -> ((TextBlock) cb).getText())
                    .collect(Collectors.joining("\n"));

            log.info("文档生成完成: sessionId={}", sessionId);

            return ApiResponse.success(Map.of(
                    "sessionId", sessionId,
                    "docType", docType,
                    "document", result
            ));

        } catch (Exception e) {
            log.error("文档生成失败: sessionId={}, repoPath={}", sessionId, repoPath, e);
            return ApiResponse.error(500, "文档生成失败: " + e.getMessage());
        }
    }

    private boolean isValidDocType(String docType) {
        return docType != null && VALID_DOC_TYPES.contains(docType.toUpperCase());
    }

    private String sanitizeSseError(String msg) {
        if (msg == null) return "未知错误";
        return msg.replace("\n", " ").replace("\r", "");
    }
}
