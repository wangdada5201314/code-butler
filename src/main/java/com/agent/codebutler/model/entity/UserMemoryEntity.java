package com.agent.codebutler.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户长期记忆实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_memory")
public class UserMemoryEntity {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long userId;

    /** 记忆类型：PREFERENCE / DECISION / FACT / HABIT / GENERAL */
    private String memoryType;

    /** 记忆内容（自然语言，用于语义检索） */
    private String content;

    /** 记忆摘要（用于列表展示，避免加载全文） */
    private String summary;

    /** 向量嵌入 JSON */
    private String embedding;

    /** 附加元数据 JSON */
    private String metadata;

    /** 存活天数 */
    private Integer ttlDays;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
