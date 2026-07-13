package com.agent.codebutler.util;

import java.util.function.Consumer;

/**
 * ThreadLocal 生命周期管理工具
 * <p>
 * 提供 AutoCloseable 和 Runnable 包装器，确保 ThreadLocal 值在使用后被清理，
 * 避免线程池复用时的数据泄漏和串号问题。
 * <p>
 * 用法示例：
 * <pre>{@code
 * // 方式一：try-with-resources
 * try (var scope = ThreadLocalContext.scopedValue(threadLocal, value)) {
 *     // threadLocal.get() == value
 * }
 * // threadLocal 已自动清理
 *
 * // 方式二：Runnable 包装
 * ThreadLocalContext.scoped(threadLocal, value, () -> {
 *     // threadLocal.get() == value
 * }).run();
 * }</pre>
 */
public final class ThreadLocalContext {

    private ThreadLocalContext() {}

    /**
     * 创建一个 AutoCloseable 作用域，设置 ThreadLocal 值并在关闭时自动清理
     *
     * @param threadLocal 目标 ThreadLocal
     * @param value       要设置的值
     * @return AutoCloseable 作用域对象
     */
    public static <T> Scope scopedValue(ThreadLocal<T> threadLocal, T value) {
        threadLocal.set(value);
        return () -> threadLocal.remove();
    }

    /**
     * 包装一个 Runnable，执行前设置 ThreadLocal 值，执行后自动清理
     *
     * @param threadLocal 目标 ThreadLocal
     * @param value       要设置的值
     * @param action      要执行的逻辑
     * @return 包装后的 Runnable
     */
    public static <T> Runnable scoped(ThreadLocal<T> threadLocal, T value, Runnable action) {
        return () -> {
            threadLocal.set(value);
            try {
                action.run();
            } finally {
                threadLocal.remove();
            }
        };
    }

    /**
     * 创建一个 AutoCloseable 作用域，使用 Consumer 设置值并在关闭时执行清理
     * <p>
     * 适用于非标准 ThreadLocal 场景（如 InheritableThreadLocal 或封装在对象内部的 ThreadLocal）。
     *
     * @param setter   设置值的函数
     * @param cleaner  清理函数
     * @return AutoCloseable 作用域对象
     */
    public static Scope scopedPair(Runnable setter, Runnable cleaner) {
        setter.run();
        return cleaner::run;
    }

    /**
     * AutoCloseable 作用域接口 — 支持 try-with-resources
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
