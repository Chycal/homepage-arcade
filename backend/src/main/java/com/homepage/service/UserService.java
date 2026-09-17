package com.homepage.service;

import com.homepage.config.JwtUtil;
import com.homepage.model.User;
import com.homepage.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用户服务 - 登录/注册/密码管理
 * 安全策略:
 * - BCrypt 强度 12 (约0.3秒/hash，有效抵御暴力破解)
 * - 5次失败锁定15分钟
 * - 密码最小长度8位
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    public UserService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    /**
     * 注册新用户
     */
    public Map<String, Object> register(String username, String password) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 用户名校验
        if (username == null || username.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "用户名不能为空");
            return result;
        }
        username = username.trim();
        if (username.length() < 3 || username.length() > 50) {
            result.put("success", false);
            result.put("message", "用户名长度需在3-50个字符之间");
            return result;
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            result.put("success", false);
            result.put("message", "用户名只能包含字母、数字和下划线");
            return result;
        }

        // 密码校验
        if (password == null || password.length() < 8) {
            result.put("success", false);
            result.put("message", "密码长度不能少于8位");
            return result;
        }
        if (password.length() > 128) {
            result.put("success", false);
            result.put("message", "密码长度不能超过128位");
            return result;
        }

        // 检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            result.put("success", false);
            result.put("message", "用户名已被注册");
            return result;
        }

        // 创建用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setEnabled(true);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);

        // 生成 Token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        result.put("success", true);
        result.put("message", "注册成功");
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return result;
    }

    /**
     * 用户登录（含防暴力破解锁定机制）
     */
    public Map<String, Object> login(String username, String password) {
        Map<String, Object> result = new LinkedHashMap<>();

        Optional<User> optUser = userRepository.findByUsername(username);

        // 用户不存在 - 使用统一错误信息防止用户枚举
        if (optUser.isEmpty()) {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            return result;
        }

        User user = optUser.get();

        // 检查账号是否被禁用
        if (!user.isEnabled()) {
            result.put("success", false);
            result.put("message", "账号已被禁用，请联系管理员");
            return result;
        }

        // 检查是否被锁定
        if (user.isLocked()) {
            result.put("success", false);
            result.put("message", "账号已被临时锁定，请" + LOCK_DURATION_MINUTES + "分钟后再试");
            return result;
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            int newAttempts = user.getFailedAttempts() + 1;
            LocalDateTime lockUntil = null;

            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
            }
            userRepository.updateFailedAttempts(user.getId(), newAttempts, lockUntil);

            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                result.put("success", false);
                result.put("message", "密码错误次数过多，账号已被临时锁定" + LOCK_DURATION_MINUTES + "分钟");
            } else {
                result.put("success", false);
                result.put("message", "用户名或密码错误（剩余尝试次数: " + (MAX_FAILED_ATTEMPTS - newAttempts) + "）");
            }
            return result;
        }

        // 登录成功 - 重置失败计数
        userRepository.updateFailedAttempts(user.getId(), 0, null);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        result.put("success", true);
        result.put("message", "登录成功");
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return result;
    }

    /**
     * 刷新 Access Token
     */
    public Map<String, Object> refresh(String refreshToken) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Long userId = jwtUtil.validateRefreshToken(refreshToken);
            Optional<User> optUser = userRepository.findById(userId);
            if (optUser.isEmpty()) {
                result.put("success", false);
                result.put("message", "用户不存在");
                return result;
            }
            User user = optUser.get();
            if (!user.isEnabled()) {
                result.put("success", false);
                result.put("message", "账号已被禁用");
                return result;
            }

            String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());

            result.put("success", true);
            result.put("accessToken", newAccessToken);
            result.put("refreshToken", newRefreshToken);
            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Refresh Token 无效或已过期");
            return result;
        }
    }

    /**
     * 获取当前用户信息
     */
    public Map<String, Object> getUserInfo(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        User user = optUser.get();
        result.put("success", true);
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        result.put("createdAt", user.getCreatedAt().toString());
        return result;
    }
}
