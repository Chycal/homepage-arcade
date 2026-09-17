package com.homepage.repository;

import com.homepage.model.BannedPlayer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 封禁玩家数据访问层
 */
@Repository
public class BannedPlayerRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<BannedPlayer> rowMapper = (rs, rowNum) -> {
        BannedPlayer bp = new BannedPlayer();
        bp.setId(rs.getLong("id"));
        bp.setPlayerName(rs.getString("player_name"));
        Timestamp bt = rs.getTimestamp("banned_at");
        bp.setBannedAt(bt != null ? bt.toLocalDateTime() : null);
        bp.setBannedBy(rs.getString("banned_by"));
        return bp;
    };

    public BannedPlayerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 添加封禁 */
    public BannedPlayer save(BannedPlayer bp) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO banned_players (player_name, banned_at, banned_by) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, bp.getPlayerName());
            ps.setTimestamp(2, Timestamp.valueOf(bp.getBannedAt() != null ? bp.getBannedAt() : LocalDateTime.now()));
            ps.setString(3, bp.getBannedBy());
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id != null) bp.setId(id.longValue());
        return bp;
    }

    /** 移除封禁 */
    public void deleteByPlayerName(String playerName) {
        jdbc.update("DELETE FROM banned_players WHERE player_name COLLATE utf8mb4_general_ci = ?", playerName);
    }

    /** 检查是否被封禁 */
    public boolean isBanned(String playerName) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM banned_players WHERE player_name COLLATE utf8mb4_general_ci = ?", Integer.class, playerName
        );
        return count != null && count > 0;
    }

    /** 获取所有封禁列表 */
    public List<BannedPlayer> findAll() {
        return jdbc.query("SELECT id, player_name, banned_at, banned_by FROM banned_players ORDER BY banned_at DESC", rowMapper);
    }
}
