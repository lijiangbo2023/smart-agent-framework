package com.smart.agent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * System user entity mapped to the sys_user database table.
 *
 * @description Stores user credentials and profile information for the authentication system.
 *              Password hashes are stored using BCrypt.
 * @author Jiangbo Li
 * @date 2025-07-12
 * @version 1.0
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
    private String username;
    private String passwordHash;
    private String nickname;
    private String email;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginAt;
}
