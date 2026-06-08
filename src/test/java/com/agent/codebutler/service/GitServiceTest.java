package com.agent.codebutler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitService 单元测试 — 重点测试安全校验逻辑
 */
class GitServiceTest {

    @Test
    @DisplayName("正常路径应通过校验")
    void shouldAcceptValidPath() {
        // 使用临时目录
        String tmpDir = System.getProperty("java.io.tmpdir");
        assertDoesNotThrow(() -> GitService.validateRepoPath(tmpDir));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("空路径应抛出异常")
    void shouldRejectBlankPath(String path) {
        assertThrows(IllegalArgumentException.class,
                () -> GitService.validateRepoPath(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "repo; rm -rf /",
            "repo|cat /etc/passwd",
            "repo`id`",
            "repo$(whoami)"
    })
    @DisplayName("路径遍历和命令注入应被拦截")
    void shouldRejectIllegalPaths(String path) {
        assertThrows(IllegalArgumentException.class,
                () -> GitService.validateRepoPath(path));
    }

    @Test
    @DisplayName("Windows 盘符路径通过语法校验（即使目录不存在）")
    void shouldAcceptWindowsStylePath() {
        // validateRepoPath 允许路径语法合法但目录不存在的情况（适配新仓库场景）
        assertDoesNotThrow(() -> GitService.validateRepoPath("Z:/non_existent_path_12345"));
    }
}
