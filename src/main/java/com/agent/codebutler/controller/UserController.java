package com.agent.codebutler.controller;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.dto.ApiResponse;
import com.agent.codebutler.dto.FavoriteRepoAddRequest;
import com.agent.codebutler.dto.UserPreferenceUpdateRequest;
import com.agent.codebutler.model.dto.user.UserLoginRequest;
import com.agent.codebutler.model.dto.user.UserRegisterRequest;
import com.agent.codebutler.model.vo.FavoriteRepoVO;
import com.agent.codebutler.model.vo.LoginUserVO;
import com.agent.codebutler.model.vo.UserPreferenceVO;
import com.agent.codebutler.service.FavoriteRepoService;
import com.agent.codebutler.service.UserPreferenceService;
import com.agent.codebutler.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户控制器（认证 + 偏好 + 收藏仓库）
 */
@RestController
@RequestMapping("/api/user")
@Tag(name = "用户", description = "用户认证、偏好配置、收藏仓库接口")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UserPreferenceService userPreferenceService;
    private final FavoriteRepoService favoriteRepoService;

    public UserController(UserService userService,
                          UserPreferenceService userPreferenceService,
                          FavoriteRepoService favoriteRepoService) {
        this.userService = userService;
        this.userPreferenceService = userPreferenceService;
        this.favoriteRepoService = favoriteRepoService;
    }

    // ════════════════════════════════════════
    //  认证相关
    // ════════════════════════════════════════

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public ApiResponse<Long> userRegister(@RequestBody UserRegisterRequest request) {
        long userId = userService.userRegister(
                request.getUserAccount(),
                request.getUserPassword(),
                request.getCheckPassword());
        return ApiResponse.success(userId);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
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
    @Operation(summary = "获取当前登录用户")
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
    @Operation(summary = "用户登出")
    public ApiResponse<Boolean> userLogout(HttpServletRequest request) {
        boolean result = userService.userLogout(request);
        return ApiResponse.success(result);
    }

    // ════════════════════════════════════════
    //  用户偏好配置
    // ════════════════════════════════════════

    @GetMapping("/preference")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "获取用户偏好", description = "获取当前用户的审查偏好配置")
    public ApiResponse<UserPreferenceVO> getPreference(HttpServletRequest request) {
        Long userId = userService.getLoginUserIdOrNull(request);
        if (userId == null) {
            return ApiResponse.error(40100, "请先登录");
        }
        return ApiResponse.success(userPreferenceService.getPreferenceVO(userId));
    }

    @PutMapping("/preference")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "更新用户偏好", description = "更新审查关注点、深度和自定义指令")
    public ApiResponse<Boolean> updatePreference(@RequestBody UserPreferenceUpdateRequest body,
                                                  HttpServletRequest request) {
        Long userId = userService.getLoginUserIdOrNull(request);
        if (userId == null) {
            return ApiResponse.error(40100, "请先登录");
        }
        userPreferenceService.updatePreference(userId, body);
        return ApiResponse.success(true);
    }

    @GetMapping("/preference/focus-options")
    @Operation(summary = "获取关注点选项", description = "获取可选的审查关注点列表")
    public ApiResponse<Map<String, String>> getFocusOptions() {
        return ApiResponse.success(userPreferenceService.getAvailableFocusOptions());
    }

    // ════════════════════════════════════════
    //  收藏仓库
    // ════════════════════════════════════════

    @GetMapping("/favorite-repos")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "获取收藏仓库列表")
    public ApiResponse<List<FavoriteRepoVO>> getFavoriteRepos(HttpServletRequest request) {
        Long userId = userService.getLoginUserIdOrNull(request);
        if (userId == null) {
            return ApiResponse.error(40100, "请先登录");
        }
        return ApiResponse.success(favoriteRepoService.getUserFavorites(userId));
    }

    @PostMapping("/favorite-repos")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "添加收藏仓库")
    public ApiResponse<FavoriteRepoVO> addFavoriteRepo(@Valid @RequestBody FavoriteRepoAddRequest body,
                                                        HttpServletRequest request) {
        Long userId = userService.getLoginUserIdOrNull(request);
        if (userId == null) {
            return ApiResponse.error(40100, "请先登录");
        }
        try {
            return ApiResponse.success(favoriteRepoService.addFavorite(userId, body));
        } catch (Exception e) {
            log.warn("添加收藏仓库失败: userId={}", userId, e);
            return ApiResponse.error(40000, e.getMessage());
        }
    }

    @DeleteMapping("/favorite-repos/{id}")
    @AuthCheck(mustRole = "user")
    @Operation(summary = "删除收藏仓库")
    public ApiResponse<Boolean> removeFavoriteRepo(@PathVariable long id,
                                                    HttpServletRequest request) {
        Long userId = userService.getLoginUserIdOrNull(request);
        if (userId == null) {
            return ApiResponse.error(40100, "请先登录");
        }
        try {
            favoriteRepoService.removeFavorite(userId, id);
            return ApiResponse.success(true);
        } catch (Exception e) {
            log.warn("删除收藏仓库失败: userId={}, repoId={}", userId, id, e);
            return ApiResponse.error(40000, e.getMessage());
        }
    }
}
