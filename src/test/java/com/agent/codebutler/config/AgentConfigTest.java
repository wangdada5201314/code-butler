package com.agent.codebutler.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentConfig 单元测试 — 测试模型名提取逻辑
 */
class AgentConfigTest {

    /**
     * 通过反射调用 private static extractModelName 方法
     */
    private static String invokeExtractModelName(String modelTag) throws Exception {
        Method method = AgentConfig.class.getDeclaredMethod("extractModelName", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, modelTag);
    }

    @Test
    @DisplayName("提取 dashscope:qwen-plus → qwen-plus")
    void shouldExtractDashscopeModelName() throws Exception {
        assertEquals("qwen-plus", invokeExtractModelName("dashscope:qwen-plus"));
    }

    @Test
    @DisplayName("提取 openai:gpt-4o → gpt-4o")
    void shouldExtractOpenAIModelName() throws Exception {
        assertEquals("gpt-4o", invokeExtractModelName("openai:gpt-4o"));
    }

    @Test
    @DisplayName("提取 openai:gpt-4o-mini → gpt-4o-mini")
    void shouldExtractOpenAIMiniModelName() throws Exception {
        assertEquals("gpt-4o-mini", invokeExtractModelName("openai:gpt-4o-mini"));
    }

    @Test
    @DisplayName("无前缀时返回原值")
    void shouldReturnOriginalWhenNoPrefix() throws Exception {
        assertEquals("qwen-plus", invokeExtractModelName("qwen-plus"));
    }

    @Test
    @DisplayName("空字符串应返回空字符串")
    void shouldReturnEmptyForEmptyInput() throws Exception {
        assertEquals("", invokeExtractModelName(""));
    }
}
