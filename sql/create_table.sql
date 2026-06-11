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
    tokenCount    INT DEFAULT 0 COMMENT '估算 token 消耗数',
    sessionId     VARCHAR(64) COMMENT 'Agent 会话 ID',
    createTime    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_userId_createTime (userId, createTime),
    INDEX idx_opType (opType)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 操作历史记录';

-- ============================================================
-- 用户偏好配置表 —— 存储每个用户的审查风格和自定义指令
-- ============================================================
CREATE TABLE IF NOT EXISTS user_preference (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    userId            BIGINT NOT NULL,
    reviewFocus       VARCHAR(512) DEFAULT '' COMMENT '审查关注点（逗号分隔）: naming,performance,security,architecture,readability',
    reviewDepth       VARCHAR(32) DEFAULT 'standard' COMMENT '审查深度: detailed / standard / concise',
    customPrompt      TEXT COMMENT '自定义审查指令（自由文本）',
    createTime        DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updateTime        DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户偏好配置';

-- ============================================================
-- 收藏仓库表 —— 用户常用的代码仓库快捷方式
-- ============================================================
CREATE TABLE IF NOT EXISTS favorite_repo (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    userId        BIGINT NOT NULL,
    repoPath      VARCHAR(512) NOT NULL COMMENT '仓库绝对路径',
    repoName      VARCHAR(128) DEFAULT '' COMMENT '自定义显示名称（可选）',
    createTime    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE KEY uk_user_repo (userId, repoPath),
    INDEX idx_userId (userId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏仓库';
