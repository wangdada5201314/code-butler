package com.agent.codebutler.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public HarnessAgent codeButlerAgent() {
        // 从 yml 读取的 Key 注册模型，这样不依赖环境变量
        preRegisterModels();

        Path workspaceDir = Paths.get(workspacePath);

        HarnessAgent agent = HarnessAgent.builder()
                .name("code-butler")
                .sysPrompt("""
                        你是一个专业的代码仓库智能管家（Code Butler），具备以下能力：

                        1. **代码审查**：分析代码质量、发现潜在 Bug、提供重构建议
                        2. **代码问答**：回答关于仓库中代码的任何问题
                        3. **文档生成**：自动生成 README、CHANGELOG、API 文档
                        4. **技术决策**：对比多种实现方案，给出推荐

                        工作原则：
                        - 先理解代码全貌，再给出建议
                        - 推荐基于项目现有技术栈的方案，不要引入不必要的新依赖
                        - 代码修改前明确说明风险和影响范围
                        - 保持专业但友好的沟通风格
                        """)
                .model(defaultModel)
                .workspace(workspaceDir)
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
}
