package com.agent.codebutler.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 记忆搜索请求
 */
@Data
public class MemorySearchRequest {

    /** 自然语言查询 */
    @NotBlank(message = "查询内容不能为空")
    private String query;

    /** 返回结果数量（默认 5，最大 20） */
    @Min(value = 1, message = "至少返回 1 条结果")
    @Max(value = 20, message = "最多返回 20 条结果")
    private int limit = 5;
}
