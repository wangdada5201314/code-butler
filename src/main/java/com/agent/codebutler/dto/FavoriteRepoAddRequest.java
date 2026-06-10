package com.agent.codebutler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 添加收藏仓库请求体
 */
@Data
public class FavoriteRepoAddRequest {

    @NotBlank(message = "仓库路径不能为空")
    private String repoPath;

    /** 自定义显示名称（可选） */
    private String repoName;
}
