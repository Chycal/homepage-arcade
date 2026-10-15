package com.homepage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 *
 * 启动时 Spring Boot 会扫描 com.homepage 下的组件；前端静态资源由
 * {@link com.homepage.config.WebMvcConfig} 从 classpath:/static/ 提供。
 *
 * 这里刻意不加 @EnableScheduling：项目里没有 @Scheduled/@Async 用法，
 * 拉斯维加斯的 AI 回合用的是自己按需创建的 ScheduledExecutorService
 * （见 LasVegasRoom.aiExecutor()），不需要全局调度器。
 */
@SpringBootApplication
public class HomepageApplication {

    public static void main(String[] args) {
        SpringApplication.run(HomepageApplication.class, args);
    }
}
