package com.agent.codebutler.dto;

import lombok.Data;

/**
 * 更新用户偏好请求体
 */
@Data
public class UserPreferenceUpdateRequest {

    /** 审查关注点（逗号分隔）: naming,performance,security,architecture,readability */
    private String reviewFocus;

    /** 审查深度: detailed / standard / concise */
    private String reviewDepth;

    /** 自定义审查指令 */
    private String customPrompt;
}
