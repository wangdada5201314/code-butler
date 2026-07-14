package com.agent.codebutler.service;

import com.agent.codebutler.config.AgentScopeProperties;
import com.agent.codebutler.dto.CodeReviewResult;
import com.agent.codebutler.middleware.AgentTraceMiddleware;
import com.agent.codebutler.service.base.AbstractStreamingService;
import com.agent.codebutler.tools.MemoryTools;
import com.agent.codebutler.util.GitHubUtils;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 代码审查编排服务
 * <p>
 * 支持本地仓库路径和 GitHub URL 两种输入方式。
 * GitHub 仓库通过 MCP 工具自动读取远程文件。
 */
@Service
public class CodeReviewService extends AbstractStreamingService {

    private final CodeScannerService codeScanner;
    private final GitService gitService;
    private final OperationRecordService operationRecordService;
    private final UserPreferenceService userPreferenceService;

    @Value("${github.token:}")
    private String githubToken;

    public CodeReviewService(HarnessAgent agent,
                             CodeScannerService codeScanner,
                             GitService gitService,
                             OperationRecordService operationRecordService,
                             UserPreferenceService userPreferenceService,
                             MemoryTools memoryTools,
                             AgentTraceMiddleware agentTraceMiddleware,
                             AgentScopeProperties agentScopeProperties) {
        super(agent, memoryTools, agentTraceMiddleware, agentScopeProperties);
        this.codeScanner = codeScanner;
        this.gitService = gitService;
        this.operationRecordService = operationRecordService;
        this.userPreferenceService = userPreferenceService;
    }

    // ════════════════════════════════════════════════════════
    //  同步审查入口
    // ════════════════════════════════════════════════════════

    /**
     * 对指定仓库执行代码审查（支持本地路径和 GitHub URL）
     */
    public CodeReviewResult review(String repoPath, Long userId) throws Exception {
        if (GitHubUtils.isGitHubUrl(repoPath)) {
            return reviewGitHub(repoPath.trim(), userId);
        }
        return reviewLocal(repoPath, userId);
    }

    // ════════════════════════════════════════════════════════
    //  流式审查
    // ════════════════════════════════════════════════════════

