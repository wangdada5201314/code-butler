package com.agent.codebutler.aop;

import com.agent.codebutler.annotation.QuotaCheck;
import com.agent.codebutler.exception.BusinessException;
import com.agent.codebutler.exception.ErrorCode;
import com.agent.codebutler.model.entity.User;
import com.agent.codebutler.model.enums.UserRoleEnum;
import com.agent.codebutler.service.UsageService;
import com.agent.codebutler.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 配额校验切面
 * 拦截标注了 @QuotaCheck 的方法，在执行前检查用户当日使用额度
 */
@Aspect
@Component
public class QuotaInterceptor {

    private static final Logger log = LoggerFactory.getLogger(QuotaInterceptor.class);

    private final UserService userService;
    private final UsageService usageService;

    public QuotaInterceptor(UserService userService, UsageService usageService) {
        this.userService = userService;
        this.usageService = usageService;
    }

    @Around("@annotation(quotaCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, QuotaCheck quotaCheck) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole());

        String opType = quotaCheck.opType();

        if (!usageService.hasQuota(loginUser.getId(), opType, isAdmin)) {
            int limit = usageService.getDailyLimit(opType);
            String typeName = switch (opType) {
                case "REVIEW" -> "代码审查";
                case "CHAT" -> "AI 问答";
                case "DOC" -> "文档生成";
                default -> opType;
            };
            log.info("配额超限: userId={}, opType={}, dailyLimit={}", loginUser.getId(), opType, limit);
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED.getCode(),
                    String.format("今日%s次数已达上限（%d 次），请明天再试或联系管理员提升配额", typeName, limit));
        }

        return joinPoint.proceed();
    }
}
