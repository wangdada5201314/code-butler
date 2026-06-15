-- ============================================================
-- 用户长期记忆表
-- 存储跨会话的用户偏好、关键决策、项目事实等长期记忆
-- 支持语义检索（向量相似度 + 关键词）
-- ============================================================

CREATE TABLE IF NOT EXISTS user_memory (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT '所属用户 ID',
    memory_type VARCHAR(32)  NOT NULL DEFAULT 'GENERAL' COMMENT '记忆类型：PREFERENCE/DECISION/FACT/HABIT/GENERAL',
    content     TEXT         NOT NULL COMMENT '记忆内容（自然语言）',
    summary     VARCHAR(512) DEFAULT NULL COMMENT '记忆摘要（用于列表展示）',
    embedding   JSON         DEFAULT NULL COMMENT '向量嵌入（JSON 数组，DashScope text-embedding-v3 1024 维）',
    metadata    JSON         DEFAULT NULL COMMENT '附加元数据（sessionId, repoPath, source 等）',
    ttl_days    INT          DEFAULT 90 COMMENT '存活天数，过期自动清理',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_user_id (user_id),
    INDEX idx_user_type (user_id, memory_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户长期记忆表';
