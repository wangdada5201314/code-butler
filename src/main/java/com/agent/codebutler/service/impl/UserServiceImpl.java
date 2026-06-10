package com.agent.codebutler.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.agent.codebutler.constant.UserConstant;
import com.agent.codebutler.exception.BusinessException;
import com.agent.codebutler.exception.ErrorCode;
import com.agent.codebutler.mapper.UserMapper;
import com.agent.codebutler.model.entity.User;
import com.agent.codebutler.model.enums.UserRoleEnum;
import com.agent.codebutler.model.vo.LoginUserVO;
import com.agent.codebutler.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * 用户服务实现
 * 包含注册（MD5+salt加密）、登录（Session设置）、获取当前用户（Session读取）、登出
 */
@Service
@Profile("!test")
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Value("${app.security.password-salt}")
    private String salt;

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (userAccount == null || userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号不能少于4位");
        }
        if (userPassword == null || userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码不能少于8位");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }

        // 2. 检查账号是否已存在
        QueryWrapper qw = QueryWrapper.create()
                .eq(User::getUserAccount, userAccount);
        User existing = userMapper.selectOneByQuery(qw);
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已存在");
        }

        // 3. 加密密码
        String encryptedPassword = getEncryptPassword(userPassword);

        // 4. 插入数据库
        User user = User.builder()
                .userAccount(userAccount)
                .userPassword(encryptedPassword)
                .userRole(UserRoleEnum.USER.getValue())
                .build();
        userMapper.insert(user);

        log.info("用户注册成功: account={}, id={}", userAccount, user.getId());
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (userAccount == null || userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能少于4位");
        }
        if (userPassword == null || userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能少于8位");
        }

        // 2. 加密并查询
        String encryptedPassword = getEncryptPassword(userPassword);
        QueryWrapper qw = QueryWrapper.create()
                .eq(User::getUserAccount, userAccount)
                .eq(User::getUserPassword, encryptedPassword);
        User user = userMapper.selectOneByQuery(qw);
        if (user == null) {
            log.info("登录失败: account={}", userAccount);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // 3. 记录登录态 → 存储脱敏的 LoginUserVO，避免密码泄漏到 Session/Redis
        LoginUserVO loginVO = LoginUserVO.builder()
                .id(user.getId())
                .userAccount(user.getUserAccount())
                .userName(user.getUserName())
                .userAvatar(user.getUserAvatar())
                .userRole(user.getUserRole())
                .build();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, loginVO);
        log.info("用户登录成功: account={}, id={}", userAccount, user.getId());

        return loginVO;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 从 Session 读取脱敏的 LoginUserVO
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj instanceof LoginUserVO loginVO) {
            // 从数据库重新查询，确保数据最新
            User currentUser = userMapper.selectOneById(loginVO.getId());
            if (currentUser == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            return currentUser;
        }
        // 兼容旧版 Session（存的是完整 User 对象的情况）
        if (userObj instanceof User currentUser) {
            if (currentUser.getId() == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            currentUser = userMapper.selectOneById(currentUser.getId());
            if (currentUser == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            return currentUser;
        }
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    @Override
    public Long getLoginUserIdOrNull(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj instanceof LoginUserVO loginVO) {
            return loginVO.getId();
        }
        if (userObj instanceof User user) {
            return user.getId();
        }
        return null;
    }

    /**
     * MD5 + Salt 加密
     */
    private String getEncryptPassword(String userPassword) {
        return DigestUtils.md5DigestAsHex((userPassword + salt).getBytes(StandardCharsets.UTF_8));
    }
}
