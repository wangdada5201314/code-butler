package com.agent.codebutler.service;

import com.agent.codebutler.util.FileScanConstants;
import com.agent.codebutler.util.VectorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 代码知识索引与检索服务（RAG 核心）
 * <p>
 * 架构：
 * <pre>
 * 索引阶段: 代码文件 → 分块(按方法/类) → DashScope Embedding → 向量 + 原文存 MySQL
 * 检索阶段: 用户提问 → Embedding → 余弦相似度 Top-K → 注入 prompt 上下文
 * </pre>
 * <p>
 * 内存向量缓存 + MySQL 持久化双层架构，启动时从 DB 预热缓存，
 * 后续查询直接走内存余弦相似度计算，毫秒级响应。
 * <p>
 * 职责拆分：
 * <ul>
 *     <li>{@link CodeChunker} — 代码分块逻辑</li>
 *     <li>{@link CodeKnowledgeRepository} — 数据库持久化</li>
 *     <li>本类 — 索引编排 + 语义检索 + 缓存管理</li>
 * </ul>
 */
@Service
public class CodeKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(CodeKnowledgeService.class);

    /** 内存向量缓存: repoPath → List<KnowledgeChunk>（LRU 淘汰，最多缓存 MAX_CACHED_REPOS 个仓库） */
    private final Map<String, List<KnowledgeChunk>> vectorCache = new ConcurrentHashMap<>();

    /** 最大缓存仓库数（超过后淘汰最早写入的缓存） */
    private static final int MAX_CACHED_REPOS = 10;

    /** 缓存写入顺序，用于 LRU 淘汰 */
    private final Deque<String> cacheAccessOrder = new java.util.concurrent.ConcurrentLinkedDeque<>();

    /** 索引状态追踪: repoPath → IndexStatus */
    private final Map<String, IndexStatus> indexStatusMap = new ConcurrentHashMap<>();

    private static final int DEFAULT_TOP_K = 5;

    private final DashScopeEmbeddingService embeddingService;
    private final CodeChunker codeChunker;
    private final CodeKnowledgeRepository knowledgeRepo;

    public CodeKnowledgeService(DashScopeEmbeddingService embeddingService,
                                CodeChunker codeChunker,
                                CodeKnowledgeRepository knowledgeRepo) {
        this.embeddingService = embeddingService;
        this.codeChunker = codeChunker;
        this.knowledgeRepo = knowledgeRepo;
    }

    // ════════════════════════════════════════════════════════
    //  索引编排
    // ════════════════════════════════════════════════════════

    /**
     * 索引仓库中的所有代码文件
     * <p>
     * 流程: 扫描文件 → 代码分块 → 批量 Embedding → 存 MySQL + 刷新内存缓存
     *
     * @param repoPath 仓库根目录的绝对路径
     * @return 索引统计信息
     */
    public IndexResult indexRepository(String repoPath) {
        Path root = FileScanConstants.validateRepoPath(repoPath);
        if (root == null) {
            throw new IllegalArgumentException("无效的仓库路径: " + repoPath);
        }

        String normalizedPath = root.toString();
        IndexStatus.Builder statusBuilder = new IndexStatus.Builder();
        indexStatusMap.put(normalizedPath, statusBuilder.build());

        try {
            // 1. 扫描所有源代码文件
            statusBuilder.phase("扫描文件");
            List<Path> sourceFiles;
            try {
                sourceFiles = FileScanConstants.scanSourceFiles(root, 0);
            } catch (IOException e) {
                throw new RuntimeException("扫描文件失败: " + e.getMessage(), e);
            }
            statusBuilder.totalFiles(sourceFiles.size());
            indexStatusMap.put(normalizedPath, statusBuilder.build());
            log.info("[RAG] 开始索引仓库: {}, 找到 {} 个源文件", normalizedPath, sourceFiles.size());

            // 2. 代码分块（委托 CodeChunker）
            statusBuilder.phase("代码分块");
            List<CodeChunker.CodeChunk> chunks = new ArrayList<>();
            for (Path file : sourceFiles) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    String relativePath = root.relativize(file).toString().replace('\\', '/');
                    String language = FileScanConstants.detectLanguage(file);
                    List<CodeChunker.CodeChunk> fileChunks = codeChunker.splitIntoChunks(content, relativePath, language);
                    chunks.addAll(fileChunks);
                } catch (Exception e) {
                    log.warn("[RAG] 跳过文件 {}: {}", file, e.getMessage());
                }
            }
            statusBuilder.totalChunks(chunks.size());
            indexStatusMap.put(normalizedPath, statusBuilder.build());
            log.info("[RAG] 代码分块完成: {} 个 chunks", chunks.size());

            // 3. 批量 Embedding（每批最多 20 条，避免 API 超限）
            statusBuilder.phase("向量化");
            int batchSize = 20;
            List<double[]> allEmbeddings = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<String> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()))
                        .stream().map(CodeChunker.CodeChunk::content).toList();
                try {
                    List<double[]> embeddings = embeddingService.batchEmbed(batch);
                    allEmbeddings.addAll(embeddings);
                    statusBuilder.processedChunks(allEmbeddings.size());
                    indexStatusMap.put(normalizedPath, statusBuilder.build());
                    log.info("[RAG] Embedding 进度: {}/{}", allEmbeddings.size(), chunks.size());
                } catch (Exception e) {
                    log.error("[RAG] Embedding 批次失败 (offset={}): {}", i, e.getMessage());
                    // 失败的批次用零向量占位，不影响其他批次
                    for (int j = 0; j < batch.size(); j++) {
                        allEmbeddings.add(new double[embeddingService.getDimensions()]);
                    }
                }
            }

            // 4. 持久化到 MySQL + 刷新内存缓存（委托 CodeKnowledgeRepository）
            statusBuilder.phase("持久化");
            indexStatusMap.put(normalizedPath, statusBuilder.build());
            knowledgeRepo.deleteByRepoPath(normalizedPath); // 清除旧数据

            List<KnowledgeChunk> knowledgeChunks = knowledgeRepo.saveChunks(normalizedPath, chunks, allEmbeddings);
            cacheAndEvictIfNeeded(normalizedPath, knowledgeChunks);

            statusBuilder.phase("完成").indexedChunks(knowledgeChunks.size());
            IndexStatus finalStatus = statusBuilder.build();
            indexStatusMap.put(normalizedPath, finalStatus);
            log.info("[RAG] 仓库索引完成: {} — {} 文件, {} 分块",
                    normalizedPath, finalStatus.getTotalFiles(), finalStatus.getIndexedChunks());

            return new IndexResult(finalStatus.getTotalFiles(), finalStatus.getTotalChunks(), finalStatus.getIndexedChunks());

        } catch (Exception e) {
            // 异常时标记为失败
            statusBuilder.phase("失败");
            indexStatusMap.put(normalizedPath, statusBuilder.build());
            throw e;
        } finally {
            // 将最终状态从"完成"改为"已完成"（仅成功时）
            IndexStatus current = indexStatusMap.get(normalizedPath);
            if (current != null && "完成".equals(current.getPhase())) {
                IndexStatus.Builder completedBuilder = new IndexStatus.Builder()
                        .phase("已完成")
                        .totalFiles(current.getTotalFiles())
                        .totalChunks(current.getTotalChunks())
                        .processedChunks(current.getProcessedChunks())
                        .indexedChunks(current.getIndexedChunks());
                indexStatusMap.put(normalizedPath, completedBuilder.build());
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  语义检索
    // ════════════════════════════════════════════════════════

    /**
     * 语义检索代码片段
     * <p>
     * 优先从内存缓存检索（毫秒级），缓存未命中时从 MySQL 加载并回填缓存。
     *
     * @param repoPath 仓库路径
     * @param query    自然语言查询
     * @param topK     返回结果数量
     * @return 按相似度排序的代码片段列表
     */
    public List<SearchResult> search(String repoPath, String query, int topK) {
        try {
            // 1. 查询向量化
            double[] queryVector = embeddingService.embed(query);
            if (queryVector.length == 0) {
                return List.of();
            }

            String normalizedPath = Paths.get(repoPath).normalize().toAbsolutePath().toString();

            // 2. 从内存缓存检索
            List<KnowledgeChunk> chunks = vectorCache.get(normalizedPath);

            // 3. 缓存未命中 → 从 MySQL 加载（委托 CodeKnowledgeRepository）
            if (chunks == null || chunks.isEmpty()) {
                chunks = knowledgeRepo.loadFromDatabase(normalizedPath);
                if (!chunks.isEmpty()) {
                    cacheAndEvictIfNeeded(normalizedPath, chunks);
                    log.info("[RAG] 从数据库加载 {} 个知识片段到缓存: {}", chunks.size(), normalizedPath);
                }
            }

            if (chunks.isEmpty()) {
                log.info("[RAG] 仓库尚未索引或无匹配: {}", normalizedPath);
                return List.of();
            }

            // 4. 余弦相似度 Top-K
            int k = topK > 0 ? topK : DEFAULT_TOP_K;
            return chunks.stream()
                    .map(chunk -> new SearchResult(
                            chunk.filePath, chunk.chunkId, chunk.content,
                            chunk.language, VectorUtils.cosineSimilarity(queryVector, chunk.embedding)))
                    .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                    .limit(k)
                    .filter(r -> r.score > 0.05) // 过滤低相关性结果
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[RAG] 检索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 语义检索（默认 Top-5）
     */
    public List<SearchResult> search(String repoPath, String query) {
        return search(repoPath, query, DEFAULT_TOP_K);
    }

    // ════════════════════════════════════════════════════════
    //  @Tool — Agent 可调用的知识检索工具
    // ════════════════════════════════════════════════════════

    /**
     * Agent 工具：语义搜索代码知识库
     * <p>
     * 由 KnowledgeRetrievalTool 包装后注册到 Toolkit，
     * Agent 在推理中自主调用，获取相关代码片段作为上下文。
     */
    public String searchAsTool(String repoPath, String query) {
        List<SearchResult> results = search(repoPath, query, 5);
        if (results.isEmpty()) {
            return "未在知识库中找到与 \"" + query + "\" 相关的代码片段。" +
                    "如果仓库尚未索引，请先使用 search_code_files 工具直接搜索代码。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(results.size()).append(" 个相关代码片段（RAG 语义检索）:\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("═══ 结果 ").append(i + 1);
            sb.append(" [").append(r.filePath);
            if (r.chunkId != null) sb.append(" / ").append(r.chunkId);
            sb.append("] (").append(r.language).append(", 相关性: ")
                    .append(String.format("%.2f", r.score)).append(") ═══\n");

            // 截取过长的内容
            String content = r.content;
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "\n... (已截断)";
            }
            sb.append(content).append("\n\n");
        }

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════
    //  状态查询
    // ════════════════════════════════════════════════════════

    /**
     * 获取仓库的索引状态
     */
    public IndexStatus getIndexStatus(String repoPath) {
        String normalizedPath = Paths.get(repoPath).normalize().toAbsolutePath().toString();
        IndexStatus status = indexStatusMap.get(normalizedPath);
        if (status == null) {
            // 查询数据库中是否有数据（委托 CodeKnowledgeRepository）
            int chunkCount = knowledgeRepo.getChunkCount(normalizedPath);
            if (chunkCount > 0) {
                status = new IndexStatus.Builder()
                        .phase("已索引（数据库）")
                        .indexedChunks(chunkCount)
                        .totalFiles(knowledgeRepo.countDistinctFiles(normalizedPath))
                        .build();
            } else {
                status = new IndexStatus.Builder().phase("未索引").build();
            }
        }
        return status;
    }

    /**
     * 获取仓库的索引片段数量
     */
    public int getChunkCount(String repoPath) {
        String normalizedPath = Paths.get(repoPath).normalize().toAbsolutePath().toString();
        List<KnowledgeChunk> cached = vectorCache.get(normalizedPath);
        if (cached != null) return cached.size();
        return knowledgeRepo.getChunkCount(normalizedPath);
    }

    // ════════════════════════════════════════════════════════
    //  缓存淘汰
    // ════════════════════════════════════════════════════════

    /**
     * 写入缓存并执行 LRU 淘汰（超过 MAX_CACHED_REPOS 时移除最早写入的缓存）
     */
    private void cacheAndEvictIfNeeded(String normalizedPath, List<KnowledgeChunk> chunks) {
        // 更新访问顺序（移除旧的再添加到尾部）
        cacheAccessOrder.remove(normalizedPath);
        cacheAccessOrder.addLast(normalizedPath);
        vectorCache.put(normalizedPath, chunks);

        // 淘汰最旧的缓存
        while (vectorCache.size() > MAX_CACHED_REPOS && !cacheAccessOrder.isEmpty()) {
            String evicted = cacheAccessOrder.pollFirst();
            if (evicted != null) {
                vectorCache.remove(evicted);
                log.info("[RAG] 缓存淘汰: {} (已缓存 {} 个仓库)", evicted, vectorCache.size());
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  内部数据类
    // ════════════════════════════════════════════════════════

    /** 内存中的知识片段（含向量） */
    public record KnowledgeChunk(Long id, String filePath, String chunkId, String content,
                          String language, double[] embedding) {}

    /** 检索结果 */
    public record SearchResult(String filePath, String chunkId, String content,
                               String language, double score) {}

    /** 索引结果统计 */
    public record IndexResult(int totalFiles, int totalChunks, int indexedChunks) {}

    /** 索引状态（不可变快照，线程安全） */
    public static class IndexStatus {
        private final String phase;
        private final int totalFiles;
        private final int totalChunks;
        private final int processedChunks;
        private final int indexedChunks;

        private IndexStatus(Builder builder) {
            this.phase = builder.phase;
            this.totalFiles = builder.totalFiles;
            this.totalChunks = builder.totalChunks;
            this.processedChunks = builder.processedChunks;
            this.indexedChunks = builder.indexedChunks;
        }

        /** 默认构造（未开始状态） */
        public IndexStatus() {
            this(new Builder());
        }

        public String getPhase() { return phase; }
        public int getTotalFiles() { return totalFiles; }
        public int getTotalChunks() { return totalChunks; }
        public int getProcessedChunks() { return processedChunks; }
        public int getIndexedChunks() { return indexedChunks; }

        /** 可变构建器，用于索引过程中的渐进式状态更新 */
        public static class Builder {
            private String phase = "未开始";
            private int totalFiles = 0;
            private int totalChunks = 0;
            private int processedChunks = 0;
            private int indexedChunks = 0;

            public Builder phase(String phase) { this.phase = phase; return this; }
            public Builder totalFiles(int totalFiles) { this.totalFiles = totalFiles; return this; }
            public Builder totalChunks(int totalChunks) { this.totalChunks = totalChunks; return this; }
            public Builder processedChunks(int processedChunks) { this.processedChunks = processedChunks; return this; }
            public Builder indexedChunks(int indexedChunks) { this.indexedChunks = indexedChunks; return this; }

            public IndexStatus build() { return new IndexStatus(this); }
        }
    }
}
