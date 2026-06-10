-- ============================================================
-- 迁移脚本：为已有数据库新增 operation_record 表
-- 
-- 使用方式：Docker 启动后，在 MySQL 客户端中执行：
--   docker exec -i code-butler-mysql mysql -uroot -p123456 code_butler < sql/migration_add_operation_record.sql
--
-- 或在任意 MySQL 客户端（如 Navicat / DBeaver / MySQL Workbench）中
-- 连接到 code_butler 数据库后直接执行此脚本。
-- ============================================================

CREATE TABLE IF NOT EXISTS operation_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    userId        BIGINT DEFAULT 0 NOT NULL,
    opType        VARCHAR(32) NOT NULL COMMENT '操作类型: REVIEW / CHAT / DOC',
    repoPath      VARCHAR(512) DEFAULT '' COMMENT '仓库路径',
    input         TEXT COMMENT '用户输入（提问内容 / 文档类型）',
    outputSummary TEXT COMMENT 'AI 输出摘要（前 500 字）',
    status        VARCHAR(16) DEFAULT 'COMPLETED' COMMENT '状态: COMPLETED / FAILED / TIMEOUT',
    durationMs    INT DEFAULT 0 COMMENT '耗时（毫秒）',
    sessionId     VARCHAR(64) COMMENT 'Agent 会话 ID',
    createTime    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_userId_createTime (userId, createTime),
    INDEX idx_opType (opType)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 操作历史记录';
