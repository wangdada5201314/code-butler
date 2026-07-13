package com.agent.codebutler.service;

import com.agent.codebutler.mapper.UserMemoryMapper;
import com.agent.codebutler.model.entity.UserMemoryEntity;
import com.agent.codebutler.util.VectorUtils;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 用户长期记忆服务
 * <p>
 * 支持：
 * - 记录记忆（自然语言 → 向量嵌入 → MySQL）
 * - 语义检索（查询向量化 → 余弦相似度 Top-K）
 * - 记忆管理（列表/更新/删除/过期清理）
 */
@Service
public class UserMemoryService {

    private static final Logger log = LoggerFactory.getLogger(UserMemoryService.class);

    private final UserMemoryMapper mapper;
    private final DashScopeEmbeddingService embedding;

    /** 内存缓存：user_id → 记忆列表（ConcurrentHashMap + 手动 LRU 淘汰） */
    private static final int MAX_CACHE_USERS = 100;
    private final Map<Long, List<CachedMemory>> cache = new ConcurrentHashMap<>();

    public UserMemoryService(UserMemoryMapper mapper, DashScopeEmbeddingService embedding) {
        this.mapper = mapper;
        this.embedding = embedding;
    }

    /**
     * 记录一条记忆
     *
     * @param userId     用户 ID
     * @param content    记忆内容（自然语言）
     * @param memoryType 记忆类型
     * @param summary    摘要（可选，null 则自动截取前 80 字）
     * @param metadata   元数据 JSON（可选）
     * @param ttlDays    存活天数
     */
    public UserMemoryEntity record(Long userId, String content, String memoryType,
                                    String summary, String metadata, int ttlDays) {
        // 向量化
        double[] vector;
        try {
            vector = embedding.embed(content);
        } catch (Exception e) {
            log.warn("记忆向量化失败，使用零向量: {}", e.getMessage());
            vector = new double[1024];
        }

        String embeddingJson = VectorUtils.vectorToJson(vector);
        String summaryText = (summary != null && !summary.isBlank())
                ? summary
                : (content.length() > 80 ? content.substring(0, 80) + "..." : content);

        UserMemoryEntity entity = UserMemoryEntity.builder()
                .userId(userId)
                .memoryType(memoryType)
                .content(content)
                .summary(summaryText)
                .embedding(embeddingJson)
                .metadata(metadata)
                .ttlDays(ttlDays)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        mapper.insert(entity);

        // 清除缓存
        cache.remove(userId);

        log.info("记忆已记录: userId={}, type={}, id={}, summary={}", userId, memoryType, entity.getId(), summaryText);
        return entity;
    }

    /**
     * 语义检索用户记忆
     *
     * @param userId 用户 ID
     * @param query  查询文本
     * @param limit  返回数量上限
     * @return 相关记忆列表（按相似度降序，过滤低于阈值的）
     */
    public List<SearchResult> search(Long userId, String query, int limit) {
        // 向量化查询
        double[] queryVector;
        try {
            queryVector = embedding.embed(query);
        } catch (Exception e) {
            log.warn("查询向量化失败: {}", e.getMessage());
            return Collections.emptyList();
        }

        // 加载用户记忆（缓存优先）
        List<CachedMemory> memories = loadUserMemories(userId);

        // 余弦相似度计算 + Top-K
        return memories.stream()
                .map(m -> new SearchResult(
                        m.entity,
                        VectorUtils.cosineSimilarity(queryVector, m.vector)))
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(limit)
                .filter(r -> r.score > 0.05)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户所有记忆列表（用于前端面板）
     */
    public List<UserMemoryEntity> listByUser(Long userId, String memoryType) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("create_time", false);

        if (memoryType != null && !memoryType.isBlank()) {
            qw.eq("memory_type", memoryType);
        }

        return mapper.selectListByQuery(qw);
    }

    /**
     * 删除单条记忆
     */
    public boolean delete(Long memoryId, Long userId) {
        UserMemoryEntity entity = mapper.selectOneById(memoryId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return false;
        }
        mapper.deleteById(memoryId);
        cache.remove(userId);
        return true;
    }

    /**
     * 更新记忆内容
     */
    public boolean update(Long memoryId, Long userId, String newContent) {
        UserMemoryEntity entity = mapper.selectOneById(memoryId);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return false;
        }
        entity.setContent(newContent);
        entity.setSummary(newContent.length() > 80 ? newContent.substring(0, 80) + "..." : newContent);

        // 重新向量化
        try {
            double[] vector = embedding.embed(newContent);
            entity.setEmbedding(VectorUtils.vectorToJson(vector));
        } catch (Exception e) {
            log.warn("更新记忆向量化失败: {}", e.getMessage());
        }

        entity.setUpdateTime(LocalDateTime.now());
        mapper.update(entity);
        cache.remove(userId);
        return true;
    }

    /**
     * 清理过期记忆（SQL 层面直接删除，避免全表加载到内存）
     */
    public int cleanExpired() {
        // 使用 SQL: WHERE DATE_ADD(create_time, INTERVAL ttl_days DAY) < NOW()
        QueryWrapper query = QueryWrapper.create()
                .where("DATE_ADD(create_time, INTERVAL ttl_days DAY) < NOW()");
        int deleted = mapper.deleteByQuery(query);
        if (deleted > 0) {
            cache.clear(); // 有数据被删除，清除所有缓存
            log.info("已清理 {} 条过期记忆", deleted);
        }
        return deleted;
    }

    // ---- 内部缓存逻辑 ----

    private List<CachedMemory> loadUserMemories(Long userId) {
        return cache.computeIfAbsent(userId, id -> {
            // 从 DB 加载（跳过 embedding 为 null 的条目）
            List<UserMemoryEntity> entities = mapper.selectListByQuery(
                    QueryWrapper.create()
                            .eq("user_id", id)
                            .isNotNull("embedding")
                            .orderBy("create_time", false));

            List<CachedMemory> result = entities.stream()
                    .map(e -> {
                        try {
                            double[] v = VectorUtils.jsonToVector(e.getEmbedding());
                            return new CachedMemory(e, v);
                        } catch (Exception ex) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 缓存超限淘汰：当缓存条目超过阈值时，移除一个非当前用户的条目
            evictIfNeeded(id);
            return result;
        });
    }

    /**
     * 缓存淘汰：当缓存用户数超过 MAX_CACHE_USERS 时，移除一个非当前用户的条目
     */
    private void evictIfNeeded(Long currentUserId) {
        if (cache.size() <= MAX_CACHE_USERS) return;
        // 移除任意一个非当前用户的条目（ConcurrentHashMap 迭代安全）
        for (Long key : cache.keySet()) {
            if (!key.equals(currentUserId)) {
                cache.remove(key);
                break;
            }
        }
    }

    private record CachedMemory(UserMemoryEntity entity, double[] vector) {}

    /**
     * 搜索结果（包含实体和相似度分数）
     */
    public record SearchResult(UserMemoryEntity entity, double score) {
        public String content() { return entity.getContent(); }
        public String summary() { return entity.getSummary(); }
        public String memoryType() { return entity.getMemoryType(); }
        public Long id() { return entity.getId(); }
    }
}
