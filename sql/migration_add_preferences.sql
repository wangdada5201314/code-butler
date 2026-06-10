-- ============================================================
-- 增量迁移脚本：用户偏好配置 + 收藏仓库
-- 适用于已存在的数据库，执行以下 DDL 即可
--
-- 用法（Docker MySQL）：
--   docker exec -i code-butler-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-123456} code_butler < migration_add_preferences.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS user_preference (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    userId            BIGINT NOT NULL,
    reviewFocus       VARCHAR(512) DEFAULT '' COMMENT '审查关注点（逗号分隔）',
    reviewDepth       VARCHAR(32) DEFAULT 'standard' COMMENT '审查深度: detailed / standard / concise',
    customPrompt      TEXT COMMENT '自定义审查指令',
    createTime        DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updateTime        DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户偏好配置';

CREATE TABLE IF NOT EXISTS favorite_repo (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    userId        BIGINT NOT NULL,
    repoPath      VARCHAR(512) NOT NULL COMMENT '仓库绝对路径',
    repoName      VARCHAR(128) DEFAULT '' COMMENT '自定义显示名称',
    createTime    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE KEY uk_user_repo (userId, repoPath),
    INDEX idx_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏仓库';
