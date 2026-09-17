package com.homepage.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API 控制器
 * 提供后端数据接口，供前端 Vue 调用
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return result;
    }

    /**
     * 获取个人信息
     */
    @GetMapping("/profile")
    public Map<String, Object> profile() {
        Map<String, Object> result = new HashMap<>();
        result.put("name", "chycal");
        result.put("title", "Full Stack Developer");
        result.put("bio", "热爱技术，专注于 Java 和前端开发。");
        result.put("email", "chycal@example.com");
        result.put("github", "https://github.com/chycal");
        return result;
    }

    /**
     * 获取技能列表
     */
    @GetMapping("/skills")
    public Map<String, Object> skills() {
        Map<String, Object> result = new HashMap<>();
        result.put("languages", new String[]{"Java", "JavaScript", "Python", "SQL"});
        result.put("frameworks", new String[]{"Spring Boot", "Vue.js", "React", "MyBatis"});
        result.put("tools", new String[]{"Docker", "Git", "Linux", "Maven"});
        return result;
    }

    /**
     * 获取项目列表
     */
    @GetMapping("/projects")
    public Map<String, Object> projects() {
        Map<String, Object> result = new HashMap<>();
        result.put("projects", new Map[]{
            Map.of("name", "个人首页", "desc", "基于 Spring Boot + Vue3 的个人主页，集成技能展示、项目卡片和实时服务监控", "tech", "Spring Boot, Vue3"),
            Map.of("name", "五子棋对战", "desc", "多人实时五子棋对战，支持观战、AI 对手、悔棋、求和、重开、评论等功能", "tech", "WebSocket, Vue3, Canvas"),
            Map.of("name", "贪吃蛇游戏", "desc", "经典贪吃蛇小游戏，支持键盘和触屏操控，含排行榜", "tech", "Vue3, Canvas"),
            Map.of("name", "24点", "desc", "24点数学游戏，随机四张扑克牌，用加减乘除算出24", "tech", "Vue3, Java"),
            Map.of("name", "自走棋", "desc", "自走棋自动对战，收集英雄、搭配羁绊、对抗AI", "tech", "Vue3, Java")
        });
        return result;
    }
}
