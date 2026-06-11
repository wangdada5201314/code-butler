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
     * 对指定仓库执行代码审查
     *
     * @param repoPath 仓库路径
     * @param userId   当前登录用户 ID（可为 null）
     */
    public CodeReviewResult review(String repoPath, Long userId) throws Exception {
        GitService.validateRepoPath(repoPath);

        String sessionId = "review-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("开始代码审查: sessionId={}, repoPath={}, userId={}", sessionId, repoPath, userId);

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

        // 使用实际用户 ID 绑定 Agent 记忆，让 AI 逐步了解用户的审查偏好
        String agentUserId = userId != null ? "review-" + userId : "code-reviewer";
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(agentUserId)
                .build();

        String result = extractText(agent.call(new UserMessage(prompt), ctx)
                .timeout(Duration.ofSeconds(agentCallTimeoutSeconds))
                .block());

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("代码审查完成: sessionId={}, duration={}ms", sessionId, durationMs);

        // 异步记录操作历史
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
     */
    static String extractText(Msg msg) {
        if (msg == null) {
            return "[Agent 未返回内容]";
        }
        List<?> content = msg.getContent();
        if (content == null || content.isEmpty()) {
            return "[Agent 返回内容为空]";
        }
        return content.stream()
                .filter(TextBlock.class::isInstance)
                .map(cb -> ((TextBlock) cb).getText())
                .collect(Collectors.joining("\n"));
    }
}
