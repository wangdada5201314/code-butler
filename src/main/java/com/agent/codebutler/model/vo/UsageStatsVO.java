package com.agent.codebutler.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用量统计视图对象
 * 聚合展示用户在指定周期内的 AI 使用情况
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatsVO {

    /** 今日审查次数 */
    private int todayReviewCount;

    /** 今日问答次数（含智能问答 + 通用聊天） */
    private int todayChatCount;

    /** 今日文档生成次数 */
    private int todayDocCount;

    /** 今日总调用次数 */
    private int todayTotalCount;

    /** 本月审查次数 */
    private int monthReviewCount;

    /** 本月问答次数 */
    private int monthChatCount;

    /** 本月文档生成次数 */
    private int monthDocCount;

    /** 本月总调用次数 */
    private int monthTotalCount;

    /** 本月估算 token 消耗 */
    private long monthTokenCount;

    /** 今日审查配额上限（-1 表示不限） */
    private int reviewDailyLimit;

    /** 今日问答配额上限（-1 表示不限） */
    private int chatDailyLimit;

    /** 今日文档配额上限（-1 表示不限） */
    private int docDailyLimit;

    /** 今日剩余审查次数（-1 表示不限） */
    private int reviewDailyRemaining;

    /** 今日剩余问答次数（-1 表示不限） */
    private int chatDailyRemaining;

    /** 今日剩余文档次数（-1 表示不限） */
    private int docDailyRemaining;

    /** 是否为管理员（不限配额） */
    private boolean isAdmin;
}
