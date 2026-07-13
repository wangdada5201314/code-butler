package com.agent.codebutler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 向量化配置属性
 * <p>
 * 映射 application.yml 中 embedding.* 配置块，
 * 替代 DashScopeEmbeddingService 中的 4 个 @Value 注入。
 * <p>
 * 支持任意 OpenAI 兼容的 Embedding API（DashScope / DeepSeek / OpenAI 等），
 * 不配置时自动 fallback: embedding.* > dashscope.* > openai.*
 */
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /** Embedding API 地址 */
    private String apiUrl;

    /** Embedding API Key */
    private String apiKey;

    /** Embedding 模型名称 */
    private String model = "text-embedding-v3";

    /** 向量维度 */
    private int dimensions = 1024;

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
}
