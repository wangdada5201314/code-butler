package com.agent.codebutler.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {

    private String userAccount;
    private String userPassword;
    private String checkPassword;
}
