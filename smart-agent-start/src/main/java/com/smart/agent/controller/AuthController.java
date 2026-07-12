package com.smart.agent.controller;

import com.smart.agent.model.ServiceResponse;
import com.smart.agent.service.UserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller.
 *
 * @description Provides user registration, login, and JWT token verification endpoints.
 * @author Jiangbo Li
 * @date 2025-07-12
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ServiceResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank() ||
                request.getPassword() == null || request.getPassword().isBlank()) {
            return ServiceResponse.failed("用户名和密码不能为空", null);
        }
        if (request.getPassword().length() < 6) {
            return ServiceResponse.failed("密码至少需要6个字符", null);
        }
        Map<String, Object> result = userService.register(
                request.getUsername(), request.getPassword(), request.getNickname());
        if (result == null) {
            return ServiceResponse.failed("用户名已存在", null);
        }
        log.info("User registered: {}", request.getUsername());
        return ServiceResponse.success(result);
    }

    @PostMapping("/login")
    public ServiceResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> result = userService.login(request.getUsername(), request.getPassword());
        if (result == null) {
            return ServiceResponse.failed("用户名或密码错误", null);
        }
        log.info("User logged in: {}", request.getUsername());
        return ServiceResponse.success(result);
    }

    @GetMapping("/verify")
    public ServiceResponse<Map<String, Object>> verify(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ServiceResponse.failed("无效的Token", null);
        }
        String token = authHeader.substring(7);
        Map<String, Object> result = userService.verifyToken(token);
        if (result == null) {
            return ServiceResponse.failed("Token已过期或无效", null);
        }
        return ServiceResponse.success(result);
    }

    @Data
    public static class RegisterRequest {
        private String username;
        private String password;
        private String nickname;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
