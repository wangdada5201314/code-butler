package com.agent.codebutler.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户上下文工具类
 * <p>
 * 从 RequestAttribute 读取当前用户 ID（由 AuthInterceptor 在鉴权通过后设置），
 * 避免 Controller 层重复调用 UserService.getLoginUserIdOrNull()。
 */
public final class UserContext {

    private static final String ATTR_USER_ID = "code-butler.userId";

    private UserContext() {}

    /**
     * 设置当前请求的用户 ID（由 AuthInterceptor 调用）
     */
    public static void setUserId(HttpServletRequest request, Long userId) {
        request.setAttribute(ATTR_USER_ID, userId);
    }

    /**
     * 获取当前请求的用户 ID
     *
     * @return 用户 ID，未登录时返回 null
     */
    public static Long getUserId(HttpServletRequest request) {
        Object id = request.getAttribute(ATTR_USER_ID);
        return id instanceof Long ? (Long) id : null;
    }
}
