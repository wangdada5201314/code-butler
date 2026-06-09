package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.dto.CodeChatRequest;
import com.agent.codebutler.dto.CodeReviewResult;
import com.agent.codebutler.dto.DocGenerateResult;
import com.agent.codebutler.service.ChatService;
import com.agent.codebutler.service.CodeReviewService;
import com.agent.codebutler.service.DocGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/code")
@Validated
@Tag(name = "代码管家", description = "代码审查、问答、文档生成接口")
public class CodeButlerController {

    private static final Logger log = LoggerFactory.getLogger(CodeButlerController.class);

    private final CodeReviewService codeReviewService;
    private final DocGenerationService docGenerationService;
    private final ChatService chatService;

    public CodeButlerController(CodeReviewService codeReviewService,
                                DocGenerationService docGenerationService,
                                ChatService chatService) {
        this.codeReviewService = codeReviewService;
        this.docGenerationService = docGenerationService;
        this.chatService = chatService;
    }

    @PostMapping("/review")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "代码审查", description = "对指定仓库路径进行全面的代码审查")
    public ApiResponse<CodeReviewResult> review(
            @RequestParam @NotBlank(message = "仓库路径不能为空")
            @Parameter(description = "仓库本地路径") String repoPath) {
        try {
            CodeReviewResult result = codeReviewService.review(repoPath);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("代码审查失败: repoPath={}", repoPath, e);
            return ApiResponse.error(500, "代码审查失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = "user")
    @Operation(summary = "流式问答", description = "基于 SSE 的流式代码问答，实时返回 AI 分析结果")
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody CodeChatRequest request) {
        return chatService.streamChat(request);
    }

    @PostMapping("/docs")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "生成文档", description = "为指定仓库生成文档（README / CHANGELOG / API 等）")
    public ApiResponse<DocGenerateResult> generateDocs(
            @RequestParam @NotBlank(message = "仓库路径不能为空")
            @Parameter(description = "仓库本地路径") String repoPath,
            @RequestParam(defaultValue = "README")
            @Parameter(description = "文档类型", example = "README") String docType) {

        if (!docGenerationService.isValidDocType(docType)) {
            return ApiResponse.error(400, "不支持的文档类型: " + docType
                    + "，可选: " + String.join(", ", docGenerationService.getValidDocTypes()));
        }

        try {
            DocGenerateResult result = docGenerationService.generate(repoPath, docType);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("文档生成失败: repoPath={}, docType={}", repoPath, docType, e);
            return ApiResponse.error(500, "文档生成失败: " + e.getMessage());
        }
    }
}
