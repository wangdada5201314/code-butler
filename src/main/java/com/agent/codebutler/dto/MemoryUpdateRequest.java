package com.agent.codebutler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 记忆更新请求
 */
@Data
public class MemoryUpdateRequest {

    /** 更新后的记忆内容 */
    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容不能超过 5000 字符")
    private String content;
}
