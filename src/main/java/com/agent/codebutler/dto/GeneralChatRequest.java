package com.agent.codebutler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用聊天请求（不需要仓库路径）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneralChatRequest {

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private String sessionId;

    /**
     * Plan Mode：开启后 Agent 会先制定分步计划，再逐步执行
     */
    private Boolean planMode = false;
}
