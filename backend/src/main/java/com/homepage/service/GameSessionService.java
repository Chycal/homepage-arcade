package com.homepage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏会话管理
 *
 * - 每局开始前获取 session，返回 sessionId + 第一波食物（种子保密）
 * - 游戏过程中，客户端逐次请求后续食物
 * - 提交时只用 sessionId + moves，服务端用机密种子自动计算分数
 * - 一把一密，过期自动清理（10 分钟）
 */
@Service
public class GameSessionService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionService.class);

    private static final long SESSION_TTL_SECONDS = 600; // 10 分钟
    private static final int MAX_SESSIONS = 5000;

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /** 游戏会话——种子仅为服务端可见 */
    public static class Session {
        public final String id;
        public final int seed;         // 服务端机密种子，永不发送给客户端
        public final long createdAt;
        public boolean used;          // 是否已完成提交
        public int foodsServed;       // 已下发了几次食物（初始 + 后续 refill）

        Session(String id, int seed) {
            this.id = id;
            this.seed = seed;
            this.createdAt = System.currentTimeMillis();
            this.used = false;
            this.foodsServed = 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > SESSION_TTL_SECONDS * 1000L;
        }
    }

    /** 创建会话 */
    public Session createSession() {
        if (sessions.size() >= MAX_SESSIONS) {
            evictExpired();
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        int seed = secureRandom.nextInt();
        Session s = new Session(id, seed);
        sessions.put(id, s);
        return s;
    }

    /** 按 id 查找会话（不消耗），用于 refill 中途查询 */
    public Session lookup(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null || s.isExpired()) {
            return null;
        }
        return s;
    }

    /** 提交时消耗会话（一次性使用） */
    public Session consumeSession(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null || s.isExpired() || s.used) {
            return null;
        }
        s.used = true;
        return s;
    }

    @Scheduled(fixedRate = 60000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> {
            Session s = e.getValue();
            return s.used || (now - s.createdAt > SESSION_TTL_SECONDS * 1000L);
        });
    }
}
