package com.agent.codebutler.service;

import com.agent.codebutler.dto.CodeReviewResult;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 代码审查编排服务
 * <p>
 * 支持本地仓库路径和 GitHub URL 两种输入方式。
 * GitHub 仓库通过 MCP 工具自动读取远程文件。
 */
@Service
public class CodeReviewService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);

    private final HarnessAgent agent;
    private final CodeScannerService codeScanner;
    private final GitService gitService;
    private final OperationRecordService operationRecordService;
    private final UserPreferenceService userPreferenceService;

    @Value("${agentscope.call-timeout-seconds:120}")
    private int agentCallTimeoutSeconds;

    @Value("${github.token:}")
    private String githubToken;

    public CodeReviewService(HarnessAgent agent,
                             CodeScannerService codeScanner,
                             GitService gitService,
                             OperationRecordService operationRecordService,
                             UserPreferenceService userPreferenceService) {
        this.agent = agent;
        this.codeScanner = codeScanner;
        this.gitService = gitService;
        this.operationRecordService = operationRecordService;
        this.userPreferenceService = userPreferenceService;
    }

    /**
     * 对指定仓库执行代码审查（支持本地路径和 GitHub URL）
     *
     * @param repoPath 仓库路径或 GitHub URL
     * @param userId   当前登录用户 ID（可为 null）
     */
    public CodeReviewResult review(String repoPath, Long userId) throws Exception {
        // 判断是否为 GitHub URL
        if (GitHubService.isGitHubUrl(repoPath)) {
            return reviewGitHub(repoPath.trim(), userId);
        }

        // 本地仓库审查（原有逻辑）
        return reviewLocal(repoPath, userId);
    }

    /**
     * GitHub 仓库审查 — 通过 MCP 工具读取远程文件
     */
    private CodeReviewResult reviewGitHub(String githubUrl, Long userId) throws Exception {
        String[] parsed = GitHubService.parseGitHubUrl(githubUrl);
        if (parsed == null) {
            throw new IllegalArgumentException("无效的 GitHub URL: " + githubUrl);
        }

        if (!GitHubService.hasGitHubToken(githubToken)) {
            throw new IllegalStateException(
                    "GitHub Token 未配置，请在 application-local.yml 中设置 github.token 或设置环境变量 GITHUB_TOKEN");
        }

        String owner = parsed[0];
        String repo = parsed[1];
        String repoDesc = GitHubService.formatGitHubRepo(owner, repo);
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
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(agentUserId)
                .build();

        String result = extractText(agent.call(new UserMessage(prompt), ctx)
                .timeout(Duration.ofSeconds(agentCallTimeoutSeconds))
                .block());

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("GitHub 代码审查完成: sessionId={}, repo={}, duration={}ms", sessionId, repoDesc, durationMs);

        // 构建概述信息
        String overview = String.format("GitHub 仓库: %s\n审查方式: MCP 远程读取", repoDesc);

        operationRecordService.recordAsync(userId, "REVIEW", githubUrl,
                null, result, durationMs, sessionId, "COMPLETED",
                UsageService.estimateTokens(result));

        return CodeReviewResult.builder()
                .sessionId(sessionId)
                .repoPath(githubUrl)
                .overview(overview)
                .review(result)
                .build();
    }

    /**
     * 本地仓库审查（原有逻辑）
     */
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
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(agentUserId)
                .build();

        String result = extractText(agent.call(new UserMessage(prompt), ctx)
                .timeout(Duration.ofSeconds(agentCallTimeoutSeconds))
                .block());

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
                .build();
    }

    /**
     * 从 Agent 返回的 Msg 中安全提取文本内容
     * <p>
     * 当 Agent 使用 MCP 工具时，返回的消息可能包含多种内容块类型：
     * TextBlock（文本）、ToolUseBlock（工具调用）、ToolResultBlock（工具结果）。
     * 此方法提取所有文本内容，跳过工具块。
     */
    static String extractText(Msg msg) {
        if (msg == null) {
            return "[Agent 未返回内容]";
        }
        List<?> content = msg.getContent();
        if (content == null || content.isEmpty()) {
            return "[Agent 返回内容为空]";
        }

        // 尝试提取 TextBlock
        String text = content.stream()
                .filter(TextBlock.class::isInstance)
                .map(cb -> ((TextBlock) cb).getText())
                .collect(Collectors.joining("\n"));

        // 如果没有 TextBlock，尝试从其他块类型提取
        if (text.isBlank()) {
            text = content.stream()
                    .map(block -> {
                        if (block instanceof TextBlock tb) {
                            return tb.getText();
                        } else {
                            // 对于 ToolUseBlock/ToolResultBlock 等，尝试 toString
                            return block.toString();
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }

        return text.isBlank() ? "[Agent 未返回文本内容，可能工具调用失败]" : text;
    }
}
