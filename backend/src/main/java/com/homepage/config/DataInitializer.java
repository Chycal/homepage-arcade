package com.homepage.config;

import com.homepage.model.User;
import com.homepage.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据初始化器 - 在应用启动时创建数据库表和管理员账号
 *
 * 全部表使用 CREATE TABLE IF NOT EXISTS，全新数据库可直接启动；
 * 管理员凭据通过配置提供（环境变量 ADMIN_USERNAME / ADMIN_PASSWORD
 * 或 gitignore 掉的 application-local.yml），不写入代码库。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public DataInitializer(UserRepository userRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        initSchema();
        initAdminAccount();
    }

    /** 初始化管理员账号（凭据来自配置，未配置时跳过） */
    private void initAdminAccount() {
        if (adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.warn("未配置 app.admin.username / app.admin.password，跳过默认管理员初始化");
            return;
        }

        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            admin.setFailedAttempts(0);
            admin.setCreatedAt(LocalDateTime.now());
            userRepository.save(admin);
            log.info("管理员账号已创建: username={}", adminUsername);
        } else {
            log.info("管理员账号已存在: username={}", adminUsername);
        }
    }

    /** 初始化数据库表结构 */
    private void initSchema() {
        try {
            // 用户表
            jdbc.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(50) NOT NULL, " +
                "password VARCHAR(255) NOT NULL, " +
                "role VARCHAR(20) NOT NULL DEFAULT 'USER', " +
                "enabled TINYINT(1) NOT NULL DEFAULT 1, " +
                "locked_until DATETIME NULL, " +
                "failed_attempts INT NOT NULL DEFAULT 0, " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_username (username)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            log.info("users 表已就绪");

            // 访问记录表
            jdbc.execute("CREATE TABLE IF NOT EXISTS visitor_log (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "ip VARCHAR(45) NOT NULL, " +
                "page VARCHAR(100) DEFAULT '/', " +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "KEY idx_visitor_ip (ip)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            log.info("visitor_log 表已就绪");
        } catch (Exception e) {
            log.warn("数据库表初始化警告（可能已存在）: {}", e.getMessage());
        }
    }
}
