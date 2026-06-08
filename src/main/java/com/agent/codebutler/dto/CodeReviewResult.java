package com.agent.codebutler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 代码审查结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeReviewResult {

    private String sessionId;
    private String repoPath;
    private String overview;
    private String review;
    private List<CodeIssue> issues;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeIssue {
        private String severity;
        private Integer line;
        private String fileName;
        private String message;
        private String suggestion;
    }
}
