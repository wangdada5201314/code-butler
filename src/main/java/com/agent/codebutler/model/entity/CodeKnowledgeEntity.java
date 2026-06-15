package com.agent.codebutler.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代码知识片段实体（RAG 向量存储）
 * <p>
 * 存储代码分块及其向量嵌入，支持语义检索。
 * 每个 chunk 对应一个代码片段（方法/类/固定长度块），
 * embedding 字段以 JSON 数组格式存储 1024 维浮点向量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("code_knowledge")
public class CodeKnowledgeEntity implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("repoPath")
    private String repoPath;

    @Column("filePath")
    private String filePath;

    @Column("chunkId")
    private String chunkId;

    @Column("content")
    private String content;

    @Column("language")
    private String language;

    /** JSON 数组格式的向量嵌入，如 [0.01, -0.03, ...] */
    @Column("embedding")
    private String embedding;

    @Column(value = "createTime", onInsertValue = "now()")
    private LocalDateTime createTime;
}
