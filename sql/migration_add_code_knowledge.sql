-- ============================================================
-- RAG 代码知识库表
-- 存储代码分块及其向量嵌入，支持语义检索
-- ============================================================

CREATE TABLE IF NOT EXISTS code_knowledge (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    repoPath    VARCHAR(512) NOT NULL COMMENT '仓库路径',
    filePath    VARCHAR(512) NOT NULL COMMENT '文件相对路径',
    chunkId     VARCHAR(64)  DEFAULT NULL COMMENT '分块标识（如方法名、块序号）',
    content     TEXT         NOT NULL COMMENT '代码原文',
    language    VARCHAR(32)  DEFAULT NULL COMMENT '编程语言（Java/Python/JavaScript 等）',
    embedding   JSON         NOT NULL COMMENT '向量嵌入（JSON 数组，如 [0.01, -0.03, ...]）',
    createTime  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_repo_path (repoPath),
    INDEX idx_repo_file (repoPath, filePath(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='代码知识库（RAG 向量存储）';
