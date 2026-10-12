package com.homepage.lasvegas.controller;

import com.homepage.lasvegas.model.LasVegasMatchSummary;
import com.homepage.lasvegas.repository.LasVegasRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拉斯维加斯战绩接口。
 *
 * /api/lasvegas/** 受 SecurityConfig 的 /api/** 规则保护，必须登录。
 * 只能看到「自己参与过」的对局。
 */
@RestController
@RequestMapping("/api/lasvegas")
public class LasVegasController {

    private static final int MAX_LIMIT = 100;

    private final LasVegasRepository repository;

    public LasVegasController(LasVegasRepository repository) {
        this.repository = repository;
    }

    /** 我的战绩列表（我参与过的对局，最近在前） */
    @GetMapping("/matches")
    public ResponseEntity<Map<String, Object>> myMatches(
            Authentication auth,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        Long userId = currentUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<LasVegasMatchSummary> matches = repository.findMatchesByUser(userId, safeLimit);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("matches", matches);
        result.put("total", matches.size());
        return ResponseEntity.ok(result);
    }

    /** 我的汇总战绩 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> myStats(Authentication auth) {
        Long userId = currentUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录"));
        }
        List<LasVegasMatchSummary> matches = repository.findMatchesByUser(userId, MAX_LIMIT);

        int wins = 0;
        int totalMoney = 0;
        for (LasVegasMatchSummary match : matches) {
            if (match.isIWon()) {
                wins++;
            }
            totalMoney += match.getMyMoney();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("games", matches.size());
        result.put("wins", wins);
        result.put("totalMoney", totalMoney);
        result.put("totalMatches", repository.totalMatches());
        return ResponseEntity.ok(result);
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return null;
        }
        return (Long) auth.getPrincipal();
    }
}
