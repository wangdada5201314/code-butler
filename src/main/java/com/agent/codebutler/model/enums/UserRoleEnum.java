package com.agent.codebutler.model.enums;

/**
 * 用户角色枚举
 */
public enum UserRoleEnum {

    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }

    public static UserRoleEnum getEnumByValue(String value) {
        if (value == null) return null;
        for (UserRoleEnum e : UserRoleEnum.values()) {
            if (e.value.equals(value)) return e;
        }
        return null;
    }
}
