package com.agent.codebutler.controller;

import com.agent.codebutler.service.CodeScannerService;
import com.agent.codebutler.service.GitService;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodeButlerController.class)
@ActiveProfiles("test")
class CodeButlerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HarnessAgent agent;

    @MockBean
    private CodeScannerService codeScanner;

    @MockBean
    private GitService gitService;

    @BeforeEach
    void setUp() {
        when(agent.getName()).thenReturn("code-butler");
    }

    @Test
    @DisplayName("健康检查应返回 200")
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/code/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("代码审查缺少 repoPath 应返回 400")
    void reviewMissingRepoPathShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/code/review"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("流式问答缺少必填字段应返回 400")
    void chatStreamMissingFieldsShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/code/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("文档生成路径包含非法字符应返回 400")
    void docsWithIllegalPathShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/code/docs")
                        .param("repoPath", "../etc/passwd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
