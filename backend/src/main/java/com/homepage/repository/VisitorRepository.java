package com.homepage.repository;

import com.homepage.model.VisitorRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * 访问记录数据访问层
 */
@Repository
public class VisitorRepository {

    private final JdbcTemplate jdbc;

    public VisitorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 保存访问记录
     */
    public VisitorRecord save(VisitorRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO visitor_log (ip, page) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, record.getIp());
            ps.setString(2, record.getPage());
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        if (id != null) {
            record.setId(id.longValue());
        }
        return record;
    }

    /**
     * 获取总访问量（总 PV）
     */
    public long totalPageViews() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM visitor_log", Long.class);
        return count != null ? count : 0;
    }

    /**
     * 获取独立访客数（去重 IP）
     */
    public long uniqueVisitors() {
        Long count = jdbc.queryForObject("SELECT COUNT(DISTINCT ip) FROM visitor_log", Long.class);
        return count != null ? count : 0;
    }

    /**
     * 获取今日访问量
     */
    public long todayPageViews() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM visitor_log WHERE DATE(created_at) = CURDATE()", Long.class);
        return count != null ? count : 0;
    }

    /**
     * 获取今日独立访客数
     */
    public long todayUniqueVisitors() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT ip) FROM visitor_log WHERE DATE(created_at) = CURDATE()", Long.class);
        return count != null ? count : 0;
    }
}
