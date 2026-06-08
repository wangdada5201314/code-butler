package com.agent.codebutler;

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

    @Test
    void contextLoads() {
        // 验证 Spring 上下文启动成功
    }
}
