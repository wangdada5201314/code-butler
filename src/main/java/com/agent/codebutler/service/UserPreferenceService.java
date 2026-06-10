package com.agent.codebutler.service;

import com.agent.codebutler.dto.UserPreferenceUpdateRequest;
import com.agent.codebutler.mapper.UserPreferenceMapper;
import com.agent.codebutler.model.entity.UserPreference;
import com.agent.codebutler.model.vo.UserPreferenceVO;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户偏好配置服务
 */
@Service
public class UserPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceService.class);

    /** 审查关注点的中英文映射 */
    private static final Map<String, String> FOCUS_LABELS = Map.of(
            "naming", "命名规范",
            "performance", "性能优化",
            "security", "安全漏洞",
            "architecture", "架构设计",
            "readability", "代码可读性"
    );

    /** 审查深度的 prompt 描述 */
    private static final Map<String, String> DEPTH_LABELS = Map.of(
            "detailed", "详细深入（逐文件分析，给出代码示例）",
            "standard", "标准（覆盖主要问题，适度详细）",
            "concise", "精简（只列出关键问题，简明扼要）"
    );

    private final UserPreferenceMapper preferenceMapper;

    public UserPreferenceService(UserPreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    /**
     * 获取用户偏好（不存在则返回默认值）
     */
    public UserPreferenceVO getPreferenceVO(long userId) {
        UserPreference pref = getPreference(userId);
        if (pref == null) {
            return UserPreferenceVO.builder()
                    .reviewFocus("")
                    .reviewDepth("standard")
                    .customPrompt("")
                    .build();
        }
        return UserPreferenceVO.builder()
                .reviewFocus(pref.getReviewFocus() != null ? pref.getReviewFocus() : "")
                .reviewDepth(pref.getReviewDepth() != null ? pref.getReviewDepth() : "standard")
                .customPrompt(pref.getCustomPrompt() != null ? pref.getCustomPrompt() : "")
                .build();
    }

    /**
     * 更新用户偏好（upsert 语义）
     */
    public void updatePreference(long userId, UserPreferenceUpdateRequest req) {
        UserPreference existing = getPreference(userId);

        if (existing != null) {
            existing.setReviewFocus(req.getReviewFocus());
            existing.setReviewDepth(req.getReviewDepth());
            existing.setCustomPrompt(req.getCustomPrompt());
            preferenceMapper.update(existing);
            log.info("用户偏好已更新: userId={}", userId);
        } else {
            UserPreference pref = UserPreference.builder()
                    .userId(userId)
                    .reviewFocus(req.getReviewFocus())
                    .reviewDepth(req.getReviewDepth())
                    .customPrompt(req.getCustomPrompt())
                    .build();
            preferenceMapper.insert(pref);
            log.info("用户偏好已创建: userId={}", userId);
        }
    }

    /**
     * 构建融入审查 prompt 的用户偏好文本。
     * 如果没有自定义偏好，返回空字符串。
     *
     * @param userId 用户 ID（可为 null）
     * @return 可直接拼接到 prompt 的偏好描述文本
     */
    public String buildPreferencePrompt(Long userId) {
        if (userId == null) return "";

        UserPreference pref = getPreference(userId);
        if (pref == null) return "";

        StringBuilder sb = new StringBuilder();

        // 1. 审查深度
        String depth = pref.getReviewDepth();
        if (depth != null && !depth.isEmpty()) {
            String depthDesc = DEPTH_LABELS.getOrDefault(depth, DEPTH_LABELS.get("standard"));
            sb.append("审查深度要求：").append(depthDesc).append("。\n");
        }

        // 2. 审查关注点
        String focus = pref.getReviewFocus();
        if (focus != null && !focus.isBlank()) {
            String focusDesc = Arrays.stream(focus.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> FOCUS_LABELS.getOrDefault(s, s))
                    .collect(Collectors.joining("、"));
            if (!focusDesc.isEmpty()) {
                sb.append("重点关注方面：").append(focusDesc).append("（请对这些方面给予更多篇幅和更深入的分析）。\n");
            }
        }

        // 3. 自定义指令
        String custom = pref.getCustomPrompt();
        if (custom != null && !custom.isBlank()) {
            sb.append("用户的额外要求：").append(custom.trim()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 获取可用的审查关注点选项
     */
    public Map<String, String> getAvailableFocusOptions() {
        return FOCUS_LABELS;
    }

    private UserPreference getPreference(long userId) {
        QueryWrapper qw = QueryWrapper.create()
                .eq(UserPreference::getUserId, userId);
        return preferenceMapper.selectOneByQuery(qw);
    }
}
