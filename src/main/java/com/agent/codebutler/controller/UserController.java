package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.constant.UserConstant;
import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.model.dto.user.UserLoginRequest;
import com.agent.codebutler.model.dto.user.UserRegisterRequest;
import com.agent.codebutler.model.vo.LoginUserVO;
import com.agent.codebutler.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<Long> userRegister(@RequestBody UserRegisterRequest request) {
        long userId = userService.userRegister(
                request.getUserAccount(),
                request.getUserPassword(),
                request.getCheckPassword());
        return ApiResponse.success(userId);
    }

    @PostMapping("/login")
    public ApiResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest request,
                                               HttpServletRequest httpRequest) {
        LoginUserVO loginUserVO = userService.userLogin(
                request.getUserAccount(),
                request.getUserPassword(),
                httpRequest);
        return ApiResponse.success(loginUserVO);
    }

    @GetMapping("/get/login")
    @AuthCheck
    public ApiResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        var user = userService.getLoginUser(request);
        LoginUserVO vo = LoginUserVO.builder()
                .id(user.getId())
                .userAccount(user.getUserAccount())
                .userName(user.getUserName())
                .userAvatar(user.getUserAvatar())
                .userRole(user.getUserRole())
                .build();
        return ApiResponse.success(vo);
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> userLogout(HttpServletRequest request) {
        boolean result = userService.userLogout(request);
        return ApiResponse.success(result);
    }
}
