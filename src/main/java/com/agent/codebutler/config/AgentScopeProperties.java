package com.agent.codebutler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentScope 框架配置属性
 * <p>
 * 映射 application.yml 中 agentscope.* 配置块，
 * 替代散落在 AgentConfig / ChatService / CodeReviewService / DocGenerationService / GeneralChatService
 * 中的 10+ 个 @Value 注入。
 */
@ConfigurationProperties(prefix = "agentscope")
public class AgentScopeProperties {

    private final Model model = new Model();

    /** Agent 单次调用超时秒数（默认 120s） */
    private int callTimeoutSeconds = 120;

    private final Workspace workspace = new Workspace();

    private final Compaction compaction = new Compaction();

    /** Agent 最大迭代次数（默认 25） */
    private int maxIters = 25;

    private final State state = new State();

    /** 子 Agent（专家）声明列表 */
    private List<Subagent> subagents = initDefaultSubagents();

    // ── Getters & Setters ──

    public Model getModel() { return model; }
    public int getCallTimeoutSeconds() { return callTimeoutSeconds; }
    public void setCallTimeoutSeconds(int callTimeoutSeconds) { this.callTimeoutSeconds = callTimeoutSeconds; }
    public Workspace getWorkspace() { return workspace; }
    public Compaction getCompaction() { return compaction; }
    public int getMaxIters() { return maxIters; }
    public void setMaxIters(int maxIters) { this.maxIters = maxIters; }
    public State getState() { return state; }
    public List<Subagent> getSubagents() { return subagents; }
    public void setSubagents(List<Subagent> subagents) { this.subagents = subagents; }

    // ── 嵌套配置 ──

    public static class Model {
        /** 默认模型标识（格式: provider:model-name） */
        private String defaultValue = "dashscope:qwen-plus";

        /** 备用模型标识（主模型失败时自动切换） */
        private String fallback;

        /** 主模型最大重试次数（默认 3） */
        private int maxRetries = 3;

        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        /** 兼容 yml 中 model.default（Spring 绑定 default → defaultValue 需要显式 alias） */
        public void setDefault(String defaultValue) { this.defaultValue = defaultValue; }
        public String getFallback() { return fallback; }
        public void setFallback(String fallback) { this.fallback = fallback; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public static class Workspace {
        /** Agent 工作空间目录 */
        private String path = ".agentscope/workspace";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    public static class Compaction {
        /** 触发对话压缩的消息数阈值 */
        private int triggerMessages = 30;
        /** 压缩后保留的最近消息数 */
        private int keepMessages = 10;

        public int getTriggerMessages() { return triggerMessages; }
        public void setTriggerMessages(int triggerMessages) { this.triggerMessages = triggerMessages; }
        public int getKeepMessages() { return keepMessages; }
        public void setKeepMessages(int keepMessages) { this.keepMessages = keepMessages; }
    }

    public static class State {
        /** 启动时是否清理 Agent 持久化状态文件 */
        private boolean cleanupOnStart = false;

        public boolean isCleanupOnStart() { return cleanupOnStart; }
        public void setCleanupOnStart(boolean cleanupOnStart) { this.cleanupOnStart = cleanupOnStart; }
    }

    /**
     * 子 Agent 声明配置
     * <p>
     * 在 application.yml 中通过 agentscope.subagents 列表配置，
     * 未配置时使用默认的 SecurityAgent / PerformanceAgent / ArchitectureAgent。
     */
    public static class Subagent {
        /** 子 Agent 名称（唯一标识） */
        private String name;
        /** 子 Agent 描述（注入到主 Agent 的系统提示词中） */
        private String description;
        /** 子 Agent 最大迭代次数 */
        private int maxIters = 12;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getMaxIters() { return maxIters; }
        public void setMaxIters(int maxIters) { this.maxIters = maxIters; }
    }

    /**
     * 初始化默认的子 Agent 列表（与硬编码版本一致）
     */
    private static List<Subagent> initDefaultSubagents() {
        List<Subagent> defaults = new ArrayList<>();

        Subagent security = new Subagent();
        security.setName("SecurityAgent");
        security.setDescription("安全审查专家。专注检测安全漏洞：SQL 注入、XSS、路径穿越、硬编码密钥、"
                + "不安全的加密算法、CVE 已知漏洞。审查完成后输出按严重程度排序的漏洞清单。");
        security.setMaxIters(12);
        defaults.add(security);

        Subagent performance = new Subagent();
        performance.setName("PerformanceAgent");
        performance.setDescription("性能分析专家。专注识别性能瓶颈：N+1 查询、内存泄漏风险、"
                + "不必要的对象创建、O(n²) 算法、IO 阻塞、锁竞争。输出优化建议和预估收益。");
        performance.setMaxIters(10);
        defaults.add(performance);

        Subagent architecture = new Subagent();
        architecture.setName("ArchitectureAgent");
        architecture.setDescription("架构评审专家。从设计模式、SOLID 原则、模块耦合度、分层合规性、"
                + "可扩展性角度审查代码架构。识别反模式并给出重构路线图。");
        architecture.setMaxIters(15);
        defaults.add(architecture);

        return defaults;
    }
}
