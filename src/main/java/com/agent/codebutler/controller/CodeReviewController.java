package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.annotation.QuotaCheck;
import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.dto.CodeReviewResult;
import com.agent.codebutler.dto.CodeReviewStreamRequest;
import com.agent.codebutler.dto.DocGenerateResult;
import com.agent.codebutler.service.CodeReviewService;
import com.agent.codebutler.service.DocGenerationService;
import com.agent.codebutler.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 代码审查 + 文档生成 Controller
 */
@RestController
@RequestMapping("/api/code")
@Validated
@Tag(name = "代码审查", description = "代码审查与文档生成接口")
public class CodeReviewController {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewController.class);

    private final CodeReviewService codeReviewService;
    private final DocGenerationService docGenerationService;

    public CodeReviewController(CodeReviewService codeReviewService,
                                DocGenerationService docGenerationService) {
        this.codeReviewService = codeReviewService;
        this.docGenerationService = docGenerationService;
    }

    @PostMapping("/review")
    @AuthCheck(mustRole = "user")
    @QuotaCheck(opType = "REVIEW")
    @Operation(summary = "代码审查（同步）", description = "对指定仓库进行全面的代码审查，支持本地路径和 GitHub URL")
    public ApiResponse<CodeReviewResult> review(
            @RequestParam @NotBlank(message = "仓库路径不能为空")
            @Parameter(description = "仓库本地路径或 GitHub URL") String repoPath,
            HttpServletRequest request) throws Exception {
        Long userId = UserContext.getUserId(request);
        CodeReviewResult result = codeReviewService.review(repoPath, userId);
        return ApiResponse.success(result);
    }

    @PostMapping(value = "/review/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = "user")
    @QuotaCheck(opType = "REVIEW")
    @Operation(summary = "代码审查（流式）", description = "基于 SSE 的流式代码审查，实时返回审查进度、工具调用和 AI 分析结果")
    public Flux<ServerSentEvent<String>> reviewStream(@Valid @RequestBody CodeReviewStreamRequest body,
                                                      HttpServletRequest request) {
        Long userId = UserContext.getUserId(request);
        return codeReviewService.streamReview(body.getRepoPath(), userId);
    }

    @PostMapping("/docs")
    @AuthCheck(mustRole = "user")
    @QuotaCheck(opType = "DOC")
    @Operation(summary = "生成文档", description = "为指定仓库生成文档（README / CHANGELOG / API 等）")
    public ApiResponse<DocGenerateResult> generateDocs(
            @RequestParam @NotBlank(message = "仓库路径不能为空")
            @Parameter(description = "仓库本地路径") String repoPath,
            @RequestParam(defaultValue = "README")
            @Parameter(description = "文档类型", example = "README") String docType,
            HttpServletRequest request) throws Exception {

        if (!docGenerationService.isValidDocType(docType)) {
            return ApiResponse.error(400, "不支持的文档类型: " + docType
                    + "，可选: " + String.join(", ", docGenerationService.getValidDocTypes()));
        }

        Long userId = UserContext.getUserId(request);
        DocGenerateResult result = docGenerationService.generate(repoPath, docType, userId);
        return ApiResponse.success(result);
    }
}
