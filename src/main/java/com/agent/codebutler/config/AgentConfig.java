package com.agent.codebutler.config;

import com.agent.codebutler.middleware.AgentTraceMiddleware;
import com.agent.codebutler.middleware.CodingStandardsMiddleware;
import com.agent.codebutler.middleware.TokenTrackingMiddleware;
import com.agent.codebutler.service.CodeKnowledgeService;
import com.agent.codebutler.service.UserMemoryService;
import com.agent.codebutler.tools.CodeAnalysisTools;
import com.agent.codebutler.tools.KnowledgeRetrievalTool;
import com.agent.codebutler.tools.MemoryTools;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * AgentScope Harness Agent 配置
 * <p>
 * 支持从 application.yml 读取 API Key（不强制要求环境变量）。
 * 优先级：环境变量 > yml 配置。
 * <p>
 * 系统提示词外置到 classpath:prompts/system-prompt.md，
 * 子 Agent 声明通过 agentscope.subagents 配置，
 * MCP 工具模板外置到 classpath:mcp/tools-template.json。
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    private final AgentScopeProperties agentProps;
    private final CodeKnowledgeService codeKnowledgeService;
    private final UserMemoryService userMemoryService;
    private final MeterRegistry meterRegistry;

    // ---- API Key 配置（跨域配置，不归属 agentscope.* 前缀） ----

    @Value("${dashscope.api-key:#{null}}")
    private String dashscopeApiKey;

    @Value("${openai.api-key:#{null}}")
    private String openaiApiKey;

    @Value("${openai.base-url:#{null}}")
    private String openaiBaseUrl;

    @Value("${github.token:}")
    private String githubToken;

    public AgentConfig(AgentScopeProperties agentProps,
                       CodeKnowledgeService codeKnowledgeService,
                       UserMemoryService userMemoryService,
                       MeterRegistry meterRegistry) {
        this.agentProps = agentProps;
        this.codeKnowledgeService = codeKnowledgeService;
        this.userMemoryService = userMemoryService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 暴露 MemoryTools 为 Bean，供 Service 层在每次请求前设置 userId
     */
    @Bean
    public MemoryTools memoryTools() {
        return new MemoryTools(userMemoryService);
    }

    /**
     * 暴露 AgentTraceMiddleware 为 Bean，供 Service 层在每次请求前设置追踪回调
     */
    @Bean
    public AgentTraceMiddleware agentTraceMiddleware() {
        return new AgentTraceMiddleware();
    }

    /**
     * Token 用量追踪 Middleware（onModelCall 阶段）
     * 通过 Micrometer 指标记录每次模型调用的 Token 消耗
     */
    @Bean
    public TokenTrackingMiddleware tokenTrackingMiddleware() {
        return new TokenTrackingMiddleware(meterRegistry);
    }

    /**
     * 清理 Agent 持久化状态文件。
     */
    private void cleanAgentState(Path workspaceDir) {
        try {
            Path stateFile = workspaceDir
                    .resolve("agents").resolve("code-butler")
                    .resolve("context").resolve("code-butler")
                    .resolve("agent_state.json");
            if (Files.exists(stateFile)) {
                Files.delete(stateFile);
                log.info("已清理旧的 Agent 状态文件: {}", stateFile);
            }
        } catch (IOException e) {
            log.warn("清理 Agent 状态文件失败: {}", e.getMessage());
        }
    }

    @Bean
    public HarnessAgent codeButlerAgent(MemoryTools memoryTools,
                                         AgentTraceMiddleware agentTraceMiddleware,
                                         TokenTrackingMiddleware tokenTrackingMiddleware) {
        String defaultModel = agentProps.getModel().getDefaultValue();
        String fallbackModel = agentProps.getModel().getFallback();
        int maxRetries = agentProps.getModel().getMaxRetries();

        // 注册主模型 + 备用模型
        preRegisterModels(defaultModel);
        if (fallbackModel != null && !fallbackModel.isBlank()) {
            preRegisterModels(fallbackModel);
        }

        Path workspaceDir = Paths.get(agentProps.getWorkspace().getPath());
        if (agentProps.getState().isCleanupOnStart()) {
            cleanAgentState(workspaceDir);
        } else {
            log.info("Agent 状态持久化已启用（设置 agentscope.state.cleanup-on-start=true 可在启动时清理）");
        }
        ensureWorkspaceToolsConfig(workspaceDir);

        // 注册自定义代码分析工具 + RAG 知识检索工具 + 长期记忆工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new CodeAnalysisTools());
        toolkit.registerTool(new KnowledgeRetrievalTool(codeKnowledgeService));
        toolkit.registerTool(memoryTools);

        // ── 模型执行配置：超时 + 指数退避重试 ──
        ExecutionConfig modelExecConfig = ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(agentProps.getCallTimeoutSeconds()))
                .maxAttempts(maxRetries)
                .initialBackoff(Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(30))
                .backoffMultiplier(2.0)
                .build();

        // ── 从配置构建子 Agent（专家）──
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("code-butler")
                .sysPrompt(loadSystemPrompt())
                .model(defaultModel)
                .modelExecutionConfig(modelExecConfig)
                .maxRetries(maxRetries)
                .toolkit(toolkit)
                .middleware(new CodingStandardsMiddleware(null, meterRegistry))
                .middleware(agentTraceMiddleware)
                .middleware(tokenTrackingMiddleware)
                .enablePlanMode()
                .maxIters(agentProps.getMaxIters())
                .workspace(workspaceDir)
                .asyncToolTimeout(Duration.ofMinutes(5))
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                .compaction(CompactionConfig.builder()
                        .triggerMessages(agentProps.getCompaction().getTriggerMessages())
                        .keepMessages(agentProps.getCompaction().getKeepMessages())
                        .build());

        // 配置备用模型（主模型重试耗尽后自动切换）
        if (fallbackModel != null && !fallbackModel.isBlank()) {
            builder.fallbackModel(fallbackModel);
            log.info("备用模型已配置: {} (主模型 {} 重试 {} 次后自动切换)",
                    fallbackModel, defaultModel, maxRetries);
        }

        // 动态注册子 Agent
        for (AgentScopeProperties.Subagent sub : agentProps.getSubagents()) {
            builder.subagent(SubagentDeclaration.builder()
                    .name(sub.getName())
                    .description(sub.getDescription())
                    .model(defaultModel)
                    .steps(sub.getMaxIters())
                    .build());
            log.info("注册子 Agent: {} (steps={})", sub.getName(), sub.getMaxIters());
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            builder.disableShellTool();
            log.info("检测到 Windows 环境，已禁用 shell execute 工具");
        }

        HarnessAgent agent = builder.build();
        log.info("Code Butler Agent 初始化完成: model={}, fallback={}, retries={}, workspace={}",
                defaultModel,
                fallbackModel != null && !fallbackModel.isBlank() ? fallbackModel : "none",
                maxRetries,
                workspaceDir.toAbsolutePath());
        return agent;
    }

    /**
     * 从 classpath:prompts/system-prompt.md 加载系统提示词
     */
    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/system-prompt.md");
            String prompt = resource.getContentAsString(StandardCharsets.UTF_8);
            log.info("系统提示词已从 classpath:prompts/system-prompt.md 加载（{} 字符）", prompt.length());
            return prompt;
        } catch (IOException e) {
            log.error("加载系统提示词失败，使用内置默认", e);
            return "你是一个专业的代码仓库智能管家（Code Butler），可以进行代码审查、问答、文档生成和技术决策。";
        }
    }

    private void preRegisterModels(String defaultModel) {
        String modelName = extractModelName(defaultModel);

        if (defaultModel.startsWith("dashscope:")) {
            if (dashscopeApiKey != null && !dashscopeApiKey.isBlank()) {
                DashScopeChatModel model = DashScopeChatModel.builder()
                        .apiKey(dashscopeApiKey)
                        .modelName(modelName)
                        .stream(true)
                        .build();
                ModelRegistry.register(defaultModel, model);
                log.info("DashScope 模型已从 yml 注册: {}", defaultModel);
            } else {
                log.warn("defaultModel 配置为 dashscope: 前缀，但未配置 dashscope.api-key");
            }
        } else if (defaultModel.startsWith("openai:")) {
            if (openaiApiKey != null && !openaiApiKey.isBlank()) {
                var b = OpenAIChatModel.builder()
                        .apiKey(openaiApiKey)
                        .modelName(modelName)
                        .stream(true);

                if (openaiBaseUrl != null && !openaiBaseUrl.isBlank()) {
                    b.baseUrl(openaiBaseUrl);
                }

                ModelRegistry.register(defaultModel, b.build());
                log.info("OpenAI 兼容模型已从 yml 注册: {} (baseUrl={})",
                        defaultModel, openaiBaseUrl != null ? openaiBaseUrl : "默认");
            } else {
                log.warn("defaultModel 配置为 openai: 前缀，但未配置 openai.api-key");
            }
        } else {
            log.warn("defaultModel 前缀无法识别: {}", defaultModel);
        }
    }

    private static String extractModelName(String modelTag) {
        int idx = modelTag.indexOf(':');
        return idx >= 0 ? modelTag.substring(idx + 1) : modelTag;
    }

    /**
     * 从 classpath:mcp/tools-template.json 加载模板并替换占位符生成 tools.json
     */
    private void ensureWorkspaceToolsConfig(Path workspaceDir) {
        try {
            Files.createDirectories(workspaceDir);

            Path toolsJson = workspaceDir.resolve("tools.json");

            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank()) {
                token = githubToken;
            }

            if (token == null || token.isBlank()) {
                log.warn("GitHub Token 未配置，GitHub MCP 功能不可用");
                if (Files.exists(toolsJson)) {
                    Files.delete(toolsJson);
                    log.info("已删除旧的 tools.json（Token 未配置）");
                }
                return;
            }

            log.info("GitHub Token 已配置，MCP 远程仓库审查功能可用");

            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            String npxCommand = isWindows ? "npx.cmd" : "npx";

            // 从 classpath 加载模板并替换占位符
            ClassPathResource templateResource = new ClassPathResource("mcp/tools-template.json");
            String template = templateResource.getContentAsString(StandardCharsets.UTF_8);
            String toolsConfig = template
                    .replace("${npxCommand}", npxCommand)
                    .replace("${githubToken}", token);

            Files.writeString(toolsJson, toolsConfig);
            log.info("MCP tools.json 已生成: {}", toolsJson);

        } catch (IOException e) {
            log.warn("生成 MCP tools.json 失败: {}", e.getMessage());
        }
    }
}
