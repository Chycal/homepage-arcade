package com.homepage.controller;

import com.homepage.model.Score;
import com.homepage.repository.BannedPlayerRepository;
import com.homepage.repository.ScoreRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 贪吃蛇分数控制器（已移除服务器反作弊，改用时间戳记录游戏用时）
 */
@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreRepository scoreRepo;
    private final BannedPlayerRepository bannedPlayerRepo;

    public ScoreController(ScoreRepository scoreRepo, BannedPlayerRepository bannedPlayerRepo) {
        this.scoreRepo = scoreRepo;
        this.bannedPlayerRepo = bannedPlayerRepo;
    }

    /** 提交分数（需登录） */
    @PostMapping("/submit")
    public ResponseEntity<?> submitScore(@RequestBody Map<String, Object> body) {
        try {
            // 从 JWT 认证中获取用户名
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String playerName = (String) auth.getCredentials();

            if (playerName == null || playerName.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "请先登录"));
            }
            playerName = playerName.trim();

            // 检查是否被封禁
            if (bannedPlayerRepo.isBanned(playerName)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "该账号已被封禁，无法提交成绩"));
            }

            Object scoreObj = body.get("score");

            int score;
            if (scoreObj instanceof Integer) {
                score = (Integer) scoreObj;
            } else if (scoreObj instanceof Number) {
                score = ((Number) scoreObj).intValue();
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "分数格式错误"));
            }

            if (score < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "分数不能为负数"));
            }

            Score s = new Score();
            s.setPlayerName(playerName);
            s.setScore(score);

            // 处理时间戳（毫秒）
            if (body.containsKey("startTime") && body.get("startTime") != null) {
                long startMs = Long.parseLong(String.valueOf(body.get("startTime")));
                s.setStartTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneId.systemDefault()));
            }
            if (body.containsKey("endTime") && body.get("endTime") != null) {
                long endMs = Long.parseLong(String.valueOf(body.get("endTime")));
                s.setEndTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), ZoneId.systemDefault()));
            }

            // 计算游戏用时
            if (s.getStartTime() != null && s.getEndTime() != null) {
                s.setDurationSeconds(Duration.between(s.getStartTime(), s.getEndTime()).getSeconds());
            }

            scoreRepo.save(s);

            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "成绩提交成功");
            resp.put("id", s.getId());
            resp.put("durationSeconds", s.getDurationSeconds());
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "服务器错误: " + e.getMessage()));
        }
    }

    /** 获取排行榜（排除被封禁玩家） */
    @GetMapping("/top")
    public ResponseEntity<?> getTopScores(@RequestParam(defaultValue = "10") int limit) {
        List<Score> scores = scoreRepo.findTop(limit);
        return ResponseEntity.ok(Map.of("scores", scores));
    }
}
