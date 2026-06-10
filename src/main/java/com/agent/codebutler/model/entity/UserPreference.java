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
 * 用户偏好配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_preference")
public class UserPreference implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("userId")
    private Long userId;

    /** 审查关注点（逗号分隔）: naming,performance,security,architecture,readability */
    @Column("reviewFocus")
    private String reviewFocus;

    /** 审查深度: detailed / standard / concise */
    @Column("reviewDepth")
    private String reviewDepth;

    /** 自定义审查指令（自由文本） */
    @Column("customPrompt")
    private String customPrompt;

    @Column(value = "createTime", onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(value = "updateTime", onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;
}
