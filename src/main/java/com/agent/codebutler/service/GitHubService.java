package com.agent.codebutler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub 仓库 URL 解析与校验服务
 * <p>
 * 支持格式：
 * - https://github.com/owner/repo
 * - https://github.com/owner/repo.git
 * - github.com/owner/repo
 */
@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);

    /** 匹配 GitHub URL 的正则 */
    private static final Pattern GITHUB_PATTERN = Pattern.compile(
            "(?:https?://)?github\\.com/([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+?)(?:\\.git)?(?:/.*)?$"
    );

    /**
     * 判断输入是否为 GitHub URL
     */
    public static boolean isGitHubUrl(String input) {
        if (input == null || input.isBlank()) return false;
        return GITHUB_PATTERN.matcher(input.trim()).matches();
    }

    /**
     * 从 GitHub URL 中解析 owner 和 repo
     *
     * @param githubUrl GitHub 仓库 URL
     * @return [owner, repo]，解析失败返回 null
     */
    public static String[] parseGitHubUrl(String githubUrl) {
        if (githubUrl == null) return null;
        Matcher m = GITHUB_PATTERN.matcher(githubUrl.trim());
        if (m.matches()) {
            return new String[]{m.group(1), m.group(2)};
        }
        return null;
    }

    /**
     * 构建标准化的 GitHub 仓库描述
     */
    public static String formatGitHubRepo(String owner, String repo) {
        return String.format("github.com/%s/%s", owner, repo);
    }

    /**
     * 校验 GitHub Token 是否已配置
     */
    public static boolean hasGitHubToken(String token) {
        return token != null && !token.isBlank();
    }
}
