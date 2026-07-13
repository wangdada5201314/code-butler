package com.agent.codebutler.tools;

import com.agent.codebutler.util.FileScanConstants;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 自定义代码分析工具集
 * <p>
 * 通过 @Tool 注解注册到 AgentScope Toolkit，Agent 在推理过程中自主决定调用。
 * 所有工具都内置路径安全校验，防止目录穿越攻击。
 */
public class CodeAnalysisTools {

    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisTools.class);

    // ════════════════════════════════════════════════════════
    //  1. 关键词搜索
    // ════════════════════════════════════════════════════════

    @Tool(name = "search_code_files", description = "在仓库中按关键词搜索源代码文件，返回匹配的文件路径和行号")
    public String searchCodeFiles(
            @ToolParam(name = "repoPath", description = "仓库根目录的绝对路径") String repoPath,
            @ToolParam(name = "keyword", description = "要搜索的关键词（区分大小写）") String keyword) {

        Path root = FileScanConstants.validateRepoPath(repoPath);
        if (root == null) return "错误: 无效的仓库路径";
        if (keyword == null || keyword.isBlank()) return "错误: 关键词不能为空";

        List<String> results = new ArrayList<>();
        AtomicInteger matchCount = new AtomicInteger(0);

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileScanConstants.IGNORE_DIRS.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matchCount.get() >= 50) return FileVisitResult.TERMINATE;
                    if (!FileScanConstants.isSourceFile(file)) return FileVisitResult.CONTINUE;
                    if (attrs.size() > 500_000) return FileVisitResult.CONTINUE; // 跳过大文件

                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            if (lines.get(i).contains(keyword)) {
                                String relPath = root.relativize(file).toString().replace('\\', '/');
                                results.add("%s:%d  %s".formatted(relPath, i + 1, lines.get(i).trim()));
                                if (matchCount.incrementAndGet() >= 50) break;
                            }
                        }
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "搜索失败: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "未找到包含 \"" + keyword + "\" 的文件";
        }
        return "找到 %d 处匹配（最多显示 50 条）:\n%s".formatted(results.size(), String.join("\n", results));
    }

    // ════════════════════════════════════════════════════════
    //  2. 代码行数统计
    // ════════════════════════════════════════════════════════

    @Tool(name = "count_code_lines", description = "统计仓库的代码行数，按语言分类，区分代码行/注释行/空行")
    public String countCodeLines(
            @ToolParam(name = "repoPath", required = true, description = "仓库根目录的绝对路径") String repoPath) {

        Path root = FileScanConstants.validateRepoPath(repoPath);
        if (root == null) return "错误: 无效的仓库路径";

        Map<String, int[]> stats = new TreeMap<>(); // lang -> [code, comment, blank]
        AtomicInteger totalFiles = new AtomicInteger(0);

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileScanConstants.IGNORE_DIRS.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!FileScanConstants.isSourceFile(file)) return FileVisitResult.CONTINUE;
                    if (attrs.size() > 1_000_000) return FileVisitResult.CONTINUE;

                    String ext = getExtension(file);
                    String lang = FileScanConstants.detectLanguageByExtension(ext);
                    int[] counts = stats.computeIfAbsent(lang, k -> new int[3]);
                    totalFiles.incrementAndGet();

                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        boolean inBlockComment = false;
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty()) {
                                counts[2]++;
                            } else if (inBlockComment) {
                                counts[1]++;
                                if (trimmed.contains("*/")) inBlockComment = false;
                            } else if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--")) {
                                counts[1]++;
                            } else if (trimmed.startsWith("/*")) {
                                counts[1]++;
                                if (!trimmed.contains("*/")) inBlockComment = true;
                            } else {
                                counts[0]++;
                            }
                        }
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "统计失败: " + e.getMessage();
        }

        if (stats.isEmpty()) return "未找到源代码文件";

        StringBuilder sb = new StringBuilder();
        sb.append("代码统计 (%d 个文件):\n\n".formatted(totalFiles.get()));
        sb.append("%-12s %8s %8s %8s %8s%n".formatted("语言", "代码行", "注释行", "空行", "总计"));
        sb.append("-".repeat(48)).append("\n");

        int[] totals = {0, 0, 0};
        for (var entry : stats.entrySet()) {
            int[] c = entry.getValue();
            int total = c[0] + c[1] + c[2];
            sb.append("%-12s %8d %8d %8d %8d%n".formatted(entry.getKey(), c[0], c[1], c[2], total));
            totals[0] += c[0]; totals[1] += c[1]; totals[2] += c[2];
        }
        sb.append("-".repeat(48)).append("\n");
        int grandTotal = totals[0] + totals[1] + totals[2];
        sb.append("%-12s %8d %8d %8d %8d%n".formatted("合计", totals[0], totals[1], totals[2], grandTotal));

        double commentRatio = grandTotal > 0 ? (double) totals[1] / grandTotal * 100 : 0;
        sb.append("\n注释率: %.1f%%".formatted(commentRatio));

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════
    //  3. 圈复杂度分析
    // ════════════════════════════════════════════════════════

    @Tool(name = "calculate_complexity", description = "计算指定文件的圈复杂度，识别高复杂度方法（>10）")
    public String calculateComplexity(
            @ToolParam(name = "repoPath", required = true, description = "仓库根目录的绝对路径") String repoPath,
            @ToolParam(name = "filePath", required = true, description = "相对于仓库根目录的文件路径") String filePath) {

        Path root = FileScanConstants.validateRepoPath(repoPath);
        if (root == null) return "错误: 无效的仓库路径";

        Path file = root.resolve(filePath).normalize();
        if (!file.startsWith(root)) return "错误: 文件路径不合法";
        if (!Files.exists(file)) return "错误: 文件不存在 - " + filePath;

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            // 决策点关键词（适用于多种语言）
            Set<String> decisionKeywords = Set.of(
                    "if", "else if", "for", "while", "case", "catch",
                    "&&", "||", "??", "?."
            );

            int complexity = 1; // 基础复杂度
            int lineCount = 0;
            List<String> highComplexityLines = new ArrayList<>();

            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) continue;
                lineCount++;

                for (String kw : decisionKeywords) {
                    if (kw.length() > 2) {
                        // 关键词需要词边界匹配
                        if (trimmed.matches(".*\\b" + kw.replace(".", "\\.") + "\\b.*")) {
                            complexity++;
                            if (complexity <= 30) { // 只记录前 30 个
                                highComplexityLines.add("  L%d: %s".formatted(i + 1, trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed));
                            }
                        }
                    } else {
                        // 运算符直接匹配
                        int idx = 0;
                        while ((idx = trimmed.indexOf(kw, idx)) >= 0) {
                            complexity++;
                            idx += kw.length();
                        }
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("文件: %s%n".formatted(filePath));
            sb.append("有效代码行: %d%n".formatted(lineCount));
            sb.append("圈复杂度: %d%n".formatted(complexity));
            sb.append("评级: %s%n%n".formatted(complexityRating(complexity)));

            if (!highComplexityLines.isEmpty()) {
                sb.append("决策点明细:\n");
                sb.append(String.join("\n", highComplexityLines));
            }
            return sb.toString();

        } catch (IOException e) {
            return "分析失败: " + e.getMessage();
        }
    }

    // ════════════════════════════════════════════════════════
    //  4. 代码坏味道检测
    // ════════════════════════════════════════════════════════

    @Tool(name = "detect_code_smells", description = "扫描仓库中的代码坏味道：超长方法、过大文件、深层嵌套、硬编码密钥等")
    public String detectCodeSmells(
            @ToolParam(name = "repoPath", required = true, description = "仓库根目录的绝对路径") String repoPath) {

        Path root = FileScanConstants.validateRepoPath(repoPath);
        if (root == null) return "错误: 无效的仓库路径";

        List<String> smells = new ArrayList<>();
        AtomicInteger filesScanned = new AtomicInteger(0);

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return FileScanConstants.IGNORE_DIRS.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!FileScanConstants.isSourceFile(file)) return FileVisitResult.CONTINUE;
                    if (attrs.size() > 1_000_000) return FileVisitResult.CONTINUE;
                    filesScanned.incrementAndGet();

                    String relPath = root.relativize(file).toString().replace('\\', '/');

                    try {
                        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                        int totalLines = lines.size();

                        // 坏味道 1: 过大文件 (>500 行)
                        if (totalLines > 500) {
                            smells.add("🔴 文件过大 (%d 行): %s".formatted(totalLines, relPath));
                        }

                        // 坏味道 2: 硬编码密钥/密码
                        for (int i = 0; i < lines.size(); i++) {
                            String lower = lines.get(i).toLowerCase();
                            if ((lower.contains("password") || lower.contains("secret") || lower.contains("apikey") || lower.contains("api_key"))
                                    && (lower.contains("=") || lower.contains(":"))
                                    && !lower.contains("placeholder") && !lower.contains("example")
                                    && !lower.trim().startsWith("//") && !lower.trim().startsWith("*")) {
                                smells.add("🔴 疑似硬编码密钥 L%d: %s".formatted(i + 1, relPath));
                                break; // 每个文件只报一次
                            }
                        }

                        // 坏味道 3: 深层嵌套 (>4 层)
                        int maxIndent = 0;
                        for (String line : lines) {
                            if (line.trim().isEmpty()) continue;
                            int indent = 0;
                            for (char c : line.toCharArray()) {
                                if (c == ' ') indent++;
                                else if (c == '\t') indent += 4;
                                else break;
                            }
                            maxIndent = Math.max(maxIndent, indent / 4);
                        }
                        if (maxIndent > 4) {
                            smells.add("🟡 深层嵌套 (%d 层): %s".formatted(maxIndent, relPath));
                        }

                        // 坏味道 4: 超长 import 块 (>20 条)
                        long importCount = lines.stream()
                                .filter(l -> l.trim().startsWith("import ") || l.trim().startsWith("from "))
                                .count();
                        if (importCount > 20) {
                            smells.add("🟡 依赖过多 (%d 个 import): %s".formatted(importCount, relPath));
                        }

                        // 坏味道 5: TODO/FIXME/HACK 注释
                        long todoCount = lines.stream()
                                .filter(l -> l.toUpperCase().matches(".*(TODO|FIXME|HACK|XXX).*"))
                                .count();
                        if (todoCount > 0) {
                            smells.add("🟢 待处理标记 (%d 个 TODO/FIXME): %s".formatted(todoCount, relPath));
                        }

                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "扫描失败: " + e.getMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("扫描了 %d 个文件\n\n".formatted(filesScanned.get()));

        if (smells.isEmpty()) {
            sb.append("✅ 未发现明显的代码坏味道");
        } else {
            // 按严重程度排序
            smells.sort((a, b) -> {
                int pa = a.startsWith("🔴") ? 0 : a.startsWith("🟡") ? 1 : 2;
                int pb = b.startsWith("🔴") ? 0 : b.startsWith("🟡") ? 1 : 2;
                return pa - pb;
            });
            sb.append("发现 %d 个潜在问题:\n\n".formatted(smells.size()));
            sb.append(String.join("\n", smells));
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════
    //  内部工具方法
    // ════════════════════════════════════════════════════════

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private String complexityRating(int complexity) {
        if (complexity <= 5) return "🟢 简单 — 易于理解和维护";
        if (complexity <= 10) return "🟢 中等 — 可接受";
        if (complexity <= 20) return "🟡 较高 — 建议拆分重构";
        if (complexity <= 50) return "🔴 高 — 难以测试和维护，强烈建议重构";
        return "🔴 极高 — 严重质量问题，必须重构";
    }
}
