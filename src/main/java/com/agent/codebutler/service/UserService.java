package com.agent.codebutler.service;

import com.agent.codebutler.model.entity.User;
import com.agent.codebutler.model.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录，返回脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户（从 Session 读取，未登录则抛异常）
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户登出
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取当前登录用户 ID，未登录返回 null（不抛异常）
     */
    Long getLoginUserIdOrNull(HttpServletRequest request);
}
