package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.dto.QuotaConfigUpdateRequest;
import com.agent.codebutler.model.entity.QuotaConfig;
import com.agent.codebutler.service.QuotaConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员配额配置 Controller
 */
@RestController
@RequestMapping("/api/code")
@Validated
@Tag(name = "配额管理", description = "管理员配额配置接口")
public class AdminQuotaController {

    private final QuotaConfigService quotaConfigService;

    public AdminQuotaController(QuotaConfigService quotaConfigService) {
        this.quotaConfigService = quotaConfigService;
    }

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
