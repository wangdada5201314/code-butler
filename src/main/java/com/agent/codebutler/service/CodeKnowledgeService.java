package com.agent.codebutler.service;

import com.agent.codebutler.mapper.CodeKnowledgeMapper;
import com.agent.codebutler.model.entity.CodeKnowledgeEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.agent.codebutler.model.entity.table.CodeKnowledgeEntityTableDef.CODE_KNOWLEDGE_ENTITY;

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
 */
@Service
public class CodeKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(CodeKnowledgeService.class);

    /** 内存向量缓存: repoPath → List<KnowledgeChunk> */
    private final Map<String, List<KnowledgeChunk>> vectorCache = new ConcurrentHashMap<>();

    /** 索引状态追踪: repoPath → IndexStatus */
    private final Map<String, IndexStatus> indexStatusMap = new ConcurrentHashMap<>();

    private static final int CHUNK_SIZE = 1500;
    private static final int CHUNK_OVERLAP = 200;
    private static final int DEFAULT_TOP_K = 5;

    private static final Set<String> IGNORE_DIRS = Set.of(
            "node_modules", ".git", "target", "build", "dist", "out",
            ".idea", ".vscode", "__pycache__", ".gradle", "vendor",
            ".next", ".nuxt", "bin", "obj", ".mvn"
    );

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".go", ".rs",
            ".c", ".cpp", ".h", ".cs", ".rb", ".php", ".swift", ".kt",
            ".vue", ".html", ".css", ".scss", ".sql", ".yml", ".yaml",
            ".xml", ".json", ".sh", ".bash", ".md", ".properties"
    );

    /** 代码块边界正则（匹配方法/类/函数声明） */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "^\\s*(?:" +
                    "public|private|protected|static|final|abstract|synchronized|native|default" +
                    "|class|interface|enum|record" +
                    "|def|fn|func|function|async|export|import" +
                    ")\\s+",
            Pattern.MULTILINE
    );

    private final DashScopeEmbeddingService embeddingService;
    private final CodeKnowledgeMapper codeKnowledgeMapper;
    private final ObjectMapper objectMapper;

    public CodeKnowledgeService(DashScopeEmbeddingService embeddingService,
                                CodeKnowledgeMapper codeKnowledgeMapper) {
        this.embeddingService = embeddingService;
        this.codeKnowledgeMapper = codeKnowledgeMapper;
        this.objectMapper = new ObjectMapper();
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
        Path root = validateRepoPath(repoPath);
        if (root == null) {
            throw new IllegalArgumentException("无效的仓库路径: " + repoPath);
        }

        String normalizedPath = root.toString();
        IndexStatus status = new IndexStatus();
        indexStatusMap.put(normalizedPath, status);

        try {
            // 1. 扫描所有源代码文件
            status.phase = "扫描文件";
            List<Path> sourceFiles;
            try {
                sourceFiles = scanSourceFiles(root);
            } catch (IOException e) {
                throw new RuntimeException("扫描文件失败: " + e.getMessage(), e);
            }
            status.totalFiles = sourceFiles.size();
            log.info("[RAG] 开始索引仓库: {}, 找到 {} 个源文件", normalizedPath, sourceFiles.size());

            // 2. 代码分块
            status.phase = "代码分块";
            List<CodeChunk> chunks = new ArrayList<>();
            for (Path file : sourceFiles) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    String relativePath = root.relativize(file).toString().replace('\\', '/');
                    String language = detectLanguage(file);
                    List<CodeChunk> fileChunks = splitIntoChunks(content, relativePath, language);
                    chunks.addAll(fileChunks);
                } catch (Exception e) {
                    log.warn("[RAG] 跳过文件 {}: {}", file, e.getMessage());
                }
            }
            status.totalChunks = chunks.size();
            log.info("[RAG] 代码分块完成: {} 个 chunks", chunks.size());

            // 3. 批量 Embedding（每批最多 20 条，避免 API 超限）
            status.phase = "向量化";
            int batchSize = 20;
            List<double[]> allEmbeddings = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<String> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()))
                        .stream().map(c -> c.content).toList();
                try {
                    List<double[]> embeddings = embeddingService.batchEmbed(batch);
                    allEmbeddings.addAll(embeddings);
                    status.processedChunks = allEmbeddings.size();
                    log.info("[RAG] Embedding 进度: {}/{}", allEmbeddings.size(), chunks.size());
                } catch (Exception e) {
                    log.error("[RAG] Embedding 批次失败 (offset={}): {}", i, e.getMessage());
                    // 失败的批次用零向量占位，不影响其他批次
                    for (int j = 0; j < batch.size(); j++) {
                        allEmbeddings.add(new double[embeddingService.getDimensions()]);
                    }
                }
            }

            // 4. 持久化到 MySQL + 刷新内存缓存
            status.phase = "持久化";
            deleteByRepoPath(normalizedPath); // 清除旧数据

            List<KnowledgeChunk> knowledgeChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size() && i < allEmbeddings.size(); i++) {
                CodeChunk chunk = chunks.get(i);
                double[] embedding = allEmbeddings.get(i);

                CodeKnowledgeEntity entity = CodeKnowledgeEntity.builder()
                        .repoPath(normalizedPath)
                        .filePath(chunk.filePath)
                        .chunkId(chunk.chunkId)
                        .content(chunk.content)
                        .language(chunk.language)
                        .embedding(serializeEmbedding(embedding))
                        .build();
                codeKnowledgeMapper.insert(entity);

                knowledgeChunks.add(new KnowledgeChunk(
                        entity.getId(), chunk.filePath, chunk.chunkId,
                        chunk.content, chunk.language, embedding));
            }

            vectorCache.put(normalizedPath, knowledgeChunks);

            status.phase = "完成";
            status.indexedChunks = knowledgeChunks.size();
            log.info("[RAG] 仓库索引完成: {} — {} 文件, {} 分块",
                    normalizedPath, status.totalFiles, status.indexedChunks);

            return new IndexResult(status.totalFiles, status.totalChunks, status.indexedChunks);

        } finally {
            // 保留状态供查询，30 分钟后自动过期（由调用方决定）
            status.phase = status.phase.equals("完成") ? "已完成" : "失败";
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

            // 3. 缓存未命中 → 从 MySQL 加载
            if (chunks == null || chunks.isEmpty()) {
                chunks = loadFromDatabase(normalizedPath);
                if (!chunks.isEmpty()) {
                    vectorCache.put(normalizedPath, chunks);
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
                            chunk.language, DashScopeEmbeddingService.cosineSimilarity(queryVector, chunk.embedding)))
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
            status = new IndexStatus();
            status.phase = "未索引";
            // 查询数据库中是否有数据
            long count = codeKnowledgeMapper.selectCountByQuery(
                    QueryWrapper.create().where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(normalizedPath)));
            if (count > 0) {
                status.indexedChunks = (int) count;
                status.phase = "已索引（数据库）";
                // 从数据库统计去重文件数（totalFiles 仅在索引过程中实时设置，完成后未持久化）
                status.totalFiles = countDistinctFiles(normalizedPath);
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
        return (int) codeKnowledgeMapper.selectCountByQuery(
                QueryWrapper.create().where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(normalizedPath)));
    }

    // ════════════════════════════════════════════════════════
    //  内部方法 — 文件扫描与分块
    // ════════════════════════════════════════════════════════

    private List<Path> scanSourceFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return IGNORE_DIRS.contains(dir.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isSourceFile(file) && attrs.size() > 0 && attrs.size() < 500_000) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    /**
     * 将文件内容分割为代码块
     * <p>
     * 策略：优先按方法/类边界分割，超长块再按固定大小切分，保留重叠区域。
     */
    private List<CodeChunk> splitIntoChunks(String content, String filePath, String language) {
        List<CodeChunk> chunks = new ArrayList<>();

        if (content.isBlank()) return chunks;

        // 尝试按代码块边界分割
        List<String> blocks = splitByCodeBlocks(content);

        int chunkIndex = 0;
        for (String block : blocks) {
            if (block.length() > CHUNK_SIZE * 2) {
                // 超大块 → 按固定大小再切
                for (int i = 0; i < block.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                    int end = Math.min(i + CHUNK_SIZE, block.length());
                    String subChunk = block.substring(i, end);
                    if (subChunk.trim().length() > 30) { // 跳过过短的片段
                        chunks.add(new CodeChunk(filePath, "chunk_" + chunkIndex++, subChunk.trim(), language));
                    }
                }
            } else if (block.trim().length() > 30) {
                chunks.add(new CodeChunk(filePath, "chunk_" + chunkIndex++, block.trim(), language));
            }
        }

        // 兜底：如果按代码块分割失败（只产生了一个大块），使用固定大小分块
        if (chunks.size() <= 1 && content.length() > CHUNK_SIZE) {
            chunks.clear();
            chunkIndex = 0;
            for (int i = 0; i < content.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
                int end = Math.min(i + CHUNK_SIZE, content.length());
                String subChunk = content.substring(i, end);
                if (subChunk.trim().length() > 30) {
                    chunks.add(new CodeChunk(filePath, "chunk_" + chunkIndex++, subChunk.trim(), language));
                }
            }
        }

        return chunks;
    }

    /**
     * 按代码块边界（方法/类声明）分割文件内容
     */
    private List<String> splitByCodeBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);

        int lastEnd = 0;
        List<Integer> boundaries = new ArrayList<>();
        while (matcher.find()) {
            boundaries.add(matcher.start());
        }

        if (boundaries.isEmpty() || boundaries.get(0) > 100) {
            // 没有匹配到代码块边界，或第一个边界前有大量代码 → 整体作为一个块
            blocks.add(content);
            return blocks;
        }

        // 按边界切分
        for (int i = 0; i < boundaries.size(); i++) {
            int start = boundaries.get(i);
            int end = (i + 1 < boundaries.size()) ? boundaries.get(i + 1) : content.length();
            String block = content.substring(start, end);
            if (block.trim().length() > 30) {
                blocks.add(block);
            }
        }

        // 第一个边界前的内容（如 import 块、文件头注释）
        if (boundaries.get(0) > 50) {
            String header = content.substring(0, boundaries.get(0));
            if (header.trim().length() > 30) {
                blocks.add(0, header);
            }
        }

        return blocks;
    }

    // ════════════════════════════════════════════════════════
    //  内部方法 — 数据库操作
    // ════════════════════════════════════════════════════════

    /**
     * 从 MySQL 加载知识片段到内存
     */
    private List<KnowledgeChunk> loadFromDatabase(String repoPath) {
        List<CodeKnowledgeEntity> entities = codeKnowledgeMapper.selectListByQuery(
                QueryWrapper.create().where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(repoPath)));

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (CodeKnowledgeEntity entity : entities) {
            double[] embedding = parseEmbedding(entity.getEmbedding());
            chunks.add(new KnowledgeChunk(
                    entity.getId(), entity.getFilePath(), entity.getChunkId(),
                    entity.getContent(), entity.getLanguage(), embedding));
        }
        return chunks;
    }

    /**
     * 删除仓库的所有索引数据
     */
    private void deleteByRepoPath(String repoPath) {
        codeKnowledgeMapper.deleteByQuery(
                QueryWrapper.create().where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(repoPath)));
    }

    /**
     * 统计仓库在数据库中的去重文件数
     */
    private int countDistinctFiles(String repoPath) {
        List<CodeKnowledgeEntity> entities = codeKnowledgeMapper.selectListByQuery(
                QueryWrapper.create()
                        .select(CODE_KNOWLEDGE_ENTITY.FILE_PATH)
                        .where(CODE_KNOWLEDGE_ENTITY.REPO_PATH.eq(repoPath))
                        .groupBy(CODE_KNOWLEDGE_ENTITY.FILE_PATH));
        return entities.size();
    }

    // ════════════════════════════════════════════════════════
    //  内部方法 — 向量序列化/反序列化
    // ════════════════════════════════════════════════════════

    private String serializeEmbedding(double[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException e) {
            // 手动构建 JSON 数组
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < embedding.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(embedding[i]);
            }
            return sb.append("]").toString();
        }
    }

    private double[] parseEmbedding(String json) {
        try {
            return objectMapper.readValue(json, double[].class);
        } catch (Exception e) {
            log.warn("解析 embedding JSON 失败: {}", e.getMessage());
            return new double[0];
        }
    }

    // ════════════════════════════════════════════════════════
    //  内部方法 — 文件类型检测
    // ════════════════════════════════════════════════════════

    private boolean isSourceFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private String detectLanguage(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".java")) return "Java";
        if (name.endsWith(".py")) return "Python";
        if (name.endsWith(".js") || name.endsWith(".jsx")) return "JavaScript";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "TypeScript";
        if (name.endsWith(".go")) return "Go";
        if (name.endsWith(".rs")) return "Rust";
        if (name.endsWith(".c") || name.endsWith(".h")) return "C";
        if (name.endsWith(".cpp")) return "C++";
        if (name.endsWith(".cs")) return "C#";
        if (name.endsWith(".rb")) return "Ruby";
        if (name.endsWith(".php")) return "PHP";
        if (name.endsWith(".swift")) return "Swift";
        if (name.endsWith(".kt")) return "Kotlin";
        if (name.endsWith(".vue")) return "Vue";
        if (name.endsWith(".sql")) return "SQL";
        if (name.endsWith(".yml") || name.endsWith(".yaml")) return "YAML";
        if (name.endsWith(".xml")) return "XML";
        if (name.endsWith(".json")) return "JSON";
        if (name.endsWith(".md")) return "Markdown";
        if (name.endsWith(".properties")) return "Properties";
        return "Text";
    }

    private Path validateRepoPath(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) return null;
        try {
            Path path = Paths.get(repoPath).normalize().toAbsolutePath();
            return Files.isDirectory(path) ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  内部数据类
    // ════════════════════════════════════════════════════════

    /** 代码分块 */
    record CodeChunk(String filePath, String chunkId, String content, String language) {}

    /** 内存中的知识片段（含向量） */
    record KnowledgeChunk(Long id, String filePath, String chunkId, String content,
                          String language, double[] embedding) {}

    /** 检索结果 */
    public record SearchResult(String filePath, String chunkId, String content,
                               String language, double score) {}

    /** 索引结果统计 */
    public record IndexResult(int totalFiles, int totalChunks, int indexedChunks) {}

    /** 索引状态（可变对象，用于进度追踪） */
    public static class IndexStatus {
        public String phase = "未开始";
        public int totalFiles = 0;
        public int totalChunks = 0;
        public int processedChunks = 0;
        public int indexedChunks = 0;
    }
}
