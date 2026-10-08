package com.homepage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 * - JWT 无状态认证
 * - API 权限分级
 * - 静态资源及公开接口允许匿名访问
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（使用 JWT 无状态认证，不需要 CSRF）
            .csrf().disable()
            // 无状态 Session
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            // 权限配置
            .authorizeRequests()
                // 认证接口 - 公开
                .antMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                // 访客统计 - 公开
                .antMatchers("/api/visitor/**").permitAll()
                // 首页内容 - 公开
                .antMatchers("/api/profile", "/api/skills", "/api/projects", "/api/health").permitAll()
                // WebSocket
                .antMatchers("/ws/**").permitAll()
                // 静态资源 - 公开
                .antMatchers("/", "/index.html", "/favicon.ico", "/favicon.svg").permitAll()
                .antMatchers("/css/**", "/js/**", "/assets/**", "/fonts/**", "/images/**").permitAll()
                // 其他 API 需要认证
                .antMatchers("/api/**").authenticated()
                // 其他请求允许（SPA fallback）
                .anyRequest().permitAll()
            .and()
            // 添加 JWT 过滤器
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);
    }
}
