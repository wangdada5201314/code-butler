package com.agent.codebutler.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Token 用量追踪 Middleware — 利用 2.0 新增的 onModelCall 阶段
 * <p>
 * 在每次模型调用完成后，从 {@link ModelCallEndEvent} 中提取 {@link ChatUsage}，
 * 记录 input/output/cached tokens 到 Micrometer 指标体系，
 * 同时维护会话级别的累计用量供配额管理使用。
 * <p>
 * 面试价值：展示 2.0 五阶段 Middleware 架构中 onModelCall 的实战应用，
 * 以及 Micrometer + Prometheus 可观测性体系的集成。
 */
public class TokenTrackingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(TokenTrackingMiddleware.class);

    private final MeterRegistry meterRegistry;

    /** 累计 Token 用量（全局级别，可用于告警） */
    private final AtomicLong totalInputTokens = new AtomicLong(0);
    private final AtomicLong totalOutputTokens = new AtomicLong(0);

    /** 预创建的 Micrometer 指标 */
    private final Counter inputTokenCounter;
    private final Counter outputTokenCounter;
    private final Counter cachedTokenCounter;
    private final Counter modelCallCounter;

    public TokenTrackingMiddleware(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.inputTokenCounter = meterRegistry != null
                ? Counter.builder("agent.model.tokens")
                    .tag("direction", "input")
                    .description("模型调用输入 Token 总数")
                    .register(meterRegistry)
                : null;
        this.outputTokenCounter = meterRegistry != null
                ? Counter.builder("agent.model.tokens")
                    .tag("direction", "output")
                    .description("模型调用输出 Token 总数")
                    .register(meterRegistry)
                : null;
        this.cachedTokenCounter = meterRegistry != null
                ? Counter.builder("agent.model.tokens")
                    .tag("direction", "cached")
                    .description("模型调用缓存命中 Token 数")
                    .register(meterRegistry)
                : null;
        this.modelCallCounter = meterRegistry != null
                ? Counter.builder("agent.model.calls")
                    .description("模型调用总次数")
                    .register(meterRegistry)
                : null;
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx,
                                         ModelCallInput input,
                                         Function<ModelCallInput, Flux<AgentEvent>> next) {

        String modelName = input.model() != null ? input.model().getModelName() : "unknown";
        int messageCount = input.messages() != null ? input.messages().size() : 0;
        int toolsCount = input.tools() != null ? input.tools().size() : 0;

        log.debug("[TokenTracker] 模型调用开始: model={}, messages={}, tools={}",
                modelName, messageCount, toolsCount);

        return next.apply(input)
                .doOnNext(event -> {
                    if (event instanceof ModelCallEndEvent endEvent) {
                        ChatUsage usage = endEvent.getUsage();
                        if (usage != null) {
                            recordUsage(modelName, usage);
                        }
                    }
                });
    }

    private void recordUsage(String modelName, ChatUsage usage) {
        int in = usage.getInputTokens();
        int out = usage.getOutputTokens();
        int cached = usage.getCachedTokens();
        int total = usage.getTotalTokens();
        double time = usage.getTime();

        // 累计计数
        totalInputTokens.addAndGet(in);
        totalOutputTokens.addAndGet(out);

        // Micrometer 指标
        if (inputTokenCounter != null) inputTokenCounter.increment(in);
        if (outputTokenCounter != null) outputTokenCounter.increment(out);
        if (cachedTokenCounter != null && cached > 0) cachedTokenCounter.increment(cached);
        if (modelCallCounter != null) modelCallCounter.increment();

        log.info("[TokenTracker] 模型调用完成: model={}, in={}, out={}, cached={}, total={}, time={}s, "
                        + "累计: in={}, out={}",
                modelName, in, out, cached, total, String.format("%.2f", time),
                totalInputTokens.get(), totalOutputTokens.get());
    }

    // ── 查询方法（供 Service 层或 Controller 使用） ──

    public long getTotalInputTokens() {
        return totalInputTokens.get();
    }

    public long getTotalOutputTokens() {
        return totalOutputTokens.get();
    }

    public long getTotalTokens() {
        return totalInputTokens.get() + totalOutputTokens.get();
    }
}
