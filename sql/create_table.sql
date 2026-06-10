-- Code Butler 用户表
-- 需要先创建数据库: CREATE DATABASE IF NOT EXISTS code_butler DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS user (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    userAccount  VARCHAR(256) NOT NULL,
    userPassword VARCHAR(512) NOT NULL,
    userName     VARCHAR(256) NULL,
    userAvatar   VARCHAR(1024) NULL,
    userRole     VARCHAR(256) DEFAULT 'user' NOT NULL,
    editTime     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    createTime   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updateTime   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    isDelete     TINYINT DEFAULT 0 NOT NULL,
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
);

-- 初始数据
-- 加密方式: MD5(password + "code-butler")
-- admin / user 密码均为 12345678
INSERT INTO user VALUES
(1, 'admin', '0489e82fd1e35d704975f8259259774d', '管理员', NULL, 'admin', NOW(), NOW(), NOW(), 0),
(2, 'user',  '0489e82fd1e35d704975f8259259774d', '普通用户', NULL, 'user', NOW(), NOW(), NOW(), 0);

-- ============================================================
-- 操作历史记录表 —— 记录用户的 AI 审查/问答/文档生成操作
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
