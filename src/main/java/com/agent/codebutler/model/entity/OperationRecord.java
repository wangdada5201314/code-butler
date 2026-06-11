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
 * AI 操作历史记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("operation_record")
public class OperationRecord implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("userId")
    private Long userId;

    @Column("opType")
    private String opType;

    @Column("repoPath")
    private String repoPath;

    @Column("input")
    private String input;

    @Column("outputSummary")
    private String outputSummary;

    @Column("status")
    private String status;

    @Column("durationMs")
    private Integer durationMs;

    @Column("tokenCount")
    private Integer tokenCount;

    @Column("sessionId")
    private String sessionId;

    @Column(value = "createTime", onInsertValue = "now()")
    private LocalDateTime createTime;
}
