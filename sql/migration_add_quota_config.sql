-- ============================================================
-- 增量迁移脚本：配额配置表（管理员可动态调整每日限额）
--
-- 用法（Docker MySQL）：
--   docker exec -i code-butler-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-123456} code_butler < sql/migration_add_quota_config.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS quota_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    opType      VARCHAR(32) NOT NULL COMMENT '操作类型: REVIEW / CHAT / DOC',
    dailyLimit  INT NOT NULL DEFAULT -1 COMMENT '每日限额，-1 表示不限',
    description VARCHAR(128) DEFAULT '' COMMENT '配置描述',
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_opType (opType)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配额配置';

-- 初始数据（与 application.yml 默认值一致）
INSERT INTO quota_config (opType, dailyLimit, description) VALUES
('REVIEW', 20, '代码审查每日限额'),
('CHAT',   50, 'AI 问答每日限额（含智能问答 + 通用聊天）'),
('DOC',    20, '文档生成每日限额')
ON DUPLICATE KEY UPDATE dailyLimit = VALUES(dailyLimit);
