package com.agent.codebutler.tools;

import com.agent.codebutler.service.UserMemoryService;
import com.agent.codebutler.util.ThreadLocalContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.stream.Collectors;

/**
 * 长期记忆工具 — Agent 可自主调用以读写跨会话记忆
 * <p>
 * 通过 @Tool 注解注册到 AgentScope Toolkit，Agent 在推理过程中可自主决定：
 * - 何时将重要信息写入记忆（用户偏好、关键决策、项目事实）
 * - 何时检索记忆以获取历史上下文
 */
public class MemoryTools {

    private final UserMemoryService memoryService;

    /**
     * 当前会话的用户 ID（由外层 Service 设置）。
     * 使用 InheritableThreadLocal 确保 AgentScope 内部创建的子线程能继承父线程的 userId，
     * 避免 Reactor 调度或工具异步执行时 ThreadLocal 丢失。
     */
    private final InheritableThreadLocal<Long> currentUserId = new InheritableThreadLocal<>();

    public MemoryTools(UserMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 设置当前请求的用户 ID（在每次 Agent 调用前由 Service 层设置）
     */
    public void setUserId(Long userId) {
        currentUserId.set(userId);
    }

    /**
     * 清除当前线程的用户 ID（Agent 调用结束后必须调用，防止线程池复用时串号）
     */
    public void clearUserId() {
        currentUserId.remove();
    }

    /**
     * 创建一个 userId 作用域，设置用户 ID 并在关闭时自动清理
     * <p>
     * 推荐使用 try-with-resources 模式：
     * <pre>{@code
     * try (var scope = memoryTools.scopedUserId(userId)) {
     *     // Agent 调用期间 userId 可用
     * }
     * // userId 已自动清理
     * }</pre>
     */
    public ThreadLocalContext.Scope scopedUserId(Long userId) {
        return ThreadLocalContext.scopedValue(currentUserId, userId);
    }

    @Tool(name = "record_to_memory",
          description = "写入一条长期记忆。当用户表达偏好、做重要决策或分享关键项目信息时调用。"
                  + "memoryType 可选: PREFERENCE(偏好) / DECISION(决策) / FACT(事实) / HABIT(习惯) / GENERAL(通用)")
    public String recordToMemory(
            @ToolParam(name = "content", required = true, description = "记忆内容（自然语言描述）") String content,
            @ToolParam(name = "memoryType", required = true, description = "记忆类型: PREFERENCE/DECISION/FACT/HABIT/GENERAL") String memoryType) {
        Long userId = currentUserId.get();
        if (userId == null) {
            return "[MEMORY] 无法记录：未获取到当前用户";
        }
        try {
            var entity = memoryService.record(userId, content, memoryType, null, null, 90);
            return "[MEMORY] 已记录长期记忆 #" + entity.getId()
                    + " (type=" + memoryType + ", summary=" + entity.getSummary() + ")";
        } catch (Exception e) {
            return "[MEMORY] 记录失败: " + e.getMessage();
        }
    }

    @Tool(name = "retrieve_from_memory",
          description = "语义检索长期记忆。对话开始时调用以了解用户偏好和历史上下文。"
                  + "limit 建议 3-5，值越大结果越多。")
    public String retrieveFromMemory(
            @ToolParam(name = "query", required = true, description = "查询文本（自然语言，如'用户偏好''上次技术选型'）") String query,
            @ToolParam(name = "limit", required = false, description = "返回数量上限，默认 5") int limit) {
        if (limit <= 0) limit = 5;
        Long userId = currentUserId.get();
        if (userId == null) {
            return "[MEMORY] 无法检索：未获取到当前用户";
        }
        try {
            var results = memoryService.search(userId, query, limit);
            if (results.isEmpty()) {
                return "[MEMORY] 未找到相关长期记忆";
            }
            return results.stream()
                    .map(r -> String.format("- [%s] score=%.2f: %s",
                            r.memoryType(), r.score(), r.content()))
                    .collect(Collectors.joining("\n",
                            "[MEMORY] 找到 " + results.size() + " 条相关记忆:\n", ""));
        } catch (Exception e) {
            return "[MEMORY] 检索失败: " + e.getMessage();
        }
    }
}
