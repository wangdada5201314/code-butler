-- ============================================================
-- 增量迁移脚本：用量统计（tokenCount 列）
-- 适用于已存在的数据库，执行以下 DDL 即可
--
-- 用法（Docker MySQL）：
--   docker exec -i code-butler-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD:-123456} code_butler < sql/migration_add_token_count.sql
-- ============================================================

-- 为 operation_record 表新增 tokenCount 列
ALTER TABLE operation_record
    ADD COLUMN IF NOT EXISTS tokenCount INT DEFAULT 0 COMMENT '本次操作消耗的估算 token 数'
    AFTER durationMs;
