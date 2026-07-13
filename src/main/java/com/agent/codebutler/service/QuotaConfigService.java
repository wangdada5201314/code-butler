package com.agent.codebutler.service;

import com.agent.codebutler.mapper.QuotaConfigMapper;
import com.agent.codebutler.model.entity.QuotaConfig;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配额配置服务
 * <p>
 * 启动时从数据库加载所有配额配置并缓存，
 * 管理员可通过前端动态调整各操作类型的每日限额。
 */
@Service
public class QuotaConfigService {

    private static final Logger log = LoggerFactory.getLogger(QuotaConfigService.class);

    /** 内存缓存：opType -> dailyLimit（volatile 引用，reloadCache 时原子替换） */
    private volatile Map<String, Integer> limitCache = new ConcurrentHashMap<>();

    private final QuotaConfigMapper quotaConfigMapper;

    public QuotaConfigService(QuotaConfigMapper quotaConfigMapper) {
        this.quotaConfigMapper = quotaConfigMapper;
    }

    /**
     * 启动时加载所有配额配置到缓存
     */
    @PostConstruct
    public void init() {
        reloadCache();
        log.info("配额配置加载完成: {}", limitCache);
    }

    /**
     * 重新从数据库加载缓存（原子替换引用，避免 clear-then-populate 竞态）
     */
    public void reloadCache() {
        Map<String, Integer> newCache = new ConcurrentHashMap<>();
        List<QuotaConfig> configs = quotaConfigMapper.selectAll();
        for (QuotaConfig c : configs) {
            newCache.put(c.getOpType(), c.getDailyLimit());
        }
        this.limitCache = newCache; // volatile 写，原子可见
    }

    /**
     * 获取指定操作类型的每日限额
     *
     * @param opType REVIEW / CHAT / DOC
     * @return 每日限额，-1 表示不限
     */
    public int getDailyLimit(String opType) {
        return limitCache.getOrDefault(opType, -1);
    }

    /**
     * 获取所有配额配置（供管理员查看）
     */
    public List<QuotaConfig> getAllConfigs() {
        return quotaConfigMapper.selectAll();
    }

    /**
     * 更新指定操作类型的每日限额
     *
     * @param opType     操作类型
     * @param dailyLimit 新的每日限额（-1 表示不限）
     */
    public void updateDailyLimit(String opType, int dailyLimit) {
        QuotaConfig existing = quotaConfigMapper.selectOneByQuery(
                QueryWrapper.create().eq(QuotaConfig::getOpType, opType));

        if (existing != null) {
            existing.setDailyLimit(dailyLimit);
            existing.setUpdateTime(LocalDateTime.now());
            quotaConfigMapper.update(existing);
        } else {
            QuotaConfig config = QuotaConfig.builder()
                    .opType(opType)
                    .dailyLimit(dailyLimit)
                    .updateTime(LocalDateTime.now())
                    .build();
            quotaConfigMapper.insert(config);
        }

        // 更新缓存
        limitCache.put(opType, dailyLimit);
        log.info("配额已更新: opType={}, dailyLimit={}", opType, dailyLimit);
    }
}
