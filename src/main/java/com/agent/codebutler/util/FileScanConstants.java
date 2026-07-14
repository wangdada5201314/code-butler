package com.agent.codebutler.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 文件扫描与语言检测公共常量/工具
 * <p>
 * 统一三处重复定义（CodeScannerService / CodeKnowledgeService / CodeAnalysisTools）的
 * 忽略目录集合、源代码扩展名集合、语言检测方法和路径校验逻辑，
 * 作为项目唯一的文件扫描基础组件。
 */
public final class FileScanConstants {

    private FileScanConstants() {}

    // ──────────────── 路径安全检测 ────────────────

    /** 路径遍历攻击和命令注入检测：禁止 ..、shell 特殊字符、敏感系统目录 */
    public static final Pattern ILLEGAL_PATH_PATTERN = Pattern.compile(
            "(\\.\\.)|([;|&`$(){}<>!])|(/etc/)|(/proc/)|(/sys/)");

    // ──────────────── 忽略的目录（合并三处为超集） ────────────────

    /** 扫描时应跳过的目录名 */
    public static final Set<String> IGNORE_DIRS = Set.of(
            "node_modules", ".git", ".idea", ".vscode",
            "target", "build", "dist", "out", "bin", "obj",
            "__pycache__", ".venv", "venv",
            ".gradle", ".next", ".nuxt",
            ".workbuddy", ".agentscope",
            "vendor", ".mvn"
    );

    // ──────────────── 源代码文件扩展名（合并三处为超集） ────────────────

    /** 被视为源代码文件的扩展名 */
    public static final Set<String> SOURCE_EXTENSIONS = Set.of(
            // JVM
            ".java", ".kt", ".kts",
            // Python
            ".py", ".pyi",
            // JavaScript / TypeScript
            ".js", ".mjs", ".cjs", ".ts", ".jsx", ".tsx",
            // Systems
            ".go", ".rs", ".c", ".cpp", ".cc", ".h", ".hpp",
            // .NET / Other
            ".cs", ".rb", ".php", ".swift",
            // Frontend
            ".vue", ".html", ".css", ".scss",
            // Data / Config
            ".sql", ".yml", ".yaml", ".xml", ".json", ".properties", ".toml",
            // Script
            ".sh", ".bash", ".bat", ".ps1",
            // Document
            ".md", ".txt", ".rst"
    );

    // ──────────────── 文件类型判断 ────────────────

    /**
     * 判断文件是否为源代码文件（按扩展名匹配）
     */
    public static boolean isSourceFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return SOURCE_EXTENSIONS.contains(name.substring(dot));
    }

    // ──────────────── 语言检测（合并两处为统一版本） ────────────────

    /**
     * 根据文件名检测编程语言
     *
     * @return 语言名称，未知类型返回 "Other"
     */
    public static String detectLanguage(Path file) {
        String name = file.getFileName().toString().toLowerCase();

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
        if (name.endsWith(".cs")) return "C#";
        if (name.endsWith(".rb")) return "Ruby";
        if (name.endsWith(".php")) return "PHP";
        if (name.endsWith(".swift")) return "Swift";
        if (name.endsWith(".vue")) return "Vue";
        if (name.endsWith(".html")) return "HTML";
        if (name.endsWith(".css") || name.endsWith(".scss")) return "CSS";
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
     * 根据文件扩展名检测语言（字符串版本，供 CodeAnalysisTools 等使用）
     */
    public static String detectLanguageByExtension(String ext) {
        if (ext == null || ext.isEmpty()) return "Other";
        return switch (ext) {
            case ".java" -> "Java";
            case ".py", ".pyi" -> "Python";
            case ".js", ".jsx", ".mjs", ".cjs" -> "JavaScript";
            case ".ts", ".tsx" -> "TypeScript";
            case ".go" -> "Go";
            case ".rs" -> "Rust";
            case ".c", ".h" -> "C";
            case ".cpp", ".cc", ".hpp" -> "C++";
            case ".cs" -> "C#";
            case ".rb" -> "Ruby";
            case ".php" -> "PHP";
            case ".swift" -> "Swift";
            case ".kt", ".kts" -> "Kotlin";
            case ".vue" -> "Vue";
            case ".html" -> "HTML";
            case ".css", ".scss" -> "CSS";
            case ".sql" -> "SQL";
            case ".yml", ".yaml" -> "YAML";
            case ".xml" -> "XML";
            case ".json" -> "JSON";
            case ".sh", ".bash" -> "Shell";
            case ".properties" -> "Properties";
            case ".toml" -> "TOML";
            default -> "Other";
        };
    }

    // ──────────────── 路径校验 ────────────────

    /**
     * 校验仓库路径合法性（非空、存在、是目录 + 安全检测）
     * <p>
     * 安全检测：拒绝包含路径遍历（..）、shell 特殊字符（;|&`$ 等）、
     * 敏感系统目录（/etc/ /proc/ /sys/）的路径。
     *
     * @return 规范化后的绝对路径，不合法时返回 null
     */
    public static Path validateRepoPath(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) return null;

        // 安全检测：路径遍历攻击 + 命令注入
        if (ILLEGAL_PATH_PATTERN.matcher(repoPath).find()) {
            return null;
        }

        try {
            Path path = Paths.get(repoPath).normalize().toAbsolutePath();
            // normalize 后再次检查（防止编码绕过）
            if (path.toString().contains("..")) return null;
            return Files.isDirectory(path) ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ──────────────── 文件扫描 ────────────────

    /**
     * 扫描仓库中的所有源代码文件
     * <p>
     * 使用 Files.walkFileTree + FileVisitor 在目录级别提前剪枝，
     * 跳过 IGNORE_DIRS 中的目录。
     *
     * @param root        仓库根目录
     * @param maxFiles    最大扫描文件数（防止 OOM），0 表示不限
     * @return 源代码文件路径列表
     */
    public static List<Path> scanSourceFiles(Path root, int maxFiles) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                String dirName = dir.getFileName().toString();
                return IGNORE_DIRS.contains(dirName)
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (maxFiles > 0 && files.size() >= maxFiles) {
                    return FileVisitResult.TERMINATE;
                }
                if (isSourceFile(file) && attrs.size() > 0 && attrs.size() < 500_000) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }
}
