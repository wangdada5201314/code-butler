package com.agent.codebutler.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户长期记忆 VO（脱敏，不暴露 embedding/userId 等内部字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMemoryVO {

    private Long id;

    private String memoryType;

    private String content;

    private String summary;

    private String metadata;

    private Integer ttlDays;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
