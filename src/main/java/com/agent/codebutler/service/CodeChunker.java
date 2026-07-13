package com.agent.codebutler.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码分块器
 * <p>
 * 将源代码文件内容按语义边界（方法/类声明）分割为可索引的代码块。
 * 支持多语言识别，超长块自动按固定大小再切分，保留重叠区域。
 */
@Component
public class CodeChunker {

    private static final int CHUNK_SIZE = 1500;
    private static final int CHUNK_OVERLAP = 200;

    /** 代码块边界正则（匹配方法/类/函数声明） */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "^\\s*(?:" +
                    "public|private|protected|static|final|abstract|synchronized|native|default" +
                    "|class|interface|enum|record" +
                    "|def|fn|func|function|async|export|import" +
                    ")\\s+",
            Pattern.MULTILINE
    );

    /** 代码分块结果 */
    public record CodeChunk(String filePath, String chunkId, String content, String language) {}

    /**
     * 将文件内容分割为代码块
     * <p>
     * 策略：优先按方法/类边界分割，超长块再按固定大小切分，保留重叠区域。
     *
     * @param content  文件内容
     * @param filePath 相对文件路径
     * @param language 编程语言
     * @return 代码块列表
     */
    public List<CodeChunk> splitIntoChunks(String content, String filePath, String language) {
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
                    if (subChunk.trim().length() > 30) {
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

        List<Integer> boundaries = new ArrayList<>();
        while (matcher.find()) {
            boundaries.add(matcher.start());
        }

        if (boundaries.isEmpty() || boundaries.get(0) > 100) {
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
}
