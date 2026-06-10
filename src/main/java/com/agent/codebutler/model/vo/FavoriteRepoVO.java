package com.agent.codebutler.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收藏仓库视图对象（返回给前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteRepoVO {

    private Long id;

    /** 仓库路径 */
    private String repoPath;

    /** 自定义显示名称 */
    private String repoName;

    /** 创建时间 */
    private LocalDateTime createTime;
}
