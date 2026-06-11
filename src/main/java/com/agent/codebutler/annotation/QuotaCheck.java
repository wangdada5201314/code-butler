package com.agent.codebutler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配额校验注解
 * 在需要检查使用额度的 Controller 方法上标注
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QuotaCheck {

    /**
     * 操作类型：REVIEW / CHAT / DOC
     */
    String opType();
}
