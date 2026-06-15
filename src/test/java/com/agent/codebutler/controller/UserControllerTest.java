package com.agent.codebutler.controller;

import com.agent.codebutler.aop.AuthInterceptor;
import com.agent.codebutler.model.entity.User;
import com.agent.codebutler.model.vo.LoginUserVO;
import com.agent.codebutler.service.FavoriteRepoService;
import com.agent.codebutler.service.UserPreferenceService;
import com.agent.codebutler.service.UserService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@ActiveProfiles("test")
@Import(AuthInterceptor.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserPreferenceService userPreferenceService;

    @MockBean
    private FavoriteRepoService favoriteRepoService;

    private static final String LOGIN_JSON = """
            {"userAccount":"testuser","userPassword":"12345678"}
            """;

    private static final String REGISTER_JSON = """
            {"userAccount":"newuser","userPassword":"12345678","checkPassword":"12345678"}
            """;

    @BeforeEach
    void setUp() {
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUserAccount("testuser");
        mockUser.setUserName("TestUser");
        mockUser.setUserRole("user");
        when(userService.getLoginUser(any())).thenReturn(mockUser);
    }

    @Test
    @DisplayName("登录成功应返回用户信息")
    void loginShouldReturnUserVO() throws Exception {
        LoginUserVO vo = LoginUserVO.builder()
                .id(1L)
                .userAccount("testuser")
                .userName("TestUser")
                .userRole("user")
                .build();
        when(userService.userLogin(anyString(), anyString(), any())).thenReturn(vo);

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userAccount").value("testuser"));
    }

    @Test
    @DisplayName("注册成功应返回用户ID")
    void registerShouldReturnUserId() throws Exception {
        when(userService.userRegister(anyString(), anyString(), anyString())).thenReturn(2L);

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    @DisplayName("获取当前登录用户应返回用户信息")
    void getLoginUserShouldReturnUserVO() throws Exception {
        mockMvc.perform(get("/api/user/get/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userAccount").value("testuser"));
    }

    @Test
    @DisplayName("退出登录应返回成功")
    void logoutShouldReturnOk() throws Exception {
        when(userService.userLogout(any())).thenReturn(true);

        mockMvc.perform(post("/api/user/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
