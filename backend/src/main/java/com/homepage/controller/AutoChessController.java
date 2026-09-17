package com.homepage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homepage.model.AutoChessGame;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/autochess")
public class AutoChessController {

    private final ConcurrentHashMap<String, AutoChessGame> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SESSION_DIR = "sessions";
    private static final long SESSION_EXPIRE_MS = 24 * 60 * 60 * 1000L; // 24小时过期

    @PostConstruct
    public void init() {
        // 确保存储目录存在
        try { Files.createDirectories(Paths.get(SESSION_DIR)); } catch (IOException ignored) {}
        try { Files.createDirectories(Paths.get("snapshots")); } catch (IOException ignored) {}
        // 清理过期会话和旧快照
        cleanupExpiredSessions();
        AutoChessGame.cleanupOldSnapshots();
    }

    // ============ API ============

    @GetMapping("/new")
    public Map<String, Object> newGame() {
        String sid = UUID.randomUUID().toString().substring(0, 8);
        AutoChessGame game = new AutoChessGame();
        game.lastAccessTime = System.currentTimeMillis();
        game.gameSessionId = sid;
        sessions.put(sid, game);
        saveToDisk(sid, game);
        Map<String, Object> resp = gameState(sid);
        resp.put("sessionId", sid);
        return resp;
    }

    @GetMapping("/recover")
    public Map<String, Object> recover(@RequestParam String sid) {
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在或已过期，请开始新游戏");
        if ("gameover".equals(game.phase)) return error("该游戏已经结束，请开始新游戏");
        game = tryRestoreCheckpoint(sid, game);
        game.gameSessionId = sid;
        game.lastAccessTime = System.currentTimeMillis();
        sessions.put(sid, game);
        saveToDisk(sid, game);
        Map<String, Object> resp = gameState(sid);
        resp.put("sessionId", sid);
        return resp;
    }

