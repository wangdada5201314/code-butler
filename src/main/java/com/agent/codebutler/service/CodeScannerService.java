package com.agent.codebutler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 代码文件扫描服务
 * 带简单缓存机制，避免对同一仓库重复扫描
 */
@Service
public class CodeScannerService {

    private static final Logger log = LoggerFactory.getLogger(CodeScannerService.class);

    // 代码文件扩展名
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".py", ".pyi", ".js", ".ts", ".jsx", ".tsx",
            ".go", ".rs", ".c", ".cpp", ".cc", ".h", ".hpp",
            ".xml", ".yml", ".yaml", ".json", ".properties", ".toml",
            ".sql", ".sh", ".bat", ".ps1",
            ".md", ".txt", ".rst"
    );

    // 忽略的目录名
    private static final Set<String> IGNORE_DIRS = Set.of(
            "node_modules", ".git", ".idea", "target", "build",
            "__pycache__", ".venv", "venv", "dist", ".gradle",
            ".workbuddy", ".agentscope", "out"
    );

    /** 缓存：repoPath -> 扫描结果 */
    private final Map<String, CachedScan> cache = new ConcurrentHashMap<>();

    /** 缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 30_000;

    /** 最大扫描文件数（防止超大仓库 OOM） */
    private static final int MAX_SCAN_FILES = 5_000;

    /**
     * 扫描仓库中的代码文件（带缓存）
     */
    public List<Path> scanCodeFiles(String repoPath) throws IOException {
        Path root = Paths.get(repoPath);
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }

        // 检查缓存
        CachedScan cached = cache.get(repoPath);
        if (cached != null && !cached.isExpired()) {
            return cached.files;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.walk(root).parallel()) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isCodeFile)
                    .filter(this::isNotIgnoredDir)
                    .limit(MAX_SCAN_FILES)
                    .collect(Collectors.toList());
        }

        cache.put(repoPath, new CachedScan(files, System.currentTimeMillis()));
        return files;
    }

    /**
     * 获取仓库概况（文件树和语言统计）
     */
    public String getRepoOverview(String repoPath) throws IOException {
        GitService.validateRepoPath(repoPath);

        List<Path> files = scanCodeFiles(repoPath);

        // 按语言分类统计
        Map<String, Long> languageCount = files.stream()
                .collect(Collectors.groupingBy(this::getLanguage, Collectors.counting()));

        // 构建概述文本
        StringBuilder sb = new StringBuilder();
        sb.append("仓库路径：").append(repoPath).append("\n");
        sb.append("代码文件总数：").append(files.size()).append(" 个\n\n");
        sb.append("语言分布：\n");

        // 按数量降序排列
        languageCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry ->
                        sb.append("  - ").append(entry.getKey())
                                .append(": ").append(entry.getValue()).append(" 个文件\n"));

        // 列出主要目录（前两层的目录树）
        sb.append("\n目录结构（前两层）：\n");
        sb.append(buildTree(repoPath, 2));

        log.debug("仓库概览生成完成: {} 个文件, {} 种语言", files.size(), languageCount.size());
        return sb.toString();
    }

    /**
     * 读取指定文件的内容并生成上下文
     */
    public String readFileContext(String repoPath, List<Path> files) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path file : files) {
            try {
                String content = Files.readString(file);
                sb.append("\n===== ").append(file.getFileName()).append(" =====\n");
                sb.append(content).append("\n");
            } catch (IOException e) {
                log.warn("读取文件失败: {}", file, e);
                sb.append("\n===== ").append(file.getFileName()).append(" =====\n");
                sb.append("[读取失败: ").append(e.getMessage()).append("]\n");
            }
        }
        return sb.toString();
    }

    /**
     * 清除缓存（外部调用，如仓库变更后）
     */
    public void clearCache(String repoPath) {
        cache.remove(repoPath);
        log.debug("缓存已清除: {}", repoPath);
    }

    // ---- 内部方法 ----

    private boolean isCodeFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        // 处理复合后缀：.service.ts -> 先看完整后缀
        for (String ext : CODE_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotIgnoredDir(Path path) {
        for (int i = 0; i < path.getNameCount(); i++) {
            if (IGNORE_DIRS.contains(path.getName(i).toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 改进的语言识别：按优先级精确匹配
     */
    private String getLanguage(Path path) {
        String name = path.getFileName().toString().toLowerCase();

        if (name.endsWith(".java")) return "Java";
        if (name.endsWith(".kt") || name.endsWith(".kts")) return "Kotlin";
        if (name.endsWith(".py") || name.endsWith(".pyi")) return "Python";
        if (name.endsWith(".tsx")) return "TypeScript/React";
        if (name.endsWith(".jsx")) return "JavaScript/React";
        if (name.endsWith(".ts")) return "TypeScript";
        if (name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".cjs")) return "JavaScript";
        if (name.endsWith(".go")) return "Go";
        if (name.endsWith(".rs")) return "Rust";
        if (name.endsWith(".c") || name.endsWith(".h")) return "C";
        if (name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".hpp")) return "C++";
        if (name.endsWith(".sql")) return "SQL";
        if (name.endsWith(".yml") || name.endsWith(".yaml")) return "YAML";
        if (name.endsWith(".xml")) return "XML";
        if (name.endsWith(".json")) return "JSON";
        if (name.endsWith(".properties")) return "Properties";
        if (name.endsWith(".toml")) return "TOML";
        if (name.endsWith(".sh") || name.endsWith(".bash")) return "Shell";
        if (name.endsWith(".bat") || name.endsWith(".ps1")) return "Script";
        if (name.endsWith(".md") || name.endsWith(".rst") || name.endsWith(".txt")) return "Document";

        return "Other";
    }

    /**
     * 构建目录树（前 N 层）
     */
    private String buildTree(String rootPath, int maxDepth) throws IOException {
        Path root = Paths.get(rootPath);
        StringBuilder sb = new StringBuilder();
        Set<String> ignoreDirsLower = IGNORE_DIRS.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.equals(root))
                    .filter(p -> {
                        String dirName = p.getFileName().toString().toLowerCase();
                        return !ignoreDirsLower.contains(dirName);
                    })
                    .sorted()
                    .forEach(dir -> {
                        int depth = root.relativize(dir).getNameCount();
                        sb.append("  ".repeat(depth))
                                .append("|- ").append(dir.getFileName()).append("\n");
                    });
        }
        return sb.toString();
    }

    // ---- 缓存内部类 ----

    private static class CachedScan {
        final List<Path> files;
        final long timestamp;

        CachedScan(List<Path> files, long timestamp) {
            this.files = List.copyOf(files); // 不可变
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
