package com.agent.codebutler.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user")
public class User implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("userAccount")
    private String userAccount;

    @Column("userPassword")
    private String userPassword;

    @Column("userName")
    private String userName;

    @Column("userAvatar")
    private String userAvatar;

    @Column("userRole")
    private String userRole;

    @Column(value = "editTime", onInsertValue = "now()")
    private LocalDateTime editTime;

    @Column(value = "createTime", onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(value = "updateTime", onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
