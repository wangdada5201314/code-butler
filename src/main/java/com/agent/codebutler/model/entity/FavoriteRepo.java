package com.agent.codebutler.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户收藏仓库实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("favorite_repo")
public class FavoriteRepo implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("userId")
    private Long userId;

    /** 仓库绝对路径 */
    @Column("repoPath")
    private String repoPath;

    /** 自定义显示名称（可选） */
    @Column("repoName")
    private String repoName;

    @Column(value = "createTime", onInsertValue = "now()")
    private LocalDateTime createTime;
}
