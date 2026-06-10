package com.agent.codebutler.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户偏好视图对象（返回给前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceVO {

    /** 审查关注点（逗号分隔） */
    private String reviewFocus;

    /** 审查深度: detailed / standard / concise */
    private String reviewDepth;

    /** 自定义审查指令 */
    private String customPrompt;
}
