package com.agent.codebutler.tools;

import com.agent.codebutler.service.CodeKnowledgeService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RAG 知识检索工具 — 注册到 AgentScope Toolkit 供 Agent 调用
 * <p>
 * 包装 CodeKnowledgeService，提供 @Tool 注解方法，
 * Agent 在推理过程中可自主调用以检索相关代码片段。
 * <p>
 * 面试价值：展示 RAG (Retrieval-Augmented Generation) 完整链路 —
 * 索引 → 向量化 → 存储 → 检索 → 注入上下文，以及 Agent Tool 机制。
 */
public class KnowledgeRetrievalTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalTool.class);

    private final CodeKnowledgeService knowledgeService;

    public KnowledgeRetrievalTool(CodeKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Tool(name = "search_code_knowledge",
            description = "从代码知识库中语义检索相关代码片段。基于向量相似度的 RAG 检索，" +
                    "可以按自然语言描述查找相关的类、方法、配置等代码。" +
                    "使用前请确保仓库已被索引（通过 index_code_knowledge 工具）。")
    public String searchCodeKnowledge(
            @ToolParam(name = "repoPath", required = true,
                    description = "仓库根目录的绝对路径") String repoPath,
            @ToolParam(name = "query", required = true,
                    description = "自然语言查询，如'用户认证逻辑'、'数据库连接配置'、'异常处理'") String query) {

        log.info("[RAG Tool] 检索: repoPath={}, query={}", repoPath, query);
        return knowledgeService.searchAsTool(repoPath, query);
    }

    @Tool(name = "index_code_knowledge",
            description = "索引仓库的代码到知识库中，以便后续进行语义检索。" +
                    "首次使用 search_code_knowledge 前必须先调用此工具。" +
                    "会对仓库中的所有源代码文件进行分块、向量化并存储。")
    public String indexCodeKnowledge(
            @ToolParam(name = "repoPath", required = true,
                    description = "仓库根目录的绝对路径") String repoPath) {

        log.info("[RAG Tool] 索引仓库: {}", repoPath);
        try {
            CodeKnowledgeService.IndexResult result = knowledgeService.indexRepository(repoPath);
            return String.format("仓库索引完成！\n- 扫描文件: %d 个\n- 代码分块: %d 个\n- 成功索引: %d 个\n\n现在可以使用 search_code_knowledge 工具进行语义检索。",
                    result.totalFiles(), result.totalChunks(), result.indexedChunks());
        } catch (Exception e) {
            log.error("[RAG Tool] 索引失败: {}", e.getMessage(), e);
            return "索引失败: " + e.getMessage();
        }
    }

    @Tool(name = "get_knowledge_status",
            description = "查询仓库的代码知识库索引状态，包括是否已索引、索引的片段数量等。")
    public String getKnowledgeStatus(
            @ToolParam(name = "repoPath", required = true,
                    description = "仓库根目录的绝对路径") String repoPath) {

        CodeKnowledgeService.IndexStatus status = knowledgeService.getIndexStatus(repoPath);
        int chunkCount = knowledgeService.getChunkCount(repoPath);
        return String.format("仓库知识索引状态:\n- 阶段: %s\n- 索引片段数: %d\n- 索引文件数: %d",
                status.phase, chunkCount, status.totalFiles);
    }
}
