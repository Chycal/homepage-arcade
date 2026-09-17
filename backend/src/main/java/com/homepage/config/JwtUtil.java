package com.homepage.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类 - HMAC-SHA512 签名
 * Access Token: 15分钟
 * Refresh Token: 7天
 */
@Component
public class JwtUtil {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;

    private static final long ACCESS_EXPIRATION = 15 * 60 * 1000L;       // 15分钟
    private static final long REFRESH_EXPIRATION = 7 * 24 * 60 * 60 * 1000L; // 7天

    public JwtUtil(
            @Value("${jwt.access-secret:}") String accessSecret,
            @Value("${jwt.refresh-secret:}") String refreshSecret) {

        // 如果没有配置，自动生成高熵密钥（每次重启会变，仅适合开发环境）
        if (accessSecret == null || accessSecret.isEmpty()) {
            this.accessKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(
                accessSecret.getBytes(StandardCharsets.UTF_8));
            this.accessKey = Keys.hmacShaKeyFor(keyBytes);
        }

        if (refreshSecret == null || refreshSecret.isEmpty()) {
            this.refreshKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(
                refreshSecret.getBytes(StandardCharsets.UTF_8));
            this.refreshKey = Keys.hmacShaKeyFor(keyBytes);
        }
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(Long userId, String username, String role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION))
                .signWith(accessKey)
                .compact();
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRATION))
                .signWith(refreshKey)
                .compact();
    }

    /**
     * 验证 Access Token 并返回 Claims
     */
    public Claims validateAccessToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(accessKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证 Refresh Token 并返回 userId
     */
    public Long validateRefreshToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(refreshKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 中提取用户ID
     */
    public Long getUserIdFromToken(String token) {
        return Long.parseLong(validateAccessToken(token).getSubject());
    }

    /**
     * 从 Token 中提取角色
     */
    public String getRoleFromToken(String token) {
        return validateAccessToken(token).get("role", String.class);
    }

    /**
     * 从 Token 中提取用户名
     */
    public String extractUsername(String token) {
        return validateAccessToken(token).get("username", String.class);
    }

    /**
     * 从 Token 中提取角色（别名）
     */
    public String extractRole(String token) {
        return validateAccessToken(token).get("role", String.class);
    }
}
