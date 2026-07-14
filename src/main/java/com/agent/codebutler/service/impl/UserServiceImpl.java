package com.agent.codebutler.service.impl;

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
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 * <p>
 * 密码安全升级（v4.1.0）：MD5+salt → BCrypt
 * <ul>
 *   <li>新用户：注册时直接使用 BCrypt 哈希</li>
 *   <li>旧用户：登录时自动检测 MD5 哈希，验证通过后升级为 BCrypt（零停机迁移）</li>
 * </ul>
 * <p>
 * 包含注册、登录（Session 设置）、获取当前用户（Session 读取）、登出
 */
@Service
@Profile("!test")
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** MD5 哈希固定 32 个十六进制字符，用于识别旧密码格式 */
    private static final int MD5_HASH_LENGTH = 32;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
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

        // 3. BCrypt 加密密码
        String encryptedPassword = passwordEncoder.encode(userPassword);

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

        // 2. 按用户名查询（不再在 SQL 中比对密码哈希）
        QueryWrapper qw = QueryWrapper.create()
                .eq(User::getUserAccount, userAccount);
        User user = userMapper.selectOneByQuery(qw);
        if (user == null) {
            log.info("登录失败（用户不存在）: account={}", userAccount);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // 3. 验证密码（支持 BCrypt 和 MD5 旧格式自动升级）
        boolean passwordValid = verifyAndUpgradePassword(user, userPassword);
        if (!passwordValid) {
            log.info("登录失败（密码错误）: account={}", userAccount);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // 4. 记录登录态 → 存储脱敏的 LoginUserVO
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
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj instanceof LoginUserVO loginVO) {
            User currentUser = userMapper.selectOneById(loginVO.getId());
            if (currentUser == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            return currentUser;
        }
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

    // ════════════════════════════════════════════════════════
    //  密码验证 + 自动升级（MD5 → BCrypt 零停机迁移）
    // ════════════════════════════════════════════════════════

    /**
     * 验证密码并自动升级旧格式
     * <p>
     * 检测策略：MD5 哈希固定 32 个十六进制字符且以 {@code $2} 开头的是 BCrypt，
     * 其余 32 字符哈希视为旧版 MD5，用旧版 MD5+salt 验证后自动升级为 BCrypt。
     *
     * @return true = 密码正确
     */
    private boolean verifyAndUpgradePassword(User user, String rawPassword) {
        String storedHash = user.getUserPassword();

        // BCrypt 格式：以 $2a$/$2b$/$2y$ 开头
        if (storedHash != null && storedHash.startsWith("$2")) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }

        // 旧版 MD5 格式（32 位十六进制）：尝试旧版验证后升级
        if (isLegacyMd5Hash(storedHash)) {
            String md5Hash = legacyMd5Hash(rawPassword);
            if (md5Hash.equals(storedHash)) {
                // 旧密码验证通过 → 升级为 BCrypt
                String bcryptHash = passwordEncoder.encode(rawPassword);
                user.setUserPassword(bcryptHash);
                userMapper.update(user);
                log.info("用户密码已从 MD5 自动升级为 BCrypt: userId={}", user.getId());
                return true;
            }
        }

        return false;
    }

    /**
     * 判断是否为旧版 MD5 哈希（32 位十六进制字符串）
     */
    private boolean isLegacyMd5Hash(String hash) {
        if (hash == null || hash.length() != MD5_HASH_LENGTH) {
            return false;
        }
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 旧版 MD5 哈希（仅用于验证旧密码并触发升级，不用于新注册）
     */
    private String legacyMd5Hash(String password) {
        return org.springframework.util.DigestUtils
                .md5DigestAsHex((password + "code-butler").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
