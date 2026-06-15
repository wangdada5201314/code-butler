package com.agent.codebutler;

import com.agent.codebutler.service.ChatService;
import com.agent.codebutler.service.CodeKnowledgeService;
import com.agent.codebutler.service.CodeReviewService;
import com.agent.codebutler.service.DashScopeEmbeddingService;
import com.agent.codebutler.service.DocGenerationService;
import com.agent.codebutler.service.GeneralChatService;
import com.agent.codebutler.service.OperationRecordService;
import com.agent.codebutler.service.QuotaConfigService;
import com.agent.codebutler.service.UsageService;
import com.agent.codebutler.service.UserMemoryService;
import com.agent.codebutler.service.UserService;
import com.agent.codebutler.service.UserPreferenceService;
import com.agent.codebutler.service.FavoriteRepoService;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CodeButlerApplicationTests {

    @MockBean
    private HarnessAgent agent;

    @MockBean
    private UserService userService;

    @MockBean
    private OperationRecordService operationRecordService;

    @MockBean
    private UserPreferenceService userPreferenceService;

    @MockBean
    private FavoriteRepoService favoriteRepoService;

    @MockBean
    private GeneralChatService generalChatService;

    @MockBean
    private UsageService usageService;

    @MockBean
    private QuotaConfigService quotaConfigService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private CodeKnowledgeService codeKnowledgeService;

    @MockBean
    private CodeReviewService codeReviewService;

    @MockBean
    private DashScopeEmbeddingService dashScopeEmbeddingService;

    @MockBean
    private DocGenerationService docGenerationService;

    @MockBean
    private UserMemoryService userMemoryService;

    @Test
    void contextLoads() {
        // 验证 Spring 上下文启动成功
    }
}
