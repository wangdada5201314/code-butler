package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.service.CodeKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 代码知识库 Controller
 */
@RestController
@RequestMapping("/api/code")
@Validated
@Tag(name = "代码知识库", description = "RAG 代码索引与状态查询接口")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final CodeKnowledgeService codeKnowledgeService;

    public KnowledgeController(CodeKnowledgeService codeKnowledgeService) {
        this.codeKnowledgeService = codeKnowledgeService;
    }

    @PostMapping("/knowledge/index")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "索引代码仓库", description = "将仓库代码分块并向量化存入知识库，用于 RAG 语义检索")
    public ApiResponse<CodeKnowledgeService.IndexResult> indexRepository(
            @RequestParam @NotBlank(message = "仓库路径不能为空")
            @Parameter(description = "仓库本地路径") String repoPath) {
        if (repoPath.startsWith("http://") || repoPath.startsWith("https://")) {
            return ApiResponse.error(400, "代码知识库索引仅支持本地仓库路径，不支持 GitHub URL。请先将仓库 clone 到本地后再索引。");
        }
        CodeKnowledgeService.IndexResult result = codeKnowledgeService.indexRepository(repoPath);
        return ApiResponse.success(result);
    }

    @GetMapping("/knowledge/status")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "查询索引状态", description = "查询仓库的代码知识库索引状态")
    public ApiResponse<CodeKnowledgeService.IndexStatus> getKnowledgeStatus(
            @RequestParam @NotBlank(message = "仓库路径不能为空")
            @Parameter(description = "仓库本地路径") String repoPath) {
        if (repoPath.startsWith("http://") || repoPath.startsWith("https://")) {
            return ApiResponse.error(400, "代码知识库仅支持本地仓库路径");
        }
        CodeKnowledgeService.IndexStatus status = codeKnowledgeService.getIndexStatus(repoPath);
        return ApiResponse.success(status);
    }
}
