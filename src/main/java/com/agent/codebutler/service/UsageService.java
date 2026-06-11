package com.agent.codebutler.service;

import com.agent.codebutler.mapper.OperationRecordMapper;
import com.agent.codebutler.model.entity.OperationRecord;
import com.agent.codebutler.model.vo.UsageStatsVO;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 用量统计与配额服务
 * <p>
 * 提供用量查询、配额校验和 token 估算功能。
 * 配额规则基于角色：admin 不限，普通用户按配置限额。
 */
@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private final OperationRecordMapper operationRecordMapper;

    /** 每日审查次数上限（普通用户） */
    @Value("${quota.review.daily-limit:20}")
    private int reviewDailyLimit;

    /** 每日问答次数上限（普通用户，含智能问答 + 通用聊天） */
    @Value("${quota.chat.daily-limit:50}")
    private int chatDailyLimit;

    /** 每日文档生成次数上限（普通用户） */
    @Value("${quota.doc.daily-limit:20}")
    private int docDailyLimit;

    public UsageService(OperationRecordMapper operationRecordMapper) {
        this.operationRecordMapper = operationRecordMapper;
    }

    // ════════════════════════════════════════════════════════
    //  配额校验
    // ════════════════════════════════════════════════════════

    /**
     * 检查用户是否有配额执行指定操作
     *
     * @param userId 用户 ID
     * @param opType 操作类型：REVIEW / CHAT / DOC
     * @param isAdmin 是否管理员
     * @return true = 有配额，false = 已超限
     */
    public boolean hasQuota(long userId, String opType, boolean isAdmin) {
        if (isAdmin) return true;

        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        int todayCount = countByType(userId, opType, dayStart);

        int limit = getDailyLimit(opType);
        return limit < 0 || todayCount < limit;
    }

    /**
     * 获取指定操作类型的每日限额
     *
     * @return 限额值，-1 表示不限
     */
    public int getDailyLimit(String opType) {
        return switch (opType) {
            case "REVIEW" -> reviewDailyLimit;
            case "CHAT" -> chatDailyLimit;
            case "DOC" -> docDailyLimit;
            default -> -1;
        };
    }

    /**
     * 获取今日剩余配额
     */
    public int getDailyRemaining(long userId, String opType, boolean isAdmin) {
        if (isAdmin) return -1;
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        int todayCount = countByType(userId, opType, dayStart);
        int limit = getDailyLimit(opType);
        if (limit < 0) return -1;
        return Math.max(0, limit - todayCount);
    }

    // ════════════════════════════════════════════════════════
    //  用量统计查询
    // ════════════════════════════════════════════════════════

    /**
     * 获取用户完整的用量统计
     */
    public UsageStatsVO getUsageStats(long userId, boolean isAdmin) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // 今日各类型调用数
        int todayReview = countByType(userId, "REVIEW", dayStart);
        int todayChat = countByType(userId, "CHAT", dayStart);
        int todayDoc = countByType(userId, "DOC", dayStart);

        // 本月各类型调用数
        int monthReview = countByType(userId, "REVIEW", monthStart);
        int monthChat = countByType(userId, "CHAT", monthStart);
        int monthDoc = countByType(userId, "DOC", monthStart);

        // 本月 token 消耗
        long monthTokens = sumTokens(userId, monthStart);

        return UsageStatsVO.builder()
                .todayReviewCount(todayReview)
                .todayChatCount(todayChat)
                .todayDocCount(todayDoc)
                .todayTotalCount(todayReview + todayChat + todayDoc)
                .monthReviewCount(monthReview)
                .monthChatCount(monthChat)
                .monthDocCount(monthDoc)
                .monthTotalCount(monthReview + monthChat + monthDoc)
                .monthTokenCount(monthTokens)
                .reviewDailyLimit(isAdmin ? -1 : reviewDailyLimit)
                .chatDailyLimit(isAdmin ? -1 : chatDailyLimit)
                .docDailyLimit(isAdmin ? -1 : docDailyLimit)
                .reviewDailyRemaining(isAdmin ? -1 : Math.max(0, reviewDailyLimit - todayReview))
                .chatDailyRemaining(isAdmin ? -1 : Math.max(0, chatDailyLimit - todayChat))
                .docDailyRemaining(isAdmin ? -1 : Math.max(0, docDailyLimit - todayDoc))
                .isAdmin(isAdmin)
                .build();
    }

    // ════════════════════════════════════════════════════════
    //  Token 估算
    // ════════════════════════════════════════════════════════

    /**
     * 根据文本长度估算 token 数
     * 中文约 1 token/1.5 字符，英文约 1 token/4 字符，取折中值 ~2.5 字符/token
     *
     * @param texts 要估算的文本（可多个，会合并计算）
     * @return 估算 token 数
     */
    public static int estimateTokens(String... texts) {
        int totalChars = 0;
        for (String text : texts) {
            if (text != null) {
                totalChars += text.length();
            }
        }
        return (int) Math.ceil(totalChars / 2.5);
    }

    // ════════════════════════════════════════════════════════
    //  内部查询方法
    // ════════════════════════════════════════════════════════

    private int countByType(long userId, String opType, LocalDateTime since) {
        QueryWrapper query = QueryWrapper.create()
                .eq(OperationRecord::getUserId, userId)
                .eq(OperationRecord::getOpType, opType)
                .ge(OperationRecord::getCreateTime, since);
        List<OperationRecord> records = operationRecordMapper.selectListByQuery(query);
        return records.size();
    }

    private long sumTokens(long userId, LocalDateTime since) {
        QueryWrapper query = QueryWrapper.create()
                .eq(OperationRecord::getUserId, userId)
                .ge(OperationRecord::getCreateTime, since);
        List<OperationRecord> records = operationRecordMapper.selectListByQuery(query);
        return records.stream()
                .mapToLong(r -> r.getTokenCount() != null ? r.getTokenCount() : 0)
                .sum();
    }
}
