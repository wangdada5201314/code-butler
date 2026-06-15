package com.agent.codebutler.config;

import com.agent.codebutler.middleware.AgentTraceMiddleware;
import com.agent.codebutler.middleware.CodingStandardsMiddleware;
import com.agent.codebutler.service.CodeKnowledgeService;
import com.agent.codebutler.service.UserMemoryService;
import com.agent.codebutler.tools.CodeAnalysisTools;
import com.agent.codebutler.tools.KnowledgeRetrievalTool;
import com.agent.codebutler.tools.MemoryTools;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.OpenAIChatModel;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AgentScope Harness Agent 配置
 * <p>
 * 支持从 application.yml 读取 API Key（不强制要求环境变量）。
 * 优先级：环境变量 > yml 配置。
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Value("${agentscope.model.default:dashscope:qwen-plus}")
    private String defaultModel;

    @Value("${agentscope.workspace.path:.agentscope/workspace}")
    private String workspacePath;

    @Value("${agentscope.compaction.trigger-messages:30}")
    private int triggerMessages;

    @Value("${agentscope.compaction.keep-messages:10}")
    private int keepMessages;

    @Value("${agentscope.max-iters:25}")
    private int maxIters;

    @Value("${agentscope.state.cleanup-on-start:false}")
    private boolean cleanupOnStart;

    // ---- API Key 配置（可从 yml 读取） ----

    @Value("${dashscope.api-key:#{null}}")
    private String dashscopeApiKey;

    @Value("${openai.api-key:#{null}}")
    private String openaiApiKey;

    @Value("${openai.base-url:#{null}}")
    private String openaiBaseUrl;

    @Value("${github.token:}")
    private String githubToken;

    private final CodeKnowledgeService codeKnowledgeService;
    private final UserMemoryService userMemoryService;
    private final MeterRegistry meterRegistry;

    public AgentConfig(CodeKnowledgeService codeKnowledgeService,
                       UserMemoryService userMemoryService,
                       MeterRegistry meterRegistry) {
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
    public HarnessAgent codeButlerAgent(MemoryTools memoryTools, AgentTraceMiddleware agentTraceMiddleware) {
        preRegisterModels();

        Path workspaceDir = Paths.get(workspacePath);
        if (cleanupOnStart) {
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

        // ── 定义子 Agent（专家） ──
        SubagentDeclaration securityAgent = SubagentDeclaration.builder()
                .name("SecurityAgent")
                .description("安全审查专家。专注检测安全漏洞：SQL 注入、XSS、路径穿越、硬编码密钥、"
                        + "不安全的加密算法、CVE 已知漏洞。审查完成后输出按严重程度排序的漏洞清单。")
                .model(defaultModel)
                .maxIters(12)
                .build();

        SubagentDeclaration performanceAgent = SubagentDeclaration.builder()
                .name("PerformanceAgent")
                .description("性能分析专家。专注识别性能瓶颈：N+1 查询、内存泄漏风险、"
                        + "不必要的对象创建、O(n²) 算法、IO 阻塞、锁竞争。输出优化建议和预估收益。")
                .model(defaultModel)
                .maxIters(10)
                .build();

        SubagentDeclaration architectureAgent = SubagentDeclaration.builder()
                .name("ArchitectureAgent")
                .description("架构评审专家。从设计模式、SOLID 原则、模块耦合度、分层合规性、"
                        + "可扩展性角度审查代码架构。识别反模式并给出重构路线图。")
                .model(defaultModel)
                .maxIters(15)
                .build();

        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("code-butler")
                .sysPrompt("""
                        你是一个专业的代码仓库智能管家（Code Butler），具备以下能力：

                        1. **代码审查**：分析代码质量、发现潜在 Bug、提供重构建议
                        2. **代码问答**：回答关于仓库中代码的任何问题
                        3. **文档生成**：自动生成 README、CHANGELOG、API 文档
                        4. **技术决策**：对比多种实现方案，给出推荐
                        5. **GitHub 远程仓库**：可通过 MCP 工具读取 GitHub 仓库的文件、提交历史和 PR
                        6. **代码分析工具**：可使用 search_code_files、count_code_lines、calculate_complexity、detect_code_smells 工具深入分析代码
                        7. **RAG 代码知识库**：使用 index_code_knowledge 索引仓库，使用 search_code_knowledge 进行语义检索
                        8. **长期记忆**：使用 record_to_memory 记住用户偏好和项目事实，使用 retrieve_from_memory 在对话开始时检索历史上下文

                        ## 子 Agent 调度

                        你有一个专家团队可以调度，通过 spawn_subagent 创建子 Agent：

                        - **SecurityAgent**：安全审查专家（SQL 注入/XSS/路径穿越/CVE）
                        - **PerformanceAgent**：性能分析专家（N+1 查询/内存泄漏/算法复杂度）
                        - **ArchitectureAgent**：架构评审专家（SOLID/设计模式/耦合度）

                        审查流程：
                        1. 先使用 search_code_files 和 index_code_knowledge 了解仓库结构
                        2. 根据任务类型，spawn 对应的专家子 Agent 进行专项分析
                        3. 汇总各专家的报告，形成最终审查结论

                        工作原则：
                        - 先理解代码全貌，再给出建议
                        - 对于代码审查任务，至少调度 2 个专家子 Agent 提供多维度分析
                        - 子 Agent 的审查结果可能包含独到见解，请整合而非简单拼接
                        - 推荐基于项目现有技术栈的方案，不要引入不必要的新依赖
                        - 代码修改前明确说明风险和影响范围
                        - 对话开始时，先调用 retrieve_from_memory 了解用户偏好和历史上下文
                        - 保持专业但友好的沟通风格
                        """)
                .model(defaultModel)
                .toolkit(toolkit)
                .subagent(securityAgent)
                .subagent(performanceAgent)
                .subagent(architectureAgent)
                .middleware(new CodingStandardsMiddleware(null, meterRegistry))
                .middleware(agentTraceMiddleware)
                .enablePlanMode()
                .maxIters(maxIters)
                .workspace(workspaceDir)
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                .compaction(CompactionConfig.builder()
                        .triggerMessages(triggerMessages)
                        .keepMessages(keepMessages)
                        .build());

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            builder.disableShellTool();
            log.info("检测到 Windows 环境，已禁用 shell execute 工具");
        }

        HarnessAgent agent = builder.build();
        log.info("Code Butler Agent 初始化完成: model={}, workspace={}", defaultModel, workspaceDir.toAbsolutePath());
        return agent;
    }

    private void preRegisterModels() {
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

            String toolsConfig = String.format("""
                    {
                      "mcpServers": {
                        "github": {
                          "transport": "stdio",
                          "command": "%s",
                          "args": ["-y", "@modelcontextprotocol/server-github"],
                          "env": {
                            "GITHUB_PERSONAL_ACCESS_TOKEN": "%s"
                          }
                        }
                      }
                    }
                    """, npxCommand, token);

            Files.writeString(toolsJson, toolsConfig);
            log.info("MCP tools.json 已生成: {}", toolsJson);

        } catch (IOException e) {
            log.warn("生成 MCP tools.json 失败: {}", e.getMessage());
        }
    }
}
