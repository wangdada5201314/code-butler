package com.agent.codebutler.aop;

import com.agent.codebutler.annotation.AuthCheck;
import com.agent.codebutler.exception.BusinessException;
import com.agent.codebutler.exception.ErrorCode;
import com.agent.codebutler.model.entity.User;
import com.agent.codebutler.model.enums.UserRoleEnum;
import com.agent.codebutler.service.UserService;
import com.agent.codebutler.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP 鉴权拦截器
 * 拦截标注了 @AuthCheck 注解的方法，校验登录状态和角色。
 * 鉴权通过后将 userId 存入 RequestAttribute，供 Controller 层通过 UserContext 读取。
 */
@Aspect
@Component
public class AuthInterceptor {

    private final UserService userService;

    public AuthInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        // 1. 获取当前登录用户（未登录会抛 NOT_LOGIN_ERROR）
        User loginUser = userService.getLoginUser(request);

        // 2. 将 userId 存入 RequestAttribute，供 Controller 层使用
        UserContext.setUserId(request, loginUser.getId());

        // 3. 如果注解未指定角色，只需登录即可
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // 4. 校验用户角色
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 5. 管理员权限校验
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        return joinPoint.proceed();
    }
}