    @GetMapping("/state")
    public Map<String, Object> state(@RequestParam String sid) {
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        game = tryRestoreCheckpoint(sid, game);
        game.lastAccessTime = System.currentTimeMillis();
        return gameState(sid);
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        String err = game.refreshShop();
        if (err != null) return error(err);
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    @PostMapping("/buy")
    public Map<String, Object> buy(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        int slot = ((Number) body.get("slot")).intValue();
        String err = game.buy(slot);
        if (err != null) return error(err);
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    @PostMapping("/sell")
    public Map<String, Object> sell(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        String uid = (String) body.get("uid");
        String err = game.sell(uid);
        if (err != null) return error(err);
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    @PostMapping("/place")
    public Map<String, Object> place(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        String uid = (String) body.get("uid");
        int row = ((Number) body.get("row")).intValue();
        int col = ((Number) body.get("col")).intValue();
        String err = game.placeOnBoard(uid, row, col);
        if (err != null) return error(err);
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    @PostMapping("/tobench")
    public Map<String, Object> toBench(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        String uid = (String) body.get("uid");
        String err = game.moveToBench(uid);
        if (err != null) return error(err);
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    @PostMapping("/levelup")
    public Map<String, Object> levelUp(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        String err = game.buyXp();
        if (err != null) return error(err);
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    @PostMapping("/battle")
    public Map<String, Object> battle(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        // 战斗开始前保存战前存档，以便玩家退出后恢复
        saveCheckpoint(sid, game);
        String err = game.startBattle();
        if (err != null) {
            deleteCheckpoint(sid); // 战斗未开始，删除存档
            return error(err);
        }
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    @PostMapping("/next")
    public Map<String, Object> nextRound(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");

        String msg = game.nextRound();

        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        // 回合已正常推进，清除战前存档
        deleteCheckpoint(sid);
        Map<String, Object> resp = gameState(sid);
        resp.put("msg", msg);
        return resp;
    }


    @PostMapping("/equip")
    public Map<String, Object> equipItem(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        String championUid = (String) body.get("championUid");
        String equipmentId = (String) body.get("equipmentId");
        String err = game.equipItem(championUid, equipmentId);
        if (err != null) return error(err);
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    @PostMapping("/unequip")
    public Map<String, Object> unequipItem(@RequestBody Map<String, Object> body) {
        String sid = (String) body.get("sid");
        AutoChessGame game = getOrLoadSession(sid);
        if (game == null) return error("游戏不存在");
        String championUid = (String) body.get("championUid");
        String equipmentId = (String) body.get("equipmentId");
        String err = game.unequipItem(championUid, equipmentId);
        if (err != null) return error(err);
        game.lastAccessTime = System.currentTimeMillis();
        saveToDisk(sid, game);
        return gameState(sid);
    }

    // =========== 会话持久化 ===========

    /** 从内存或磁盘获取游戏 */
    private AutoChessGame getOrLoadSession(String sid) {
        AutoChessGame game = sessions.get(sid);
        if (game != null) return game;

        // 尝试从磁盘恢复
        game = loadFromDisk(sid);
        if (game != null) {
            sessions.put(sid, game);
        }
        return game;
    }

    /** 保存到磁盘 */
    private void saveToDisk(String sid, AutoChessGame game) {
        try {
            File file = new File(SESSION_DIR, sid + ".json");
            objectMapper.writeValue(file, game);
        } catch (IOException e) {
            System.err.println("[AutoChess] 保存会话失败: " + sid + " - " + e.getMessage());
        }
    }

    /** 从磁盘加载 */
    private AutoChessGame loadFromDisk(String sid) {
        try {
            File file = new File(SESSION_DIR, sid + ".json");
            if (!file.exists()) return null;

            // 检查是否过期
            if (System.currentTimeMillis() - file.lastModified() > SESSION_EXPIRE_MS) {
                file.delete();
                return null;
            }

            AutoChessGame game = objectMapper.readValue(file, AutoChessGame.class);
            game.lastAccessTime = System.currentTimeMillis();
            return game;
        } catch (IOException e) {
            System.err.println("[AutoChess] 加载会话失败: " + sid + " - " + e.getMessage());
            return null;
        }
    }

    // =========== 战前存档（战斗断线恢复） ===========

    /** 保存战前存档 */
    private void saveCheckpoint(String sid, AutoChessGame game) {
        try {
            File file = new File(SESSION_DIR, sid + "_checkpoint.json");
            objectMapper.writeValue(file, game);
        } catch (IOException e) {
            System.err.println("[AutoChess] 保存战前存档失败: " + sid);
        }
    }

    /** 加载战前存档 */
    private AutoChessGame loadCheckpoint(String sid) {
        try {
            File file = new File(SESSION_DIR, sid + "_checkpoint.json");
            if (!file.exists()) return null;
            return objectMapper.readValue(file, AutoChessGame.class);
        } catch (IOException e) {
            return null;
        }
    }

    /** 删除战前存档 */
    private void deleteCheckpoint(String sid) {
        try {
            File file = new File(SESSION_DIR, sid + "_checkpoint.json");
            if (file.exists()) file.delete();
        } catch (Exception ignored) {}
    }

    /**
     * 如果游戏处于战斗阶段，尝试从战前存档恢复
     * 让玩家回到战斗开始前的准备阶段
     */
    private AutoChessGame tryRestoreCheckpoint(String sid, AutoChessGame game) {
        if (!"battle".equals(game.phase)) return game;

        AutoChessGame restored = loadCheckpoint(sid);
        if (restored == null) return game; // 没有存档，保持当前状态

        // 恢复为战前状态
        restored.lastAccessTime = System.currentTimeMillis();
        restored.gameSessionId = sid;
        // 清空战后产生的数据
        restored.battleLog = null;
        restored.battleEvents.clear();
        restored.lastRoundLog.clear();
        sessions.put(sid, restored);
        saveToDisk(sid, restored);
        System.out.println("[AutoChess] 已从战前存档恢复会话: " + sid);
        return restored;
    }

    /** 清理过期会话文件 */
    private void cleanupExpiredSessions() {
        try {
            File dir = new File(SESSION_DIR);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                long now = System.currentTimeMillis();
                for (File f : files) {
                    if (now - f.lastModified() > SESSION_EXPIRE_MS) {
                        f.delete();
                        System.out.println("[AutoChess] 清理过期文件: " + f.getName());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // =========== 辅助 ===========

    @SuppressWarnings("unchecked")
    private Map<String, Object> gameState(String sid) {
        AutoChessGame g = sessions.get(sid);
        if (g == null) return error("游戏不存在");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("round", g.round);
        resp.put("gold", g.gold);
        resp.put("level", g.level);
        resp.put("xp", g.xp);
        resp.put("hp", g.hp);
        resp.put("phase", g.phase);
        resp.put("battleLog", g.battleLog);
        resp.put("lastRoundLog", g.lastRoundLog);
        resp.put("maxFieldSize", g.maxFieldSize());
        resp.put("benchSize", g.BENCH_SIZE);
        resp.put("boardRows", g.BOARD_ROWS);
        resp.put("boardCols", g.BOARD_COLS);

        // 棋盘
        List<Map<String, Object>> boardList = new ArrayList<>();
        for (Map.Entry<String, AutoChessGame.Champion> e : g.board.entrySet()) {
            String[] parts = e.getKey().split(",");
            Map<String, Object> m = champToMap(e.getValue());
            m.put("row", Integer.parseInt(parts[0]));
            m.put("col", Integer.parseInt(parts[1]));
            boardList.add(m);
        }
        resp.put("board", boardList);

        // 备战席
        List<Map<String, Object>> benchList = new ArrayList<>();
        for (AutoChessGame.Champion c : g.bench) {
            benchList.add(champToMap(c));
        }
        resp.put("bench", benchList);

        // 商店
        List<Object> shopList = new ArrayList<>();
        for (AutoChessGame.Champion c : g.shop) {
            shopList.add(c != null ? champToMap(c) : null);
        }
        resp.put("shop", shopList);

        // 敌方棋盘
        List<Map<String, Object>> enemyList = new ArrayList<>();
        for (Map.Entry<String, AutoChessGame.Champion> e : g.enemyBoard.entrySet()) {
            String[] parts = e.getKey().split(",");
            Map<String, Object> m = champToMap(e.getValue());
            m.put("row", Integer.parseInt(parts[0]));
            m.put("col", Integer.parseInt(parts[1]));
            enemyList.add(m);
        }
        resp.put("enemyBoard", enemyList);

        // 羁绊
        resp.put("traitCount", g.traitCount);
        resp.put("activeSynergies", g.activeSynergies);

        // 所有羁绊定义
        List<Map<String, Object>> allSyns = new ArrayList<>();
        for (AutoChessGame.SynergyDef s : AutoChessGame.SYNERGIES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name);
            m.put("required", s.required);
            m.put("desc", s.desc);
            allSyns.add(m);
        }
        resp.put("allSynergies", allSyns);

        resp.put("battleEvents", g.battleEvents);
        resp.put("enemySource", g.enemySource);
        resp.put("sessionId", sid);

        // 装备系统
        List<Map<String, Object>> invList = new ArrayList<>();
        for (String eqId : g.equipmentInventory) {
            AutoChessGame.EquipmentItem eq = AutoChessGame.findEquipment(eqId);
            if (eq != null) invList.add(equipToMap(eq));
        }
        resp.put("equipmentInventory", invList);

        return resp;
    }

    private Map<String, Object> champToMap(AutoChessGame.Champion c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uid", c.uid);
        m.put("name", c.name);
        m.put("tier", c.tier);
        m.put("traits", c.traits);
        m.put("star", c.star);
        m.put("hp", c.hp);
        m.put("maxHp", c.maxHp);
        m.put("atk", c.atk);
        m.put("atkSpeed", c.atkSpeed);
        m.put("atkType", c.atkType);
        m.put("def", c.def);
        m.put("magicDef", c.magicDef);
        // 装备：将ID列表转换为装备详情列表
        List<Map<String, Object>> eqList = new ArrayList<>();
        for (String eqId : c.equipment) {
            AutoChessGame.EquipmentItem eq = AutoChessGame.findEquipment(eqId);
            if (eq != null) eqList.add(equipToMap(eq));
        }
        m.put("equipment", eqList);
        return m;
    }

    private Map<String, Object> equipToMap(AutoChessGame.EquipmentItem eq) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", eq.id);
        m.put("name", eq.name);
        m.put("type", eq.type);
        m.put("tier", eq.tier);
        m.put("desc", eq.desc);
        m.put("atkBonus", eq.atkBonus);
        m.put("defBonus", eq.defBonus);
        m.put("magicDefBonus", eq.magicDefBonus);
        m.put("atkSpeedBonus", eq.atkSpeedBonus);
        m.put("hpBonus", eq.hpBonus);
        m.put("dodgeRate", eq.dodgeRate);
        m.put("critRate", eq.critRate);
        m.put("lifesteal", eq.lifesteal);
        m.put("arpen", eq.arpen);
        m.put("mrpen", eq.mrpen);
        m.put("reflectDmg", eq.reflectDmg);
        m.put("goldPerRound", eq.goldPerRound);
        return m;
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", msg);
        return err;
    }
}
