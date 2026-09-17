package com.homepage.repository;

import com.homepage.model.Score;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 贪吃蛇分数数据访问层
 */
@Repository
public class ScoreRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<Score> rowMapper = (rs, rowNum) -> {
        Score s = new Score();
        s.setId(rs.getLong("id"));
        s.setPlayerName(rs.getString("player_name"));
        s.setScore(rs.getInt("score"));

        Timestamp st = rs.getTimestamp("start_time");
        if (st != null) s.setStartTime(st.toLocalDateTime());

        Timestamp et = rs.getTimestamp("end_time");
        if (et != null) s.setEndTime(et.toLocalDateTime());

        s.setDurationSeconds(rs.getLong("duration_seconds"));

        Timestamp ct = rs.getTimestamp("created_at");
        if (ct != null) s.setCreatedAt(ct.toLocalDateTime());

        return s;
    };

    public ScoreRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 保存分数 */
    public Score save(Score s) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO snake_scores (player_name, score, start_time, end_time, duration_seconds) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, s.getPlayerName());
            ps.setInt(2, s.getScore());
            ps.setTimestamp(3, s.getStartTime() != null ? Timestamp.valueOf(s.getStartTime()) : null);
            ps.setTimestamp(4, s.getEndTime() != null ? Timestamp.valueOf(s.getEndTime()) : null);
            ps.setLong(5, s.getDurationSeconds());
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id != null) s.setId(id.longValue());
        return s;
    }

    /** 获取前 N 名（排除被封禁玩家） */
    public List<Score> findTop(int limit) {
        return jdbc.query(
            "SELECT s.* FROM snake_scores s " +
            "LEFT JOIN banned_players b ON s.player_name COLLATE utf8mb4_general_ci = b.player_name COLLATE utf8mb4_general_ci " +
            "WHERE b.player_name IS NULL " +
            "ORDER BY s.score DESC LIMIT ?",
            rowMapper, limit
        );
    }

    /** 获取所有分数（管理员用，排除被封禁的） */
    public List<Score> findAll() {
        return jdbc.query(
            "SELECT s.* FROM snake_scores s " +
            "LEFT JOIN banned_players b ON s.player_name COLLATE utf8mb4_general_ci = b.player_name COLLATE utf8mb4_general_ci " +
            "WHERE b.player_name IS NULL " +
            "ORDER BY s.score DESC",
            rowMapper
        );
    }

    /** 删除指定分数 */
    public void deleteById(Long id) {
        jdbc.update("DELETE FROM snake_scores WHERE id = ?", id);
    }

    /** 删除某玩家所有分数 */
    public void deleteByPlayerName(String playerName) {
        jdbc.update("DELETE FROM snake_scores WHERE player_name = ?", playerName);
    }
}
