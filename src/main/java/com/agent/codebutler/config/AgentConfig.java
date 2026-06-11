package com.agent.codebutler.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
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

    // ---- API Key 配置（可从 yml 读取） ----

    @Value("${dashscope.api-key:#{null}}")
    private String dashscopeApiKey;

    @Value("${openai.api-key:#{null}}")
    private String openaiApiKey;

    @Value("${openai.base-url:#{null}}")
    private String openaiBaseUrl;

    @Value("${github.token:}")
    private String githubToken;

    /**
     * 清理 Agent 持久化状态文件。
     * <p>
     * AgentScope 会将 Agent 状态（包括权限模式）序列化到 agent_state.json。
     * 当 Builder 中的权限设置更新后（如从 DEFAULT 改为 BYPASS），旧的状态文件
     * 会覆盖 Builder 设置，导致权限模式不生效。
     * 每次启动时清理状态文件，确保使用 Builder 中的最新配置。
     */
    private void cleanAgentState(Path workspaceDir) {
        try {
            // AgentScope 状态路径: {workspace}/agents/{agentName}/context/{agentName}/agent_state.json
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
    public HarnessAgent codeButlerAgent() {
        // 从 yml 读取的 Key 注册模型，这样不依赖环境变量
        preRegisterModels();

        Path workspaceDir = Paths.get(workspacePath);

        // 清理旧状态文件，防止持久化的权限模式覆盖 Builder 设置
        cleanAgentState(workspaceDir);

        // 确保 workspace 目录和 MCP tools.json 存在
        ensureWorkspaceToolsConfig(workspaceDir);

        HarnessAgent agent = HarnessAgent.builder()
                .name("code-butler")
                .sysPrompt("""
                        你是一个专业的代码仓库智能管家（Code Butler），具备以下能力：

                        1. **代码审查**：分析代码质量、发现潜在 Bug、提供重构建议
                        2. **代码问答**：回答关于仓库中代码的任何问题
                        3. **文档生成**：自动生成 README、CHANGELOG、API 文档
                        4. **技术决策**：对比多种实现方案，给出推荐
                        5. **GitHub 远程仓库**：可通过 MCP 工具读取 GitHub 仓库的文件、提交历史和 PR

                        工作原则：
                        - 先理解代码全貌，再给出建议
                        - 对于 GitHub 仓库，使用 MCP 工具列出目录结构、读取关键文件，至少浏览 5-8 个重要源文件
                        - 推荐基于项目现有技术栈的方案，不要引入不必要的新依赖
                        - 代码修改前明确说明风险和影响范围
                        - 保持专业但友好的沟通风格
                        """)
                .model(defaultModel)
                .workspace(workspaceDir)
                // BYPASS 模式：自动批准所有工具调用（包括 MCP），无需人工确认
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                .compaction(CompactionConfig.builder()
                        .triggerMessages(triggerMessages)
                        .keepMessages(keepMessages)
                        .build())
                .build();

        log.info("Code Butler Agent 初始化完成: model={}, workspace={}", defaultModel, workspaceDir.toAbsolutePath());
        return agent;
    }

    /**
     * 将 yml 中的 API Key 注册为具名模型，
     * 这样 ModelRegistry.resolve() 直接命中，不会走到需要环境变量的工厂路径。
     * 根据 defaultModel 的前缀决定注册哪个 Provider，避免重复注册同一 tag。
     */
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
                var builder = OpenAIChatModel.builder()
                        .apiKey(openaiApiKey)
                        .modelName(modelName)
                        .stream(true);

                if (openaiBaseUrl != null && !openaiBaseUrl.isBlank()) {
                    builder.baseUrl(openaiBaseUrl);
                }

                ModelRegistry.register(defaultModel, builder.build());
                log.info("OpenAI 兼容模型已从 yml 注册: {} (baseUrl={})",
                        defaultModel, openaiBaseUrl != null ? openaiBaseUrl : "默认");
            } else {
                log.warn("defaultModel 配置为 openai: 前缀，但未配置 openai.api-key");
            }
        } else {
            log.warn("defaultModel 前缀无法识别（应为 dashscope: 或 openai:）: {}", defaultModel);
        }
    }

    /**
     * 从 "provider:model-name" 中提取 model-name 部分
     */
    private static String extractModelName(String modelTag) {
        int idx = modelTag.indexOf(':');
        return idx >= 0 ? modelTag.substring(idx + 1) : modelTag;
    }

    /**
     * 确保 workspace 目录存在，并自动生成 MCP tools.json。
     * <p>
     * GitHub Token 优先使用 GITHUB_TOKEN 环境变量，其次使用 yml 中的 github.token。
     * Token 直接写入 tools.json（workspace 目录已在 .gitignore 中，不会被提交）。
     * 每次启动都会重新生成，确保配置与 yml/环境变量保持同步。
     */
    private void ensureWorkspaceToolsConfig(Path workspaceDir) {
        try {
            Files.createDirectories(workspaceDir);

            Path toolsJson = workspaceDir.resolve("tools.json");

            // Token 优先级：环境变量 > yml 配置
            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank()) {
                token = githubToken;
            }

            if (token == null || token.isBlank()) {
                log.warn("GitHub Token 未配置（环境变量 GITHUB_TOKEN 或 yml github.token），GitHub MCP 功能不可用");
                // 不生成 tools.json，避免 MCP 启动失败
                // 如果之前有旧的 tools.json，删除以避免空 token 启动报错
                if (Files.exists(toolsJson)) {
                    Files.delete(toolsJson);
                    log.info("已删除旧的 tools.json（Token 未配置）");
                }
                return;
            }

            log.info("GitHub Token 已配置，MCP 远程仓库审查功能可用");

            // Windows 上 Java ProcessBuilder 无法直接运行 npx，需要 npx.cmd
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
            log.warn("生成 MCP tools.json 失败，GitHub MCP 功能将不可用: {}", e.getMessage());
        }
    }
}
