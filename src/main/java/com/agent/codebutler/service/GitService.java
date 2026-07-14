package com.agent.codebutler.service;

import com.agent.codebutler.util.FileScanConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Git 操作封装服务
 * 安全：对 repoPath 做路径校验，ProcessBuilder 设置超时
 */
@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    /** Git 命令超时（秒） */
    @Value("${git.command-timeout-seconds:30}")
    private int commandTimeoutSeconds;

    // ---- 公共 API ----

    /**
     * 获取最近 N 次提交的摘要（安全校验后调用）
     */
    public String getRecentChanges(String repoPath, int count) {
        validateRepoPath(repoPath);
        if (!isGitRepo(repoPath)) {
            return "[提示] 该目录不是 Git 仓库，无法获取变更记录。";
        }

        try {
            List<String> output = runCommand(repoPath,
                    "git", "log", "-" + Math.min(count, 100), "--oneline", "--no-decorate");
            StringBuilder sb = new StringBuilder();
            sb.append("最近 ").append(count).append(" 次提交：\n");
            for (String line : output) {
                sb.append("  ").append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取 Git 日志失败: {}", e.getMessage());
            return "[错误] 获取 Git 日志失败：" + e.getMessage();
        }
    }

    /**
     * 获取暂存区的 diff
     */
    public String getStagedDiff(String repoPath) {
        validateRepoPath(repoPath);
        if (!isGitRepo(repoPath)) {
            return "";
        }

        try {
            List<String> output = runCommand(repoPath,
                    "git", "diff", "--staged", "--stat");
            if (output.isEmpty()) {
                return "[提示] 暂存区无变更。";
            }
            return "暂存区变更：\n" + String.join("\n", output);
        } catch (Exception e) {
            log.warn("获取 Git staged diff 失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取未提交的 diff
     */
    public String getUnstagedDiff(String repoPath) {
        validateRepoPath(repoPath);
        if (!isGitRepo(repoPath)) {
            return "";
        }

        try {
            List<String> output = runCommand(repoPath,
                    "git", "diff", "--stat");
            if (output.isEmpty()) {
                return "[提示] 工作区无变更。";
            }
            return "工作区变更：\n" + String.join("\n", output);
        } catch (Exception e) {
            log.warn("获取 Git unstaged diff 失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取指定分支的日志
     */
    public String getBranchLog(String repoPath, String branch, int count) {
        validateRepoPath(repoPath);
        if (!isGitRepo(repoPath)) {
            return "[提示] 不是 Git 仓库。";
        }

        // 对分支名做基础校验
        if (branch == null || branch.contains(";") || branch.contains("|") || branch.contains("..")) {
            return "[错误] 无效的分支名。";
        }

        try {
            List<String> output = runCommand(repoPath,
                    "git", "log", branch, "-" + Math.min(count, 100), "--oneline", "--no-decorate");
            return String.join("\n", output);
        } catch (Exception e) {
            log.warn("获取分支日志失败: {}", e.getMessage());
            return "[错误] 获取分支日志失败：" + e.getMessage();
        }
    }

    /**
     * 获取仓库综合状态（分支、工作区、最近提交）
     */
    public String getRepoStatus(String repoPath) {
        validateRepoPath(repoPath);
        if (!isGitRepo(repoPath)) {
            return "[提示] 该目录不是 Git 仓库。";
        }

        try {
            List<String> branch = runCommand(repoPath,
                    "git", "rev-parse", "--abbrev-ref", "HEAD");
            List<String> status = runCommand(repoPath,
                    "git", "status", "--short");
            List<String> logOutput = runCommand(repoPath,
                    "git", "log", "-5", "--oneline", "--no-decorate");

            StringBuilder sb = new StringBuilder();
            sb.append("当前分支：").append(String.join("", branch)).append("\n\n");
            sb.append("工作区状态：\n");
            if (status.isEmpty()) {
                sb.append("  干净的工作区\n");
            } else {
                for (String s : status) {
                    sb.append("  ").append(s).append("\n");
                }
            }
            sb.append("\n最近提交：\n");
            for (String s : logOutput) {
                sb.append("  ").append(s).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取仓库状态失败: {}", e.getMessage());
            return "[错误] 获取仓库状态失败：" + e.getMessage();
        }
    }

    // ---- 内部方法 ----

    /**
     * 安全校验 repoPath
     * <p>
     * 使用 {@link FileScanConstants#ILLEGAL_PATH_PATTERN} 进行统一的安全检测（路径遍历 + 命令注入），
     * 在此补充异常抛出行为以适配 Controller 层调用。
     * 对语法合法但目录不存在的路径不抛异常（适配新仓库场景）。
     *
     * @throws IllegalArgumentException 如果路径不合法
     */
    public static void validateRepoPath(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("仓库路径不能为空");
        }

        // 使用统一的安全检测正则
        if (FileScanConstants.ILLEGAL_PATH_PATTERN.matcher(repoPath).find()) {
            throw new IllegalArgumentException("仓库路径包含非法字符: " + repoPath);
        }

        try {
            Path path = Paths.get(repoPath).toAbsolutePath().normalize();
            // normalize 后再次检查（防止编码绕过）
            if (path.toString().contains("..")) {
                throw new IllegalArgumentException("仓库路径包含非法字符: " + repoPath);
            }
        } catch (java.nio.file.InvalidPathException e) {
            throw new IllegalArgumentException("无效的路径格式: " + repoPath);
        }
    }

    private boolean isGitRepo(String repoPath) {
        try {
            Path repo = Paths.get(repoPath).toRealPath();
            return Files.isDirectory(repo.resolve(".git"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 执行命令（带超时）
     */
    private List<String> runCommand(String workDir, String... command) {
        List<String> output = new ArrayList<>();
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(Paths.get(workDir).toFile());
            pb.redirectErrorStream(true);
            process = pb.start();

            boolean finished = process.waitFor(commandTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                output.add("[错误] Git 命令执行超时（" + commandTimeoutSeconds + "秒）");
                return output;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.add("[提示] Git 命令退出码: " + exitCode);
            }
        } catch (Exception e) {
            output.add("[错误] 命令执行失败: " + e.getMessage());
            log.warn("Git 命令执行异常: {}", e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return output;
    }
}
