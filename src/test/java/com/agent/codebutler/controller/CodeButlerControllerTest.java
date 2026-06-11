package com.agent.codebutler.controller;

import com.agent.codebutler.service.ChatService;
import com.agent.codebutler.service.CodeReviewService;
import com.agent.codebutler.service.DocGenerationService;
import com.agent.codebutler.service.GeneralChatService;
import com.agent.codebutler.service.OperationRecordService;
import com.agent.codebutler.service.UsageService;
import com.agent.codebutler.service.UserService;
import com.agent.codebutler.aop.AuthInterceptor;
import com.agent.codebutler.aop.QuotaInterceptor;
import com.agent.codebutler.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodeButlerController.class)
@ActiveProfiles("test")
@Import({AuthInterceptor.class, QuotaInterceptor.class})
class CodeButlerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CodeReviewService codeReviewService;

    @MockBean
    private DocGenerationService docGenerationService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private GeneralChatService generalChatService;

    @MockBean
    private UserService userService;

    @MockBean
    private OperationRecordService operationRecordService;

    @MockBean
    private UsageService usageService;

    @BeforeEach
    void setUp() {
        // 模拟已登录用户（所有端点都需要 @AuthCheck(mustRole = "user")）
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUserAccount("test");
        mockUser.setUserRole("user");
        when(userService.getLoginUser(any())).thenReturn(mockUser);

        // 模拟配额检查始终通过
        when(usageService.hasQuota(anyLong(), anyString(), anyBoolean())).thenReturn(true);
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
    @DisplayName("不支持的文档类型应返回 400")
    void docsWithUnsupportedDocTypeShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/code/docs")
                        .param("repoPath", "C:/valid/path")
                        .param("docType", "INVALID_TYPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("文档生成成功应返回 200")
    void docsShouldReturnOkWhenValid() throws Exception {
        var result = new com.agent.codebutler.dto.DocGenerateResult();
        result.setDocType("README");
        result.setDocument("# Test Doc");
        when(docGenerationService.generate(any(), any(), any())).thenReturn(result);
        when(docGenerationService.isValidDocType("README")).thenReturn(true);

        mockMvc.perform(post("/api/code/docs")
                        .param("repoPath", "C:/valid/path")
                        .param("docType", "README"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("通用聊天缺少 message 应返回 400")
    void generalChatMissingMessageShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/code/chat/general/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
