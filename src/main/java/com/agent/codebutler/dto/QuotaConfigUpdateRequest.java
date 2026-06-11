package com.agent.codebutler.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新配额配置请求体（仅管理员可用）
 */
@Data
public class QuotaConfigUpdateRequest {

    @NotBlank(message = "操作类型不能为空")
    private String opType;

    /** 每日限额，-1 表示不限 */
    @Min(value = -1, message = "限额值不能小于 -1")
    private int dailyLimit;
}
