package com.agent.codebutler.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {

    private String userAccount;
    private String userPassword;
}
