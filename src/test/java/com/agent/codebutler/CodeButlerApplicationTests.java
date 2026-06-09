package com.agent.codebutler;

import com.agent.codebutler.service.UserService;
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

    @Test
    void contextLoads() {
        // 验证 Spring 上下文启动成功
    }
}
