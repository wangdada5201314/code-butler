package com.agent.codebutler.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 编码规范注入 + 可观测性 Middleware
 * <p>
 * 功能：
 * 1. onSystemPrompt — 动态注入编码规范规则到 Agent 的系统提示词
 * 2. onReasoning — 追踪每轮推理耗时
 * 3. onActing — 追踪工具调用耗时和参数
 * <p>
 * 面试价值：展示 Middleware 架构思维（关注点分离、可插拔、可观测性）
 */
public class CodingStandardsMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(CodingStandardsMiddleware.class);

    /** 每轮推理的计时器 (sessionId -> startTime) */
    private final Map<String, Instant> reasoningTimers = new ConcurrentHashMap<>();

    /** 编码规范规则（可后续改为从数据库/配置文件读取） */
    private final String codingStandards;

    public CodingStandardsMiddleware() {
        this.codingStandards = buildDefaultStandards();
    }

    public CodingStandardsMiddleware(String customStandards) {
        this.codingStandards = customStandards;
    }

    // ════════════════════════════════════════════════════════
    //  1. 系统提示词注入 — 编码规范规则
    // ════════════════════════════════════════════════════════

    public Mono<String> onSystemPrompt(Agent agent, String currentPrompt) {
        String enhanced = currentPrompt + "\n\n" + codingStandards;
        log.debug("[Middleware] 系统提示词已注入编码规范 (原长度={}, 增强后={})",
                currentPrompt.length(), enhanced.length());
        return Mono.just(enhanced);
    }

    // ════════════════════════════════════════════════════════
    //  2. 推理追踪 — 记录每轮推理耗时
    // ════════════════════════════════════════════════════════

    public Flux<AgentEvent> onReasoning(Agent agent, ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        String sessionId = extractSessionId(agent);
        Instant start = Instant.now();
        reasoningTimers.put(sessionId, start);

        log.info("[Middleware] 推理开始: session={}, tools={}",
                sessionId, input.tools() != null ? input.tools().size() : 0);

        return next.apply(input).doOnComplete(() -> {
            Duration elapsed = Duration.between(start, Instant.now());
            reasoningTimers.remove(sessionId);
            log.info("[Middleware] 推理完成: session={}, elapsed={}ms", sessionId, elapsed.toMillis());
        }).doOnError(e -> {
            Duration elapsed = Duration.between(start, Instant.now());
            reasoningTimers.remove(sessionId);
            log.warn("[Middleware] 推理异常: session={}, elapsed={}ms, error={}",
                    sessionId, elapsed.toMillis(), e.getMessage());
        });
    }

    // ════════════════════════════════════════════════════════
    //  3. 工具调用追踪 — 记录工具名称、参数摘要、耗时
    // ════════════════════════════════════════════════════════

    public Flux<AgentEvent> onActing(Agent agent, ActingInput input,
                                      Function<ActingInput, Flux<AgentEvent>> next) {
        List<?> toolCalls = input.toolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (Object tc : toolCalls) {
                if (tc instanceof ToolUseBlock block) {
                    String toolName = block.getName();
                    Map<String, Object> args = block.getInput();
                    // 参数摘要：只记录 key 列表，不记录完整值（避免日志过大）
                    String argKeys = args != null ? String.join(", ", args.keySet()) : "none";
                    log.info("[Middleware] 工具调用: name={}, args=[{}]", toolName, argKeys);
                }
            }
        }

        Instant start = Instant.now();
        return next.apply(input).doOnComplete(() -> {
            Duration elapsed = Duration.between(start, Instant.now());
            log.info("[Middleware] 工具执行完成: count={}, elapsed={}ms",
                    toolCalls != null ? toolCalls.size() : 0, elapsed.toMillis());
        });
    }

    // ════════════════════════════════════════════════════════
    //  内部方法
    // ════════════════════════════════════════════════════════

    private String extractSessionId(Agent agent) {
        // 简化：使用 Agent 名称作为 session 标识
        // 生产环境可从 RuntimeContext 获取
        return agent.getName() != null ? agent.getName() : "unknown";
    }

    /**
     * 构建默认编码规范规则
     * <p>
     * 这些规则会被注入到 Agent 的系统提示词中，指导 AI 在审查时遵循统一标准。
     * 后续可从数据库动态读取，实现管理员自定义规范。
     */
    private String buildDefaultStandards() {
        return """
                ─── 编码规范审查标准 ───
                
                在进行代码审查时，请遵循以下编码规范标准：
                
                【命名规范】
                - 类名使用 PascalCase，方法名和变量名使用 camelCase
                - 常量使用 UPPER_SNAKE_CASE
                - 布尔变量/方法应使用 is/has/can/should 前缀
                - 避免单字母变量（循环索引除外）
                - 方法名应清晰表达意图，避免 abbreviate
                
                【方法设计】
                - 单个方法不超过 50 行（不含空行和注释）
                - 方法参数不超过 5 个，超过时使用对象封装
                - 圈复杂度不超过 10，超过时建议拆分
                - 避免副作用，方法应只做一件事
                
                【异常处理】
                - 不要捕获 Exception/Throwable 等顶层异常（除非在最外层兜底）
                - catch 块不能为空（至少记录日志）
                - 业务异常使用自定义异常类，携带错误码
                - 资源操作使用 try-with-resources
                
                【安全规范】
                - 禁止硬编码密码、密钥、Token
                - 用户输入必须校验（长度、格式、白名单）
                - SQL 使用参数化查询，禁止拼接
                - 敏感信息（密码、Token）不出现在日志中
                
                【性能关注】
                - 循环内避免数据库查询和 IO 操作
                - 集合操作优先使用 Stream API 或 SQL 聚合，避免全量加载后内存计算
                - 字符串拼接在循环中使用 StringBuilder
                - 注意 N+1 查询问题
                
                【代码风格】
                - 使用 4 空格缩进
                - 类文件不超过 500 行
                - 嵌套深度不超过 4 层
                - 复杂逻辑添加注释说明"为什么"而非"做了什么"
                """;
    }
}
