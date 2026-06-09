package com.agent.codebutler.service;

import com.agent.codebutler.dto.DocGenerateResult;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 文档生成编排服务
 */
@Service
public class DocGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DocGenerationService.class);
    private static final Set<String> VALID_DOC_TYPES = Set.of(
            "README", "CHANGELOG", "API", "ARCHITECTURE", "CONTRIBUTING");

    private final HarnessAgent agent;
    private final CodeScannerService codeScanner;

    @Value("${agentscope.call-timeout-seconds:120}")
    private int agentCallTimeoutSeconds;

    public DocGenerationService(HarnessAgent agent, CodeScannerService codeScanner) {
        this.agent = agent;
        this.codeScanner = codeScanner;
    }

    public boolean isValidDocType(String docType) {
        return docType != null && VALID_DOC_TYPES.contains(docType.toUpperCase());
    }

    public Set<String> getValidDocTypes() {
        return VALID_DOC_TYPES;
    }

    /**
     * 为指定仓库生成文档
     */
    public DocGenerateResult generate(String repoPath, String docType) throws Exception {
        GitService.validateRepoPath(repoPath);

        String sessionId = "docs-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("开始文档生成: sessionId={}, repoPath={}, docType={}", sessionId, repoPath, docType);

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

        String result = CodeReviewService.extractText(
                agent.call(new UserMessage(prompt), ctx)
                        .timeout(Duration.ofSeconds(agentCallTimeoutSeconds))
                        .block());

        log.info("文档生成完成: sessionId={}", sessionId);

        return DocGenerateResult.builder()
                .sessionId(sessionId)
                .docType(docType)
                .document(result)
                .build();
    }
}
