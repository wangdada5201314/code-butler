package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.dto.MemorySearchRequest;
import com.agent.codebutler.dto.MemoryUpdateRequest;
import com.agent.codebutler.model.entity.OperationRecord;
import com.agent.codebutler.model.enums.UserRoleEnum;
import com.agent.codebutler.model.vo.OperationRecordVO;
import com.agent.codebutler.model.vo.UsageStatsVO;
import com.agent.codebutler.model.vo.UserMemoryVO;
import com.agent.codebutler.service.OperationRecordService;
import com.agent.codebutler.service.UsageService;
import com.agent.codebutler.service.UserMemoryService;
import com.agent.codebutler.service.UserService;
import com.agent.codebutler.util.UserContext;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户仪表板 Controller — 操作历史 / 用量统计 / 长期记忆管理
 */
@RestController
@RequestMapping("/api/code")
@Validated
@Tag(name = "用户仪表板", description = "操作历史、用量统计、长期记忆管理接口")
public class UserDashboardController {

    private final OperationRecordService operationRecordService;
    private final UsageService usageService;
    private final UserMemoryService userMemoryService;
    private final UserService userService;

    public UserDashboardController(OperationRecordService operationRecordService,
                                   UsageService usageService,
                                   UserMemoryService userMemoryService,
                                   UserService userService) {
        this.operationRecordService = operationRecordService;
        this.usageService = usageService;
        this.userMemoryService = userMemoryService;
        this.userService = userService;
    }

    // ════════════════════════════════════════════════════════
    //  操作历史
    // ════════════════════════════════════════════════════════

    @GetMapping("/history")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "操作历史", description = "分页查询当前用户的 AI 操作历史记录")
    public ApiResponse<Page<OperationRecordVO>> getHistory(
            @RequestParam(defaultValue = "1") @Parameter(description = "页码") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "每页数量") int pageSize,
            HttpServletRequest request) {
        Long userId = UserContext.getUserId(request);
        if (userId == null) return ApiResponse.error(40100, "请先登录");

        Page<OperationRecord> recordPage = operationRecordService.getUserHistory(userId, page, pageSize);

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
        Long userId = UserContext.getUserId(request);
        if (userId == null) return ApiResponse.error(40100, "请先登录");

        var loginUser = userService.getLoginUser(request);
        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());
        UsageStatsVO stats = usageService.getUsageStats(userId, isAdmin);
        return ApiResponse.success(stats);
    }

    // ════════════════════════════════════════════════════════
    //  长期记忆管理
    // ════════════════════════════════════════════════════════

    @GetMapping("/memory")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "记忆列表", description = "获取当前用户的所有长期记忆")
    public ApiResponse<List<UserMemoryVO>> listMemories(
            @RequestParam(required = false) @Parameter(description = "按类型筛选") String memoryType,
            HttpServletRequest request) {
        Long userId = UserContext.getUserId(request);
        if (userId == null) return ApiResponse.error(40100, "请先登录");

        List<UserMemoryVO> voList = userMemoryService.listByUser(userId, memoryType).stream()
                .map(e -> UserMemoryVO.builder()
                        .id(e.getId())
                        .memoryType(e.getMemoryType())
                        .content(e.getContent())
                        .summary(e.getSummary())
                        .metadata(e.getMetadata())
                        .ttlDays(e.getTtlDays())
                        .createTime(e.getCreateTime())
                        .updateTime(e.getUpdateTime())
                        .build())
                .toList();
        return ApiResponse.success(voList);
    }

    @PostMapping("/memory/search")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "搜索记忆", description = "语义搜索当前用户的长期记忆")
    public ApiResponse<List<Map<String, Object>>> searchMemories(
            @Valid @RequestBody MemorySearchRequest body,
            HttpServletRequest request) {
        Long userId = UserContext.getUserId(request);
        if (userId == null) return ApiResponse.error(40100, "请先登录");

        List<Map<String, Object>> results = userMemoryService.search(userId, body.getQuery(), body.getLimit()).stream()
                .map(r -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", r.id());
                    map.put("memoryType", r.memoryType());
                    map.put("content", r.content());
                    map.put("summary", r.summary());
                    map.put("score", r.score());
                    return map;
                })
                .toList();
        return ApiResponse.success(results);
    }

    @DeleteMapping("/memory/{id}")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "删除记忆", description = "删除指定 ID 的长期记忆")
    public ApiResponse<Boolean> deleteMemory(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = UserContext.getUserId(request);
        if (userId == null) return ApiResponse.error(40100, "请先登录");
        boolean ok = userMemoryService.delete(id, userId);
        return ok ? ApiResponse.success(true) : ApiResponse.error(40000, "记忆不存在或无权限");
    }

    @PutMapping("/memory/{id}")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "更新记忆", description = "更新指定记忆的内容")
    public ApiResponse<Boolean> updateMemory(
            @PathVariable Long id,
            @Valid @RequestBody MemoryUpdateRequest body,
            HttpServletRequest request) {
        Long userId = UserContext.getUserId(request);
        if (userId == null) return ApiResponse.error(40100, "请先登录");
        boolean ok = userMemoryService.update(id, userId, body.getContent());
        return ok ? ApiResponse.success(true) : ApiResponse.error(40000, "记忆不存在或无权限");
    }
}
