package com.agent.codebutler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量化服务（支持任意 OpenAI 兼容 Embedding API）
 * <p>
 * 自动适配多种提供商：
 * 1. 优先使用 embedding.* 专属配置（url / key / model）
 * 2. 其次使用 dashscope.* 配置（DashScope text-embedding-v3）
 * 3. 最后使用 openai.* 配置（DeepSeek / OpenAI 等兼容接口）
 * <p>
 * 请求格式统一为 OpenAI 兼容标准，所有主流提供商均支持。
 */
@Service
public class DashScopeEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingService.class);

    private static final String DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

    /** Embedding API 地址（优先读取） */
    @Value("${embedding.api-url:#{null}}")
    private String apiUrl;

    /** Embedding API Key（优先读取） */
    @Value("${embedding.api-key:#{null}}")
    private String apiKey;

    @Value("${embedding.model:text-embedding-v3}")
    private String model;

    @Value("${embedding.dimensions:1024}")
    private int dimensions;

    /** DashScope 备用 Key */
    @Value("${dashscope.api-key:#{null}}")
    private String dashscopeApiKey;

    /** OpenAI 兼容备用 Key / URL */
    @Value("${openai.api-key:#{null}}")
    private String openaiApiKey;

    @Value("${openai.base-url:#{null}}")
    private String openaiBaseUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DashScopeEmbeddingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 将单条文本转换为向量
     *
     * @param text 待向量化的文本
     * @return 1024 维浮点向量
     */
    public double[] embed(String text) {
        List<double[]> results = batchEmbed(List.of(text));
        return results.isEmpty() ? new double[0] : results.get(0);
    }

    /**
     * 批量将文本转换为向量（单次最多 25 条）
     *
     * @param texts 文本列表
     * @return 向量列表，顺序与输入一致
     */
    public List<double[]> batchEmbed(List<String> texts) {
        // 动态解析 API 地址和 Key（支持多提供商）
        String resolvedUrl = resolveApiUrl();
        String resolvedKey = resolveApiKey();

        if (resolvedKey == null || resolvedKey.isBlank()) {
            throw new IllegalStateException(
                    "Embedding API Key 未配置。请设置以下任一配置：embedding.api-key / dashscope.api-key / openai.api-key");
        }
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            throw new IllegalStateException(
                    "Embedding API URL 未配置。请设置 embedding.api-url 或 openai.base-url");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            log.info("[Embedding] 调用: url={}, model={}, dimensions={}, batchSize={}",
                    resolvedUrl, model, dimensions, texts.size());

            // 构建请求体（OpenAI 兼容格式）
            var requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            // dimensions 参数仅部分提供商支持（DashScope/OpenAI），DeepSeek 不支持
            if (dimensions > 0 && !resolvedUrl.contains("deepseek")) {
                requestBody.put("dimensions", dimensions);
            }
            var inputArray = requestBody.putArray("input");
            for (String text : texts) {
                inputArray.add(text);
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolvedUrl))
                    .header("Authorization", "Bearer " + resolvedKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[Embedding] API 调用失败: url={}, status={}, body={}",
                        resolvedUrl, response.statusCode(), response.body());
                throw new RuntimeException("Embedding API 调用失败，状态码: " + response.statusCode());
            }

            // 解析响应
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode dataArray = root.get("data");

            List<double[]> embeddings = new ArrayList<>();
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode item : dataArray) {
                    JsonNode embeddingArray = item.get("embedding");
                    if (embeddingArray != null) {
                        double[] vec = new double[embeddingArray.size()];
                        for (int i = 0; i < embeddingArray.size(); i++) {
                            vec[i] = embeddingArray.get(i).asDouble();
                        }
                        embeddings.add(vec);
                    }
                }
            }

            // 记录 token 消耗
            JsonNode usage = root.get("usage");
            if (usage != null) {
                int tokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0;
                log.info("Embedding 完成: {} 条文本, {} tokens, {} 维", texts.size(), tokens, dimensions);
            }

            return embeddings;

        } catch (Exception e) {
            log.error("DashScope Embedding 调用异常: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算两个向量的余弦相似度
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }

    /**
     * 向量 → JSON 数组字符串
     */
    public static String vectorToJson(double[] vec) {
        if (vec == null || vec.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6f", vec[i]));
        }
        return sb.append("]").toString();
    }

    /**
     * JSON 数组字符串 → 向量
     */
    public static double[] jsonToVector(String json) {
        if (json == null || json.isBlank()) return new double[0];
        // 简单解析 [0.01, -0.03, ...]
        String[] parts = json.replaceAll("[\\[\\]\\s]", "").split(",");
        double[] vec = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vec[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                vec[i] = 0.0;
            }
        }
        return vec;
    }

    public String getModel() {
        return model;
    }

    public int getDimensions() {
        return dimensions;
    }

    public boolean isConfigured() {
        return resolveApiKey() != null && !resolveApiKey().isBlank();
    }

    /**
     * 解析 Embedding API URL
     * 优先级: embedding.api-url > dashscope 默认 > openai.base-url
     */
    private String resolveApiUrl() {
        // 1. 专属配置
        if (apiUrl != null && !apiUrl.isBlank()) {
            return apiUrl;
        }
        // 2. DashScope
        if (dashscopeApiKey != null && !dashscopeApiKey.isBlank()) {
            return DASHSCOPE_URL;
        }
        // 3. OpenAI 兼容（DeepSeek 等）— 拼接 /embeddings，处理有无 /v1 后缀
        if (openaiBaseUrl != null && !openaiBaseUrl.isBlank()) {
            String base = openaiBaseUrl.replaceAll("/+$", "");
            if (!base.endsWith("/v1")) {
                base = base + "/v1";
            }
            return base + "/embeddings";
        }
        return null;
    }

    /**
     * 解析 Embedding API Key
     * 优先级: embedding.api-key > dashscope.api-key > openai.api-key
     */
    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) return apiKey;
        if (dashscopeApiKey != null && !dashscopeApiKey.isBlank()) return dashscopeApiKey;
        if (openaiApiKey != null && !openaiApiKey.isBlank()) return openaiApiKey;
        return null;
    }
}
