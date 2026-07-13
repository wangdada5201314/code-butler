package com.agent.codebutler.service;

import com.agent.codebutler.util.FileScanConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
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

    /** 缓存：repoPath -> 扫描结果 */
    private final Map<String, CachedScan> cache = new ConcurrentHashMap<>();

    /** 缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 30_000;

    /** 最大扫描文件数（防止超大仓库 OOM） */
    private static final int MAX_SCAN_FILES = 5_000;

    /**
     * 扫描仓库中的代码文件（带缓存）
     * 使用 Files.walkFileTree + FileVisitor 在目录级别提前剪枝，
     * 避免遍历被忽略目录内的文件。
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

        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                String dirName = dir.getFileName().toString();
                if (FileScanConstants.IGNORE_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (files.size() >= MAX_SCAN_FILES) {
                    return FileVisitResult.TERMINATE;
                }
                if (FileScanConstants.isSourceFile(file)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // 跳过无法访问的文件
                return FileVisitResult.CONTINUE;
            }
        });

        List<Path> result = List.copyOf(files);
        cache.put(repoPath, new CachedScan(result, System.currentTimeMillis()));
        return result;
    }

    /**
     * 获取仓库概况（文件树和语言统计）
     */
    public String getRepoOverview(String repoPath) throws IOException {
        GitService.validateRepoPath(repoPath);

        List<Path> files = scanCodeFiles(repoPath);

        // 按语言分类统计
        Map<String, Long> languageCount = files.stream()
                .collect(Collectors.groupingBy(FileScanConstants::detectLanguage, Collectors.counting()));

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
     * 包含路径遍历防护：确保所有文件在仓库根目录之下
     */
    public String readFileContext(String repoPath, List<Path> files) throws IOException {
        Path root = Paths.get(repoPath).toRealPath();
        StringBuilder sb = new StringBuilder();
        for (Path file : files) {
            try {
                // 路径遍历防护：规范化路径确保文件在仓库之内
                Path resolved = file.toRealPath();
                if (!resolved.startsWith(root)) {
                    log.warn("路径逃逸拦截: {}", file);
                    continue;
                }
                String content = Files.readString(resolved);
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

    /**
     * 构建目录树（前 N 层）
     */
    private String buildTree(String rootPath, int maxDepth) throws IOException {
        Path root = Paths.get(rootPath);
        StringBuilder sb = new StringBuilder();
        Set<String> ignoreDirsLower = FileScanConstants.IGNORE_DIRS.stream()
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
