package com.agent.codebutler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码问答请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeChatRequest {

    @NotBlank(message = "提问内容不能为空")
    private String question;

    @NotBlank(message = "仓库路径不能为空")
    private String repoPath;

    private String sessionId;

    /**
     * Plan Mode：开启后 Agent 会先制定分步计划，再逐步执行
     */
    private Boolean planMode = false;
}
