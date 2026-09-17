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
 * 用户数据访问层
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

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

    public Optional<User> findByUsername(String username) {
        var list = jdbc.query("SELECT * FROM users WHERE username = ?", rowMapper, username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<User> findById(Long id) {
        var list = jdbc.query("SELECT * FROM users WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

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

    public void updateFailedAttempts(Long id, int attempts, LocalDateTime lockedUntil) {
        jdbc.update("UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?",
                attempts,
                lockedUntil != null ? Timestamp.valueOf(lockedUntil) : null,
                id);
    }

    public void updatePassword(Long id, String encodedPassword) {
        jdbc.update("UPDATE users SET password = ? WHERE id = ?", encodedPassword, id);
    }
}
