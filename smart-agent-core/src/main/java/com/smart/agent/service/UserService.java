package com.smart.agent.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.agent.persistence.entity.SysUser;
import com.smart.agent.persistence.mapper.SysUserMapper;
import com.smart.agent.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service layer for user registration, login, and JWT token verification.
 *
 * @description Handles user authentication with BCrypt password hashing and JWT token generation.
 *              Supports register, login, and token verification operations.
 * @author Jiangbo Li
 * @date 2025-07-12
 * @version 1.0
 */
@Slf4j
@Service
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtils jwtUtils;

    public UserService(SysUserMapper sysUserMapper, JwtUtils jwtUtils) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Register a new user.
     * @return token + user info, or null if username already exists
     */
    public Map<String, Object> register(String username, String password, String nickname) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        if (sysUserMapper.selectCount(wrapper) > 0) {
            return null;
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.withDefaults().hashToString(12, password.toCharArray()));
        user.setNickname(nickname != null ? nickname : username);
        user.setStatus(1);
        sysUserMapper.insert(user);

        String token = jwtUtils.generateToken(String.valueOf(user.getId()), user.getUsername());
        return Map.of("token", token, "userId", String.valueOf(user.getId()),
                "username", user.getUsername(), "nickname", user.getNickname());
    }

    /**
     * Login with username and password.
     * @return token + user info, or null if credentials invalid
     */
    public Map<String, Object> login(String username, String password) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = sysUserMapper.selectOne(wrapper);

        if (user == null || user.getStatus() == 0) {
            return null;
        }

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            return null;
        }

        user.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String token = jwtUtils.generateToken(String.valueOf(user.getId()), user.getUsername());
        return Map.of("token", token, "userId", String.valueOf(user.getId()),
                "username", user.getUsername(), "nickname", user.getNickname());
    }

    /**
     * Verify JWT token and return user info.
     */
    public Map<String, Object> verifyToken(String token) {
        if (!jwtUtils.validateToken(token)) {
            return null;
        }
        String userId = jwtUtils.getUserIdFromToken(token);
        SysUser user = sysUserMapper.selectById(Long.parseLong(userId));
        if (user == null || user.getStatus() == 0) {
            return null;
        }
        return Map.of("userId", userId, "username", user.getUsername(), "nickname", user.getNickname());
    }

    public SysUser getById(Long id) {
        return sysUserMapper.selectById(id);
    }
}
