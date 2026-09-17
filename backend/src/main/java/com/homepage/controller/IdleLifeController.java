package com.homepage.controller;

import com.homepage.model.IdleLifeSave;
import com.homepage.service.IdleLifeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 挂机生活 - 控制器
 * 游客接口 /api/idle-life/guest 公开，其余接口需要登录。
 */
@RestController
@RequestMapping("/api/idle-life")
public class IdleLifeController {

    private final IdleLifeService service;

    public IdleLifeController(IdleLifeService service) {
        this.service = service;
    }

    /** 创建游客账号并自动登录 */
    @GetMapping("/guest")
    public ResponseEntity<Map<String, Object>> guest() {
        Map<String, Object> result = service.createGuest();
        if ((boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    /** 获取/创建当前用户存档 */
    @GetMapping("/save")
    public ResponseEntity<Map<String, Object>> save(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String username = (String) auth.getCredentials();
        IdleLifeSave save = service.getOrCreateSave(userId, username);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("save", service.saveView(save));
        return ResponseEntity.ok(result);
    }

    /** 游戏配置（副本/药水等） */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.putAll(service.configView());
        return ResponseEntity.ok(result);
    }

    /** 背包 */
    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> inventory(Authentication auth) {
        String username = (String) auth.getCredentials();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.putAll(service.inventoryView(username));
        return ResponseEntity.ok(result);
    }

    /** 穿装备 */
    @PostMapping("/equip")
    public ResponseEntity<Map<String, Object>> equip(@RequestBody Map<String, String> body, Authentication auth) {
        String username = (String) auth.getCredentials();
        String itemKey = body.get("itemKey");
        if (itemKey == null || itemKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少 itemKey"));
        }
        return ResponseEntity.ok(service.equip(username, itemKey));
    }

    /** 脱装备 */
    @PostMapping("/unequip")
    public ResponseEntity<Map<String, Object>> unequip(@RequestBody Map<String, String> body, Authentication auth) {
        String username = (String) auth.getCredentials();
        String slot = body.get("slot");
        if (slot == null || slot.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少 slot"));
        }
        return ResponseEntity.ok(service.unequip(username, slot));
    }

    /** 设置战斗药水 */
    @PostMapping("/potion")
    public ResponseEntity<Map<String, Object>> potion(@RequestBody Map<String, String> body, Authentication auth) {
        String username = (String) auth.getCredentials();
        return ResponseEntity.ok(service.setPotion(username, body.get("potionKey")));
    }

    /** 开始挂机（进入副本） */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody Map<String, String> body, Authentication auth) {
        String username = (String) auth.getCredentials();
        int dungeonId;
        try {
            dungeonId = Integer.parseInt(body.get("dungeonId"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "副本参数错误"));
        }
        return ResponseEntity.ok(service.startBattle(username, dungeonId));
    }

    /** 停止挂机（离开副本） */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(Authentication auth) {
        String username = (String) auth.getCredentials();
        return ResponseEntity.ok(service.stopBattle(username));
    }

    /** 执行一场战斗 */
    @PostMapping("/battle")
    public ResponseEntity<Map<String, Object>> battle(@RequestBody Map<String, String> body, Authentication auth) {
        String username = (String) auth.getCredentials();
        int dungeonId;
        try {
            dungeonId = Integer.parseInt(body.get("dungeonId"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "副本参数错误"));
        }
        return ResponseEntity.ok(service.battle(username, dungeonId));
    }
}
