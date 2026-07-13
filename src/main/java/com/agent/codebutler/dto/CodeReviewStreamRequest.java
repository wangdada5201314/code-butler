package com.agent.codebutler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 代码审查（流式）请求
 */
@Data
public class CodeReviewStreamRequest {

    /** 仓库根目录的绝对路径 */
    @NotBlank(message = "仓库路径不能为空")
    private String repoPath;
}
