package com.homepage.controller;

import com.homepage.model.BannedPlayer;
import com.homepage.model.Score;
import com.homepage.repository.BannedPlayerRepository;
import com.homepage.repository.ScoreRepository;
import com.homepage.config.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员控制器 —— 管理排行榜作弊账号封禁
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ScoreRepository scoreRepo;
    private final BannedPlayerRepository bannedPlayerRepo;
    private final JwtUtil jwtUtil;

    public AdminController(ScoreRepository scoreRepo, BannedPlayerRepository bannedPlayerRepo, JwtUtil jwtUtil) {
        this.scoreRepo = scoreRepo;
        this.bannedPlayerRepo = bannedPlayerRepo;
        this.jwtUtil = jwtUtil;
    }

    /** 验证是否是管理员 */
    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        String token = authHeader.substring(7);
        try {
            String role = jwtUtil.extractRole(token);
            return "ADMIN".equals(role);
        } catch (Exception e) {
            return false;
        }
    }

    /** 从 Authorization Header 提取管理员用户名 */
    private String getAdminUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return "unknown";
        String token = authHeader.substring(7);
        return jwtUtil.extractUsername(token);
    }

    /** 检查管理员权限 */
    private ResponseEntity<?> checkAdmin(String authHeader) {
        if (!isAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "需要管理员权限"));
        }
        return null;
    }

    /** 获取所有排行榜记录 */
    @GetMapping("/scores")
    public ResponseEntity<?> getAllScores(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> check = checkAdmin(authHeader);
        if (check != null) return check;

        List<Score> scores = scoreRepo.findAll();
        return ResponseEntity.ok(Map.of("scores", scores));
    }

    /** 封禁某个玩家 */
    @PostMapping("/ban")
    public ResponseEntity<?> banPlayer(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                        @RequestBody Map<String, String> body) {
        ResponseEntity<?> check = checkAdmin(authHeader);
        if (check != null) return check;

        String playerName = body.get("playerName");
        if (playerName == null || playerName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "玩家名不能为空"));
        }
        playerName = playerName.trim();

        // 不允许封禁管理员
        if ("chycal".equals(playerName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不能封禁管理员账号"));
        }

        String adminUser = getAdminUser(authHeader);

        BannedPlayer bp = new BannedPlayer(playerName, adminUser);
        bannedPlayerRepo.save(bp);

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "已封禁玩家: " + playerName);
        resp.put("playerName", playerName);
        return ResponseEntity.ok(resp);
    }

    /** 解封某个玩家 */
    @PostMapping("/unban")
    public ResponseEntity<?> unbanPlayer(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @RequestBody Map<String, String> body) {
        ResponseEntity<?> check = checkAdmin(authHeader);
        if (check != null) return check;

        String playerName = body.get("playerName");
        if (playerName == null || playerName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "玩家名不能为空"));
        }
        playerName = playerName.trim();

        bannedPlayerRepo.deleteByPlayerName(playerName);

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "已解封玩家: " + playerName);
        resp.put("playerName", playerName);
        return ResponseEntity.ok(resp);
    }

    /** 获取封禁列表 */
    @GetMapping("/banned")
    public ResponseEntity<?> getBannedPlayers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<?> check = checkAdmin(authHeader);
        if (check != null) return check;

        List<BannedPlayer> banned = bannedPlayerRepo.findAll();
        return ResponseEntity.ok(Map.of("banned", banned));
    }

    /** 删除某条分数记录 */
    @DeleteMapping("/scores/{id}")
    public ResponseEntity<?> deleteScore(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @PathVariable Long id) {
        ResponseEntity<?> check = checkAdmin(authHeader);
        if (check != null) return check;

        scoreRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "已删除分数记录 #" + id));
    }
}
