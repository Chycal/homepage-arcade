package com.homepage.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易内存 IP 限流器
 * — 每个 IP 每分钟最多 N 次请求
 * — 每个 IP 每小时最多 M 次请求
 */
@Component
public class IpRateLimiter {

    private static final int MAX_PER_MINUTE = 5;
    private static final int MAX_PER_HOUR  = 20;

    // IP → 最近一次窗口内的时间戳列表
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static class Window {
        long minuteStart; // 当前分钟窗口起点(秒)
        int minuteCount;
        long hourStart;   // 当前小时窗口起点(秒)
        int hourCount;

        Window(long now) {
            this.minuteStart = now / 60 * 60;
            this.hourStart  = now / 3600 * 3600;
            this.minuteCount = 0;
            this.hourCount  = 0;
        }
    }

    /**
     * 尝试放行一次请求。放行返回 true，拒绝返回 false。
     */
    public boolean tryAcquire(String ip) {
        long now = Instant.now().getEpochSecond();

        Window w = windows.compute(ip, (k, old) -> {
            if (old == null) {
                Window nw = new Window(now);
                nw.minuteCount = 1;
                nw.hourCount  = 1;
                return nw;
            }

            // 当前分钟窗口
            long currentMinute = now / 60 * 60;
            if (currentMinute != old.minuteStart) {
                old.minuteStart = currentMinute;
                old.minuteCount = 0;
            }

            // 当前小时窗口
            long currentHour = now / 3600 * 3600;
            if (currentHour != old.hourStart) {
                old.hourStart = currentHour;
                old.hourCount = 0;
            }

            old.minuteCount++;
            old.hourCount++;
            return old;
        });

        return w.minuteCount <= MAX_PER_MINUTE && w.hourCount <= MAX_PER_HOUR;
    }

    /**
     * 定期清理过期窗口（由外部定时触发，非必须但可防止 map 膨胀）
     */
    public void evictExpired() {
        long now = Instant.now().getEpochSecond();
        long currentHour = now / 3600 * 3600;
        windows.entrySet().removeIf(e -> e.getValue().hourStart != currentHour);
    }
}
