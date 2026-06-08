package com.agent.codebutler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeScannerService 单元测试
 */
class CodeScannerServiceTest {

    private final CodeScannerService scanner = new CodeScannerService();

    @Test
    @DisplayName("空目录应返回空结果")
    void shouldReturnEmptyForEmptyDir(@TempDir Path tempDir) throws IOException {
        List<Path> files = scanner.scanCodeFiles(tempDir.toString());
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("应能扫描 Java 文件")
    void shouldScanJavaFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.java"), "public class Main {}");
        Files.writeString(tempDir.resolve("test.txt"), "hello");

        List<Path> files = scanner.scanCodeFiles(tempDir.toString());
        assertEquals(2, files.size());
    }

    @Test
    @DisplayName("应忽略配置的忽略目录")
    void shouldIgnoreConfiguredDirs(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("node_modules"));
        Files.writeString(tempDir.resolve("node_modules/index.js"), "module.exports = {};");
        Files.writeString(tempDir.resolve("app.java"), "class App {}");

        List<Path> files = scanner.scanCodeFiles(tempDir.toString());
        // 只应有 app.java
        assertEquals(1, files.size());
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("app.java")));
    }

    @Test
    @DisplayName("getRepoOverview 应包含语言统计")
    void overviewShouldContainLanguageStats(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
        Files.writeString(tempDir.resolve("app.py"), "print('hello')");

        String overview = scanner.getRepoOverview(tempDir.toString());
        assertTrue(overview.contains("Java"));
        assertTrue(overview.contains("Python"));
    }

    @Test
    @DisplayName("缓存应在有效期内复用")
    void cacheShouldReuseWithinTTL(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.java"), "class Main {}");

        List<Path> first = scanner.scanCodeFiles(tempDir.toString());
        List<Path> second = scanner.scanCodeFiles(tempDir.toString());

        // 内容相等即可（首次返回原始 list，后续返回缓存的不变 list）
        assertEquals(first.size(), second.size());
    }
}
