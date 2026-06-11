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
 * 配额配置实体
 * 存储各操作类型的每日限额（管理员可在前端动态调整）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("quota_config")
public class QuotaConfig implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("opType")
    private String opType;

    @Column("dailyLimit")
    private Integer dailyLimit;

    @Column("description")
    private String description;

    @Column(value = "updateTime", onUpdateValue = "now()")
    private LocalDateTime updateTime;
}
