package com.homepage.repository;

import com.homepage.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 用户数据访问层。
 *
 * 用 {@link JdbcTemplate} 手写 SQL（未用 JPA），所以 {@code users} 表的列名与
 * {@link User} 字段的对应关系全部显式写在 {@link #rowMapper} 和 {@link #save} 里，
 * 改表结构时这两处必须同步。
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    /** users 表行 → {@link User}。locked_until 允许为 NULL，表示未锁定 */
    private final RowMapper<User> rowMapper = (rs, rowNum) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        u.setEnabled(rs.getBoolean("enabled"));
        Timestamp lt = rs.getTimestamp("locked_until");
        u.setLockedUntil(lt != null ? lt.toLocalDateTime() : null);
        u.setFailedAttempts(rs.getInt("failed_attempts"));
        u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return u;
    };

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按用户名查用户，登录与注册查重都走这里 */
    public Optional<User> findByUsername(String username) {
        var list = jdbc.query("SELECT * FROM users WHERE username = ?", rowMapper, username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 按主键查用户 */
    public Optional<User> findById(Long id) {
        var list = jdbc.query("SELECT * FROM users WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 插入新用户，并回填自增主键。
     *
     * created_at 为空时用当前时间兜底，保证 NOT NULL 列不会插入失败。
     */
    public User save(User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO users (username, password, role, enabled, locked_until, failed_attempts, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRole());
            ps.setBoolean(4, user.isEnabled());
            ps.setTimestamp(5, user.getLockedUntil() != null ? Timestamp.valueOf(user.getLockedUntil()) : null);
            ps.setInt(6, user.getFailedAttempts());
            ps.setTimestamp(7, Timestamp.valueOf(user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now()));
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id != null) user.setId(id.longValue());
        return user;
    }

    /** 记录一次登录失败，必要时同时写入锁定截止时间（lockedUntil 为 null 表示不锁定） */
    public void updateFailedAttempts(Long id, int attempts, LocalDateTime lockedUntil) {
        jdbc.update("UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?",
                attempts,
                lockedUntil != null ? Timestamp.valueOf(lockedUntil) : null,
                id);
    }

    /**
     * 更新密码。
     *
     * 目前没有调用方（改密码功能尚未实现）。调用方必须先自行做 BCrypt 编码——
     * 本方法只负责写库，不做任何哈希处理。
     */
    public void updatePassword(Long id, String encodedPassword) {
        jdbc.update("UPDATE users SET password = ? WHERE id = ?", encodedPassword, id);
    }
}
