package com.homepage.lasvegas.repository;

import com.homepage.lasvegas.model.LasVegasMatchSummary;
import com.homepage.lasvegas.model.LasVegasPlayerResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 拉斯维加斯战绩数据访问层。
 *
 * 表结构由 {@code DataInitializer.initSchema()} 以 CREATE TABLE IF NOT EXISTS 建立。
 */
@Repository
public class LasVegasRepository {

    private final JdbcTemplate jdbc;

    public LasVegasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 新建一局记录，返回自增主键 */
    public long createMatch(String roomCode, int playerCount, int rounds,
                            String winnerIds, LocalDateTime startedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO lasvegas_match " +
                "(room_code, player_count, rounds, winner_ids, started_at, finished_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, roomCode);
            ps.setInt(2, playerCount);
            ps.setInt(3, rounds);
            ps.setString(4, winnerIds);
            ps.setTimestamp(5, Timestamp.valueOf(startedAt));
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("战绩写入失败：未取得主键");
        }
        return id.longValue();
    }

    /** 写入一名玩家的成绩 */
    public void addPlayerResult(long matchId, LasVegasPlayerResult result) {
        jdbc.update(
            "INSERT INTO lasvegas_player " +
            "(match_id, user_id, username, seat, money, banknotes, is_winner) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            matchId,
            result.getUserId(),
            result.getUsername(),
            result.getSeat(),
            result.getMoney(),
            result.getBanknotes(),
            result.isWinner() ? 1 : 0);
    }

    /**
     * 查询指定玩家参与过的对局（最近在前），每局附带完整排名。
     */
    public List<LasVegasMatchSummary> findMatchesByUser(long userId, int limit) {
        List<LasVegasMatchSummary> matches = jdbc.query(
            "SELECT DISTINCT m.id, m.room_code, m.player_count, m.rounds, " +
            "       m.started_at, m.finished_at " +
            "FROM lasvegas_match m " +
            "JOIN lasvegas_player p ON p.match_id = m.id " +
            "WHERE p.user_id = ? " +
            "ORDER BY m.id DESC LIMIT ?",
            new Object[]{userId, limit},
            (rs, rowNum) -> {
                LasVegasMatchSummary summary = new LasVegasMatchSummary();
                summary.setMatchId(rs.getLong("id"));
                summary.setRoomCode(rs.getString("room_code"));
                summary.setPlayerCount(rs.getInt("player_count"));
                summary.setRounds(rs.getInt("rounds"));
                Timestamp started = rs.getTimestamp("started_at");
                if (started != null) {
                    summary.setStartedAt(started.toLocalDateTime());
                }
                Timestamp finished = rs.getTimestamp("finished_at");
                if (finished != null) {
                    summary.setFinishedAt(finished.toLocalDateTime());
                }
                return summary;
            });

        for (LasVegasMatchSummary match : matches) {
            match.getStandings().addAll(findPlayersByMatch(match.getMatchId()));
            fillMyResult(match, userId);
        }
        return matches;
    }

    /** 某局全部玩家的成绩，按金额、钞票数降序 */
    public List<LasVegasPlayerResult> findPlayersByMatch(long matchId) {
        List<LasVegasPlayerResult> results = new ArrayList<>(jdbc.query(
            "SELECT user_id, username, seat, money, banknotes, is_winner " +
            "FROM lasvegas_player WHERE match_id = ? " +
            "ORDER BY money DESC, banknotes DESC, seat ASC",
            new Object[]{matchId},
            (rs, rowNum) -> {
                // user_id 允许为 NULL（游客），必须用 getObject 判断，否则 NULL 会变成 0
                Object rawUserId = rs.getObject("user_id");
                Long userId = rawUserId == null ? null : ((Number) rawUserId).longValue();
                return new LasVegasPlayerResult(
                    userId,
                    rs.getString("username"),
                    rs.getInt("seat"),
                    rs.getInt("money"),
                    rs.getInt("banknotes"),
                    rs.getInt("is_winner") == 1);
            }));
        return results;
    }

    /** 总对局数（用于展示） */
    public long totalMatches() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM lasvegas_match", Long.class);
        return count != null ? count : 0;
    }

    /**
     * 计算并填充「我」在这局的名次与成绩。
     *
     * 名次 = 在 standings 中的位置（从 1 开始）。standings 的顺序由
     * {@link #findPlayersByMatch} 的 SQL 决定：{@code ORDER BY money DESC, banknotes DESC, seat ASC}。
     * 这条 tie-break 链就是业务规则（先比钱、再比钞票张数、最后按座位），
     * 与 {@link com.homepage.lasvegas.LasVegasScoring} 的判定口径必须一致，改一处要同步另一处。
     *
     * 注意 {@code rank}（名次）与 {@code LasVegasPlayerResult.seat}（座位号）是两个概念，别混用。
     */
    private void fillMyResult(LasVegasMatchSummary match, long userId) {
        int rank = 1;
        for (LasVegasPlayerResult standing : match.getStandings()) {
            if (standing.getUserId() != null && standing.getUserId() == userId) {
                match.setMyRank(rank);
                match.setMyMoney(standing.getMoney());
                match.setMyBanknotes(standing.getBanknotes());
                match.setIWon(standing.isWinner());
                return;
            }
            rank++;
        }
    }
}
