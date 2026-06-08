package com.agent.codebutler.config;

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

    @Bean
    public HarnessAgent codeButlerAgent() {
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
}
