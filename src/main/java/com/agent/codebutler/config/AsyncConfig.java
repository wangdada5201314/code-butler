package com.agent.codebutler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * <p>
 * 替代 Spring 默认的 SimpleAsyncTaskExecutor（每次调用创建新线程），
 * 使用有界线程池防止高并发时资源耗尽。
 */
@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * @Async 默认线程池 — 用于操作记录、日志等非关键异步任务
     */
    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-");
        // 拒绝策略：丢弃最旧的任务并记录日志（操作记录丢失不影响主业务）
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("异步任务队列已满，丢弃任务: {}", r));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        log.info("异步线程池已初始化: core={}, max={}, queue={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), 200);
        return executor;
    }
}
