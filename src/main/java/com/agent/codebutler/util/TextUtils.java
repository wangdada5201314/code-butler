package com.agent.codebutler.util;

/**
 * 文本处理公共工具
 * <p>
 * 提供 Token 估算等被多个 Service 调用的静态方法，
 * 消除 UsageService.estimateTokens 和 CodeReviewService.extractText 等跨服务静态耦合。
 */
public final class TextUtils {

    private TextUtils() {}

    /**
     * 估算文本的 Token 数量（中英文混合启发式）
     * <p>
     * 基于 2.5 字符 ≈ 1 Token 的经验比率，适用于中英文混合内容的粗略估算。
     *
     * @param texts 一段或多段文本
     * @return 估算 Token 数（向上取整）
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

    /**
     * 清理字符串中的换行符，防止破坏 SSE 协议格式
     *
     * @param msg 原始字符串
     * @return 清理后的字符串，null 输入返回 "未知错误"
     */
    public static String sanitizeSseError(String msg) {
        if (msg == null) return "未知错误";
        return msg.replace("\n", " ").replace("\r", "");
    }
}
