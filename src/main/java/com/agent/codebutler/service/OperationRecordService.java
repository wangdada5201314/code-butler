package com.agent.codebutler.service;

import com.agent.codebutler.mapper.OperationRecordMapper;
import com.agent.codebutler.model.entity.OperationRecord;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作历史记录服务
 * 负责记录和查询用户的 AI 操作（审查 / 问答 / 文档生成）
 */
@Service
public class OperationRecordService {

    private static final Logger log = LoggerFactory.getLogger(OperationRecordService.class);
    private static final int MAX_SUMMARY_LENGTH = 500;

    private final OperationRecordMapper operationRecordMapper;

    public OperationRecordService(OperationRecordMapper operationRecordMapper) {
        this.operationRecordMapper = operationRecordMapper;
    }

    /**
     * 异步记录一次 AI 操作（不阻塞主业务流程）
     *
     * @param userId    用户 ID（可为 null，表示匿名操作）
     * @param opType    操作类型: REVIEW / CHAT / DOC
     * @param repoPath  仓库路径
     * @param input     用户输入（提问内容或文档类型）
     * @param output    AI 输出内容（会自动截断为 500 字摘要）
     * @param durationMs 耗时（毫秒）
     * @param sessionId Agent 会话 ID
     * @param status    状态: COMPLETED / FAILED / TIMEOUT
     * @param tokenCount 估算 token 消耗数
     */
    @Async("asyncExecutor")
    public void recordAsync(Long userId, String opType, String repoPath,
                            String input, String output, long durationMs,
                            String sessionId, String status, int tokenCount) {
        try {
            OperationRecord record = OperationRecord.builder()
                    .userId(userId != null ? userId : 0L)
                    .opType(opType)
                    .repoPath(repoPath)
                    .input(truncate(input, MAX_SUMMARY_LENGTH))
                    .outputSummary(truncate(output, MAX_SUMMARY_LENGTH))
                    .status(status != null ? status : "COMPLETED")
                    .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                    .sessionId(sessionId)
                    .tokenCount(tokenCount)
                    .build();
            operationRecordMapper.insert(record);
            log.debug("操作记录已保存: type={}, userId={}, sessionId={}", opType, userId, sessionId);
        } catch (Exception e) {
            // 操作记录保存失败不应影响主业务，只记日志
            log.warn("保存操作记录失败: type={}, userId={}", opType, userId, e);
        }
    }

    /**
     * 分页查询指定用户的操作历史
     *
     * @param userId   用户 ID
     * @param page     页码（从 1 开始）
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public Page<OperationRecord> getUserHistory(long userId, int page, int pageSize) {
        QueryWrapper query = QueryWrapper.create()
                .eq(OperationRecord::getUserId, userId)
                .orderBy(OperationRecord::getCreateTime, false);
        return operationRecordMapper.paginate(page, pageSize, query);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
