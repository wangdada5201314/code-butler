package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.annotation.QuotaCheck;
import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.dto.CodeChatRequest;
import com.agent.codebutler.dto.CodeReviewResult;
import com.agent.codebutler.dto.DocGenerateResult;
import com.agent.codebutler.dto.GeneralChatRequest;
import com.agent.codebutler.dto.QuotaConfigUpdateRequest;
import com.agent.codebutler.model.entity.OperationRecord;
import com.agent.codebutler.model.entity.QuotaConfig;
import com.agent.codebutler.model.enums.UserRoleEnum;
import com.agent.codebutler.model.vo.OperationRecordVO;
import com.agent.codebutler.model.vo.UsageStatsVO;
import com.agent.codebutler.service.*;
import com.mybatisflex.core.paginate.Page;
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

import java.util.List;

@RestController
@RequestMapping("/api/code")
@Validated
@Tag(name = "代码管家", description = "代码审查、问答、文档生成接口")
public class CodeButlerController {

    private static final Logger log = LoggerFactory.getLogger(CodeButlerController.class);

    private final CodeReviewService codeReviewService;
    private final DocGenerationService docGenerationService;
    private final ChatService chatService;
    private final GeneralChatService generalChatService;
    private final UserService userService;
    private final OperationRecordService operationRecordService;
    private final UsageService usageService;
    private final QuotaConfigService quotaConfigService;

    public CodeButlerController(CodeReviewService codeReviewService,
                                DocGenerationService docGenerationService,
                                ChatService chatService,
                                GeneralChatService generalChatService,
                                UserService userService,
                                OperationRecordService operationRecordService,
                                UsageService usageService,
                                QuotaConfigService quotaConfigService) {
        this.codeReviewService = codeReviewService;
        this.docGenerationService = docGenerationService;
        this.chatService = chatService;
        this.generalChatService = generalChatService;
        this.userService = userService;
        this.operationRecordService = operationRecordService;
        this.usageService = usageService;
        this.quotaConfigService = quotaConfigService;
    }

    @PostMapping("/review")
    @AuthCheck(mustRole = "user")
    @QuotaCheck(opType = "REVIEW")
    @Operation(summary = "代码审查", description = "对指定仓库进行全面的代码审查，支持本地路径和 GitHub URL")
    public ApiResponse<CodeReviewResult> review(
            @RequestParam @NotBlank(message = "仓库路径不能为空")
            @Parameter(description = "仓库本地路径或 GitHub URL") String repoPath,
            HttpServletRequest request) {
        try {
            Long userId = userService.getLoginUserIdOrNull(request);
            CodeReviewResult result = codeReviewService.review(repoPath, userId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("代码审查失败: repoPath={}", repoPath, e);
            return ApiResponse.error(500, "代码审查失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = "user")
    @QuotaCheck(opType = "CHAT")
    @Operation(summary = "流式问答", description = "基于 SSE 的流式代码问答，实时返回 AI 分析结果")
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody CodeChatRequest request,
                                                     HttpServletRequest httpRequest) {
        Long userId = userService.getLoginUserIdOrNull(httpRequest);
        return chatService.streamChat(request, userId);
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
            HttpServletRequest request) {

        if (!docGenerationService.isValidDocType(docType)) {
            return ApiResponse.error(400, "不支持的文档类型: " + docType
                    + "，可选: " + String.join(", ", docGenerationService.getValidDocTypes()));
        }

        try {
            Long userId = userService.getLoginUserIdOrNull(request);
            DocGenerateResult result = docGenerationService.generate(repoPath, docType, userId);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("文档生成失败: repoPath={}, docType={}", repoPath, docType, e);
            return ApiResponse.error(500, "文档生成失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat/general/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = "user")
    @QuotaCheck(opType = "CHAT")
    @Operation(summary = "通用聊天", description = "不依赖代码仓库的自由 AI 对话，基于 SSE 流式返回")
    public Flux<ServerSentEvent<String>> generalChatStream(@Valid @RequestBody GeneralChatRequest request,
                                                            HttpServletRequest httpRequest) {
        Long userId = userService.getLoginUserIdOrNull(httpRequest);
        return generalChatService.streamChat(request, userId);
    }

    @GetMapping("/history")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "操作历史", description = "分页查询当前用户的 AI 操作历史记录")
    public ApiResponse<Page<OperationRecordVO>> getHistory(
            @RequestParam(defaultValue = "1") @Parameter(description = "页码") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "每页数量") int pageSize,
            HttpServletRequest request) {
        Long userId = userService.getLoginUserIdOrNull(request);
        if (userId == null) {
            return ApiResponse.error(40100, "请先登录");
        }

        Page<OperationRecord> recordPage = operationRecordService.getUserHistory(userId, page, pageSize);

        // 将实体转换为 VO（脱敏，不返回内部字段）
        List<OperationRecordVO> voList = recordPage.getRecords().stream()
                .map(r -> OperationRecordVO.builder()
                        .id(r.getId())
                        .opType(r.getOpType())
                        .repoPath(r.getRepoPath())
                        .input(r.getInput())
                        .outputSummary(r.getOutputSummary())
                        .status(r.getStatus())
                        .durationMs(r.getDurationMs())
                        .sessionId(r.getSessionId())
                        .createTime(r.getCreateTime())
                        .build())
                .toList();

        Page<OperationRecordVO> voPage = new Page<>();
        voPage.setRecords(voList);
        voPage.setPageNumber(recordPage.getPageNumber());
        voPage.setPageSize(recordPage.getPageSize());
        voPage.setTotalRow(recordPage.getTotalRow());

        return ApiResponse.success(voPage);
    }

    // ════════════════════════════════════════════════════════
    //  用量统计
    // ════════════════════════════════════════════════════════

    @GetMapping("/usage")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "用量统计", description = "获取当前用户的 AI 使用量统计（今日/本月调用次数、token 消耗、配额余量）")
    public ApiResponse<UsageStatsVO> getUsageStats(HttpServletRequest request) {
        Long userId = userService.getLoginUserIdOrNull(request);
        if (userId == null) {
            return ApiResponse.error(40100, "请先登录");
        }
        var loginUser = userService.getLoginUser(request);
        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        UsageStatsVO stats = usageService.getUsageStats(userId, isAdmin);
        return ApiResponse.success(stats);
    }

    // ════════════════════════════════════════════════════════
    //  配额配置管理（仅管理员）
    // ════════════════════════════════════════════════════════

    @GetMapping("/quota/config")
    @AuthCheck(mustRole = "admin")
    @Operation(summary = "获取配额配置", description = "获取所有操作类型的每日限额配置（仅管理员）")
    public ApiResponse<List<QuotaConfig>> getQuotaConfigs() {
        return ApiResponse.success(quotaConfigService.getAllConfigs());
    }

    @PutMapping("/quota/config")
    @AuthCheck(mustRole = "admin")
    @Operation(summary = "更新配额配置", description = "更新指定操作类型的每日限额（仅管理员）")
    public ApiResponse<Boolean> updateQuotaConfig(@Valid @RequestBody QuotaConfigUpdateRequest request) {
        quotaConfigService.updateDailyLimit(request.getOpType(), request.getDailyLimit());
        return ApiResponse.success(true);
    }
}
