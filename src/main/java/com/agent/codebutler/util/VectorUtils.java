package com.agent.codebutler.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 向量数学与序列化工具类
 * <p>
 * 提供余弦相似度计算、向量与 JSON 互转等纯静态方法，
 * 被 RAG 知识库（CodeKnowledgeService）和用户记忆（UserMemoryService）共用。
 */
public final class VectorUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VectorUtils() {}

    /**
     * 计算两个向量的余弦相似度
     *
     * @return 0.0 ~ 1.0 之间的相似度，向量长度不匹配或为空时返回 0.0
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;
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
     * 将 double[] 向量序列化为 JSON 数组字符串
     */
    public static String vectorToJson(double[] vec) {
        if (vec == null || vec.length == 0) return "[]";
        try {
            return OBJECT_MAPPER.writeValueAsString(vec);
        } catch (JsonProcessingException e) {
            // 回退到手动构建
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < vec.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(vec[i]);
            }
            return sb.append("]").toString();
        }
    }

    /**
     * 将 JSON 数组字符串反序列化为 double[] 向量
     */
    public static double[] jsonToVector(String json) {
        if (json == null || json.isBlank()) return new double[0];
        try {
            return OBJECT_MAPPER.readValue(json, double[].class);
        } catch (Exception e) {
            // 回退到手动解析
            String[] parts = json.replaceAll("[\\[\\]\\s]", "").split(",");
            double[] vec = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    vec[i] = Double.parseDouble(parts[i].trim());
                } catch (NumberFormatException ex) {
                    vec[i] = 0.0;
                }
            }
            return vec;
        }
    }
}