    /**
     * 流式代码审查 — 返回 SSE 事件流，实时推送审查进度和内容
     */
    public Flux<ServerSentEvent<String>> streamReview(String repoPath, Long userId) {
        String sessionId = "review-" + UUID.randomUUID().toString().substring(0, 8);
        boolean isGitHub = GitHubUtils.isGitHubUrl(repoPath);
        String agentUserId = userId != null ? "review-" + userId : "code-reviewer";

        log.info("开始流式代码审查: sessionId={}, repoPath={}, isGitHub={}", sessionId, repoPath, isGitHub);

        long startTime = System.currentTimeMillis();

        String prompt;
        try {
            prompt = buildReviewPrompt(repoPath, isGitHub, userId);
        } catch (Exception e) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("error").data("[ERROR] " + e.getMessage()).build(),
                    doneEvent());
        }

        var ctx = buildRuntimeContext(sessionId, agentUserId);

        // 文本累积器（用于流结束后解析 issues）
        StringBuilder textAccumulator = new StringBuilder();

        Flux<ServerSentEvent<String>> mainFlux = agent.streamEvents(new UserMessage(prompt), ctx)
                .flatMap(event -> {
                    if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                        String delta = ((TextBlockDeltaEvent) event).getDelta();
                        textAccumulator.append(delta);
                        return Mono.just(dataEvent(delta));
                    } else if (event.getType() == AgentEventType.TOOL_CALL_START) {
                        return Mono.just(toolCallEvent(((ToolCallStartEvent) event).getToolCallName()));
                    }
                    return Mono.<ServerSentEvent<String>>empty();
                })
                // 流结束后：解析结构化 issues 并通过 summary 事件发送
                .concatWith(Mono.fromCallable(() -> {
                    List<CodeReviewResult.CodeIssue> issues = parseIssues(textAccumulator.toString());
                    String issuesJson = OBJECT_MAPPER.writeValueAsString(issues);
                    return ServerSentEvent.<String>builder()
                            .event("summary")
                            .data(issuesJson)
                            .build();
                }).flux())
                .concatWith(Flux.just(doneEvent()));

        return executeStreamingSession(mainFlux, sessionId, userId,
                getTimeoutWithOffset(60), "审查超时", "代码审查",
                success -> operationRecordService.recordAsync(userId, "REVIEW", repoPath,
                        null, null, System.currentTimeMillis() - startTime, sessionId,
                        success ? "COMPLETED" : "FAILED", 0));
    }

    // ════════════════════════════════════════════════════════
    //  Prompt 构建
    // ════════════════════════════════════════════════════════

    private String buildReviewPrompt(String repoPath, boolean isGitHub, Long userId) throws Exception {
        if (isGitHub) {
            String[] parsed = GitHubUtils.parseGitHubUrl(repoPath);
            if (parsed == null) throw new IllegalArgumentException("无效的 GitHub URL: " + repoPath);
            String repoDesc = GitHubUtils.formatGitHubRepo(parsed[0], parsed[1]);
            return String.format("""
                    请对 GitHub 仓库 %s 进行一次全面的代码审查。

                    你可以使用以下工具来探索这个仓库：
                    - MCP 工具：列出目录、读取文件、查看提交和 PR
                    - 代码分析工具：search_code_files、count_code_lines、calculate_complexity、detect_code_smells

                    %s

                    请用中文回复，每个问题标注严重程度（🔴严重 🟡建议 🟢优化）。
                    """, repoDesc, userPreferenceService.buildPreferencePrompt(userId));
        }

        GitService.validateRepoPath(repoPath);
        String overview = codeScanner.getRepoOverview(repoPath);
        String gitStatus = gitService.getRepoStatus(repoPath);
        String gitChanges = gitService.getStagedDiff(repoPath);
        return String.format("""
                请对这个代码仓库进行一次全面的代码审查。

                你可以使用代码分析工具（search_code_files、count_code_lines、calculate_complexity、detect_code_smells）深入分析代码质量。

                %s

                %s

                %s

                %s

                请用中文回复，每个问题标注严重程度（🔴严重 🟡建议 🟢优化）。
                """, overview, gitStatus, gitChanges,
                userPreferenceService.buildPreferencePrompt(userId));
    }

    // ════════════════════════════════════════════════════════
    //  同步审查（本地 / GitHub）
    // ════════════════════════════════════════════════════════

    private CodeReviewResult reviewGitHub(String githubUrl, Long userId) throws Exception {
        String[] parsed = GitHubUtils.parseGitHubUrl(githubUrl);
        if (parsed == null) {
            throw new IllegalArgumentException("无效的 GitHub URL: " + githubUrl);
        }

        if (!GitHubUtils.hasGitHubToken(githubToken)) {
            throw new IllegalStateException(
                    "GitHub Token 未配置，请在 application-local.yml 中设置 github.token 或设置环境变量 GITHUB_TOKEN");
        }

        String owner = parsed[0];
        String repo = parsed[1];
        String repoDesc = GitHubUtils.formatGitHubRepo(owner, repo);
        String sessionId = "review-gh-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("开始 GitHub 代码审查: sessionId={}, repo={}", sessionId, repoDesc);
        long startTime = System.currentTimeMillis();

        String prompt = String.format("""
                请对 GitHub 仓库 %s 进行一次全面的代码审查。

                你可以使用以下 MCP 工具来探索这个仓库：
                - 列出仓库根目录和子目录的文件
                - 读取文件内容（README、主要源码文件、配置文件等）
                - 查看最近的提交记录和分支信息
                - 查看 Pull Request 和 Issue

                审查步骤：
                1. 先查看仓库的 README 和项目结构，了解项目概况
                2. 浏览主要源代码目录，了解技术栈和架构
                3. 深入审查关键源代码文件（至少 5-8 个重要文件）
                4. 查看最近的提交记录，了解开发活跃度

                %s

                请从以下几个方面给出审查意见：
                1. 代码质量和规范性
                2. 潜在的 Bug 和安全漏洞
                3. 性能优化建议
                4. 架构改进建议
                5. 文档完善度

                请用中文回复，结构清晰，每个问题标注严重程度（🔴严重 🟡建议 🟢优化）。
                """, repoDesc, userPreferenceService.buildPreferencePrompt(userId));

        String agentUserId = userId != null ? "review-" + userId : "code-reviewer";
        var ctx = buildRuntimeContext(sessionId, agentUserId);

        if (userId != null) memoryTools.setUserId(userId);
        String result;
        try {
            result = extractText(agent.call(new UserMessage(prompt), ctx)
                    .timeout(Duration.ofSeconds(agentScopeProperties.getCallTimeoutSeconds()))
                    .block());
        } finally {
            if (userId != null) memoryTools.clearUserId();
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("GitHub 代码审查完成: sessionId={}, repo={}, duration={}ms", sessionId, repoDesc, durationMs);

        String overview = String.format("GitHub 仓库: %s\n审查方式: MCP 远程读取", repoDesc);

        operationRecordService.recordAsync(userId, "REVIEW", githubUrl,
                null, result, durationMs, sessionId, "COMPLETED",
                UsageService.estimateTokens(result));

        return CodeReviewResult.builder()
                .sessionId(sessionId)
                .repoPath(githubUrl)
                .overview(overview)
                .review(result)
                .issues(parseIssues(result))
                .build();
    }

    private CodeReviewResult reviewLocal(String repoPath, Long userId) throws Exception {
        GitService.validateRepoPath(repoPath);

        String sessionId = "review-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("开始本地代码审查: sessionId={}, repoPath={}, userId={}", sessionId, repoPath, userId);

        long startTime = System.currentTimeMillis();
        String overview = codeScanner.getRepoOverview(repoPath);
        String gitStatus = gitService.getRepoStatus(repoPath);
        String gitChanges = gitService.getStagedDiff(repoPath);

        String prompt = String.format("""
                请对这个代码仓库进行一次全面的代码审查。

                %s

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
                """, overview, gitStatus, gitChanges,
                userPreferenceService.buildPreferencePrompt(userId));

        String agentUserId = userId != null ? "review-" + userId : "code-reviewer";
        var ctx = buildRuntimeContext(sessionId, agentUserId);

        if (userId != null) memoryTools.setUserId(userId);
        String result;
        try {
            result = extractText(agent.call(new UserMessage(prompt), ctx)
                    .timeout(Duration.ofSeconds(agentScopeProperties.getCallTimeoutSeconds()))
                    .block());
        } finally {
            if (userId != null) memoryTools.clearUserId();
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("本地代码审查完成: sessionId={}, duration={}ms", sessionId, durationMs);

        operationRecordService.recordAsync(userId, "REVIEW", repoPath,
                null, result, durationMs, sessionId, "COMPLETED",
                UsageService.estimateTokens(result));

        return CodeReviewResult.builder()
                .sessionId(sessionId)
                .repoPath(repoPath)
                .overview(overview)
                .review(result)
                .issues(parseIssues(result))
                .build();
    }

    // ════════════════════════════════════════════════════════
    //  解析工具
    // ════════════════════════════════════════════════════════

    /**
     * 从 Agent 返回的自由文本中解析结构化代码问题
     */
    static List<CodeReviewResult.CodeIssue> parseIssues(String reviewText) {
        List<CodeReviewResult.CodeIssue> issues = new ArrayList<>();
        if (reviewText == null || reviewText.isBlank()) return issues;

        Pattern severityPattern = Pattern.compile(
                "^\\s*[-*]?\\s*(🔴|🟡|🟢)\\s*(?:[严重|建议|优化|critical|warning|info]*\\s*)[:\\-—]?\\s*(.*)",
                Pattern.MULTILINE);
        Pattern filePathPattern = Pattern.compile("([\\w./\\\\-]+\\.[a-zA-Z]{1,5})(?::(\\d+))?");

        Matcher matcher = severityPattern.matcher(reviewText);

        while (matcher.find() && issues.size() < 50) {
            String emoji = matcher.group(1);
            String description = matcher.group(2).trim();

            String severity = switch (emoji) {
                case "🔴" -> "critical";
                case "🟡" -> "warning";
                case "🟢" -> "info";
                default -> "info";
            };

            String fileName = null;
            Integer line = null;
            Matcher fileMatcher = filePathPattern.matcher(description);
            if (fileMatcher.find()) {
                fileName = fileMatcher.group(1);
                if (fileMatcher.group(2) != null) {
                    try { line = Integer.parseInt(fileMatcher.group(2)); } catch (NumberFormatException ignored) {}
                }
            }

            if (description.isBlank()) {
                int lineEnd = matcher.end();
                int nextNewline = reviewText.indexOf('\n', lineEnd);
                if (nextNewline >= 0 && nextNewline + 1 < reviewText.length()) {
                    int afterNext = reviewText.indexOf('\n', nextNewline + 1);
                    String nextLine = afterNext >= 0
                            ? reviewText.substring(nextNewline + 1, afterNext).trim()
                            : reviewText.substring(nextNewline + 1).trim();
                    description = nextLine;
                }
            }

            description = description.replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                    .replaceAll("`([^`]+)`", "$1")
                    .trim();

            if (!description.isEmpty()) {
                issues.add(CodeReviewResult.CodeIssue.builder()
                        .severity(severity)
                        .fileName(fileName)
                        .line(line)
                        .message(description.length() > 300 ? description.substring(0, 300) + "..." : description)
                        .build());
            }
        }

        return issues;
    }

    /**
     * 从 Agent 返回的 Msg 中安全提取文本内容
     */
    static String extractText(Msg msg) {
        if (msg == null) {
            return "[Agent 未返回内容]";
        }
        List<?> content = msg.getContent();
        if (content == null || content.isEmpty()) {
            return "[Agent 返回内容为空]";
        }

        String text = content.stream()
                .filter(TextBlock.class::isInstance)
                .map(cb -> ((TextBlock) cb).getText())
                .collect(Collectors.joining("\n"));

        if (text.isBlank()) {
            text = content.stream()
                    .map(block -> {
                        if (block instanceof TextBlock tb) {
                            return tb.getText();
                        } else {
                            return block.toString();
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }

        return text.isBlank() ? "[Agent 未返回文本内容，可能工具调用失败]" : text;
    }
}
