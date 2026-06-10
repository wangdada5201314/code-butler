package com.agent.codebutler.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作历史记录视图对象（返回给前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationRecordVO {

    private Long id;

    /** 操作类型: REVIEW / CHAT / DOC */
    private String opType;

    /** 仓库路径 */
    private String repoPath;

    /** 用户输入摘要 */
    private String input;

    /** AI 输出摘要 */
    private String outputSummary;

    /** 状态: COMPLETED / FAILED / TIMEOUT */
    private String status;

    /** 耗时（毫秒） */
    private Integer durationMs;

    /** Agent 会话 ID */
    private String sessionId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
