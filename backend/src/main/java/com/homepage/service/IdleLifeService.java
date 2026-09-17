package com.homepage.service;

import com.homepage.config.JwtUtil;
import com.homepage.model.IdleLifeSave;
import com.homepage.model.Item;
import com.homepage.repository.IdleLifeRepository;
import com.homepage.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 挂机生活 - 核心游戏服务
 * 包含：游客账号、存档管理、属性计算、回合制战斗模拟、掉落与升级、背包与装备。
 * 目前实现【战斗模式】，种田/交易模式预留结构（MODE_FARM / MODE_TRADE）。
 */
@Service
public class IdleLifeService {

    /** 模式：战斗（已实现）；种田/交易（预留） */
    public static final String MODE_BATTLE = "BATTLE";
    public static final String MODE_FARM = "FARM";
    public static final String MODE_TRADE = "TRADE";

    private final IdleLifeRepository repo;
    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final Random random = new Random();

    /** 玩家级锁，防止同一存档并发战斗 */
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    public IdleLifeService(IdleLifeRepository repo, UserRepository userRepository,
                           UserService userService, JwtUtil jwtUtil) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    // ==================== 游客账号 ====================

    /**
     * 创建游客账号：随机用户名 + 随机密码，注册进账号系统并自动登录。
     */
    public Map<String, Object> createGuest() {
        Map<String, Object> result = new LinkedHashMap<>();
        String username;
        do {
            username = "guest_" + randomString(8);
        } while (userRepository.findByUsername(username).isPresent());
        String password = randomString(10) + "A1";

        Map<String, Object> reg = userService.register(username, password);
        if (!(boolean) reg.get("success")) {
            result.put("success", false);
            result.put("message", "游客账号创建失败，请稍后重试");
            return result;
        }

        result.put("success", true);
        result.put("message", "游客账号已创建");
        result.put("username", username);
        result.put("password", password);
        result.put("accessToken", reg.get("accessToken"));
        result.put("refreshToken", reg.get("refreshToken"));
        return result;
    }

    private String randomString(int len) {
        String chars = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ==================== 存档 ====================

    public IdleLifeSave getOrCreateSave(Long userId, String username) {
        return repo.findSaveByUsername(username).orElseGet(() -> {
            IdleLifeSave s = new IdleLifeSave();
            s.setUserId(userId);
            s.setUsername(username);
            s.setLevel(1);
            s.setExp(0);
            s.setGold(0);
            s.setCurrentDungeon(0);
            s.setBossLastBattle(0);
            return repo.insertSave(s);
        });
    }

    // ==================== 属性计算 ====================

    public static final class Stats {
        public int level;
        public long baseAttack, basePhysDef, baseMagicDef, baseMaxHp;
        public double baseDodge;
        public long attack, physDef, magicDef, maxHp;
        public double dodge;
        public Map<String, String> equipment = new LinkedHashMap<>(); // slot -> itemKey
    }

    /** 计算玩家属性 = 等级基础 + 已装备加成 */
    public Stats computeStats(IdleLifeSave save) {
        Stats st = new Stats();
        st.level = save.getLevel();
        st.baseAttack = 10 + (long) (save.getLevel() - 1) * 4;
        st.basePhysDef = 5 + (long) (save.getLevel() - 1) * 3;
        st.baseMagicDef = 5 + (long) (save.getLevel() - 1) * 3;
        st.baseDodge = 5 + (save.getLevel() - 1) * 0.5;
        st.baseMaxHp = 100 + (long) (save.getLevel() - 1) * 25;

        st.attack = st.baseAttack;
        st.physDef = st.basePhysDef;
        st.magicDef = st.baseMagicDef;
        st.dodge = st.baseDodge;
        st.maxHp = st.baseMaxHp;

        String[] slots = {"helmet", "chest", "legs", "boots"};
        for (String slot : slots) {
            String key = slot.equals("helmet") ? save.getHelmet()
                    : slot.equals("chest") ? save.getChest()
                    : slot.equals("legs") ? save.getLegs() : save.getBoots();
            if (key == null || key.isEmpty()) continue;
            IdleLifeConfig.EquipDef eq = IdleLifeConfig.EQUIPS.get(key);
            if (eq == null) continue;
            st.attack += eq.attack;
            st.physDef += eq.physDef;
            st.magicDef += eq.magicDef;
            st.dodge += eq.dodge;
            st.maxHp += eq.hp;
            st.equipment.put(slot, key);
        }
        return st;
    }

    /** 升级所需经验 */
    public static long expToNext(int level) {
        return (long) level * IdleLifeConfig.EXP_PER_LEVEL;
    }

    // ==================== 背包 ====================

    public List<Item> getInventory(String username) {
        return repo.findItems(username);
    }

    /** 穿装备（直接切换存档槽位，装备始终保留在背包中） */
    public Map<String, Object> equip(String username, String itemKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        Item item = repo.findItem(username, itemKey).orElse(null);
        if (item == null || !"EQUIPMENT".equals(item.getItemType())) {
            result.put("success", false);
            result.put("message", "背包中不存在该装备");
            return result;
        }
        IdleLifeSave save = repo.findSaveByUsername(username).orElse(null);
        if (save == null) {
            result.put("success", false);
            result.put("message", "存档不存在");
            return result;
        }
        setSlot(save, item.getSlot(), itemKey);
        repo.updateSave(save);
        result.put("success", true);
        result.put("message", "已装备 " + item.getName());
        result.put("save", saveView(save));
        return result;
    }

    /** 脱下装备 */
    public Map<String, Object> unequip(String username, String slot) {
        Map<String, Object> result = new LinkedHashMap<>();
        IdleLifeSave save = repo.findSaveByUsername(username).orElse(null);
        if (save == null) {
            result.put("success", false);
            result.put("message", "存档不存在");
            return result;
        }
        setSlot(save, slot, null);
        repo.updateSave(save);
        result.put("success", true);
        result.put("message", "已卸下装备");
        result.put("save", saveView(save));
        return result;
    }

    private void setSlot(IdleLifeSave save, String slot, String key) {
        switch (slot) {
            case "helmet": save.setHelmet(key); break;
            case "chest": save.setChest(key); break;
            case "legs": save.setLegs(key); break;
            case "boots": save.setBoots(key); break;
            default: break;
        }
    }

    /** 设置战斗药水 */
    public Map<String, Object> setPotion(String username, String potionKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        IdleLifeSave save = repo.findSaveByUsername(username).orElse(null);
        if (save == null) {
            result.put("success", false);
            result.put("message", "存档不存在");
            return result;
        }
        if (potionKey == null || potionKey.isEmpty()) {
            save.setPotionItem(null);
        } else {
            IdleLifeConfig.PotionDef potion = IdleLifeConfig.POTIONS.get(potionKey);
            if (potion == null) {
                result.put("success", false);
                result.put("message", "药水不存在");
                return result;
            }
            Item item = repo.findItem(username, potionKey).orElse(null);
            if (item == null || item.getQuantity() <= 0) {
                result.put("success", false);
                result.put("message", "背包中没有该药水");
                return result;
            }
            save.setPotionItem(potionKey);
        }
        repo.updateSave(save);
        result.put("success", true);
        result.put("message", potionKey == null ? "已取消携带药水" : "已设置战斗中自动使用药水");
        result.put("save", saveView(save));
        return result;
    }

    // ==================== 挂机开始/停止 ====================

    public Map<String, Object> startBattle(String username, int dungeonId) {
        Map<String, Object> result = new LinkedHashMap<>();
        IdleLifeSave save = repo.findSaveByUsername(username).orElse(null);
        if (save == null) {
            result.put("success", false);
            result.put("message", "存档不存在");
            return result;
        }
        IdleLifeConfig.Dungeon dungeon = IdleLifeConfig.getDungeon(dungeonId);
        if (dungeon == null) {
            result.put("success", false);
            result.put("message", "副本不存在");
            return result;
        }
        if (save.getLevel() < dungeon.reqLevel) {
            result.put("success", false);
            result.put("message", "等级不足，需要 " + dungeon.reqLevel + " 级才能进入该副本");
            return result;
        }
        save.setCurrentDungeon(dungeonId);
        save.setBossLastBattle(System.currentTimeMillis());
        repo.updateSave(save);
        result.put("success", true);
        result.put("message", "已进入" + dungeon.name + "挂机");
        result.put("save", saveView(save));
        return result;
    }

    public Map<String, Object> stopBattle(String username) {
        Map<String, Object> result = new LinkedHashMap<>();
        IdleLifeSave save = repo.findSaveByUsername(username).orElse(null);
        if (save == null) {
            result.put("success", false);
            result.put("message", "存档不存在");
            return result;
        }
        save.setCurrentDungeon(0);
        repo.updateSave(save);
        result.put("success", true);
        result.put("message", "已停止挂机");
        result.put("save", saveView(save));
        return result;
    }

    // ==================== 战斗 ====================

    /**
     * 执行一场挂机战斗。
     * 距上次 boss 战超过 BOSS_INTERVAL_MS 则进入 boss 房，否则随机小怪。
     */
    public Map<String, Object> battle(String username, int dungeonId) {
        Object lock = locks.computeIfAbsent(username, k -> new Object());
        synchronized (lock) {
            Map<String, Object> result = new LinkedHashMap<>();
            IdleLifeSave save = repo.findSaveByUsername(username).orElse(null);
            if (save == null) {
                result.put("success", false);
                result.put("message", "存档不存在");
                return result;
            }
            if (save.getCurrentDungeon() != dungeonId) {
                result.put("success", false);
                result.put("message", save.getCurrentDungeon() == 0 ? "未在挂机中，请先选择副本开始挂机"
                        : "副本不一致，请重新开始挂机");
                return result;
            }
            IdleLifeConfig.Dungeon dungeon = IdleLifeConfig.getDungeon(dungeonId);
            if (dungeon == null) {
                result.put("success", false);
                result.put("message", "副本不存在");
                return result;
            }

            long now = System.currentTimeMillis();
            boolean isBoss = now - save.getBossLastBattle() >= IdleLifeConfig.BOSS_INTERVAL_MS;
            IdleLifeConfig.Monster mob = isBoss ? dungeon.boss
                    : dungeon.monsters.get(random.nextInt(dungeon.monsters.size()));

            Stats stats = computeStats(save);
            BattleResult br = simulateBattle(stats, mob, save, username);

            List<Map<String, Object>> drops = new ArrayList<>();
            long expGained = 0;
            long goldGained = 0;
            int oldLevel = save.getLevel();

            if (br.win) {
                expGained = mob.exp;
                save.setExp(save.getExp() + expGained);
                // 升级（可能连升多级）
                while (save.getExp() >= expToNext(save.getLevel())) {
                    save.setExp(save.getExp() - expToNext(save.getLevel()));
                    save.setLevel(save.getLevel() + 1);
                }
                if (isBoss) {
                    save.setBossesKilled(save.getBossesKilled() + 1);
                    save.setBossLastBattle(now);
                } else {
                    save.setBattlesWon(save.getBattlesWon() + 1);
                }
                // 掉落
                drops = rollLoot(username, mob, isBoss);
                for (Map<String, Object> drop : drops) {
                    Object goldVal = drop.get("gold");
                    goldGained += (goldVal instanceof Number) ? ((Number) goldVal).longValue() : 0;
                }
                save.setGold(save.getGold() + goldGained);
            } else {
                if (isBoss) {
                    // boss 战死亡无惩罚，10分钟后再次挑战
                    save.setBossLastBattle(now);
                } else {
                    // 小怪战死亡，退出挂机
                    save.setCurrentDungeon(0);
                }
            }
            repo.updateSave(save);

            result.put("success", true);
            result.put("isBoss", isBoss);
            result.put("monster", monsterView(mob, isBoss));
            result.put("victory", br.win);
            result.put("rounds", br.rounds);
            result.put("potionUsed", br.potionUsed);
            result.put("expGained", expGained);
            result.put("goldGained", goldGained);
            result.put("levelUp", save.getLevel() > oldLevel ? save.getLevel() - oldLevel : 0);
            result.put("drops", drops);
            result.put("defeated", !br.win && !isBoss); // 小怪战死亡=退出挂机
            result.put("save", saveView(save));
            result.put("message", br.win ? "战斗胜利" : (isBoss ? "挑战 boss 失败，已回到营地休整" : "战斗失败，已退出挂机"));
            return result;
        }
    }

    /** 掉落规则：小怪 80%金币 / 19%素材 / 1%专属装备；boss 90%金币+素材 / 10%装备(四选一) */
    private List<Map<String, Object>> rollLoot(String username, IdleLifeConfig.Monster mob, boolean isBoss) {
        List<Map<String, Object>> drops = new ArrayList<>();
        int roll = random.nextInt(100);

        if (isBoss) {
            long gold = mob.goldMin + random.nextInt(mob.goldMax - mob.goldMin + 1);
            drops.add(Map.of("type", "GOLD", "name", "金币", "gold", gold, "quantity", 1));
            if (roll < 10) {
                // boss 专属装备，4件槽位随机1件
                String[] slots = {"helmet", "chest", "legs", "boots"};
                String slot = slots[random.nextInt(slots.length)];
                String prefix = mob.equipKey.replace("_helmet", "");
                String eqKey = prefix + "_" + slot;
                giveEquip(username, eqKey, drops);
            } else {
                // 素材 2-3 个
                int n = 2 + random.nextInt(2);
                giveMaterial(username, mob, n, drops);
            }
        } else {
            if (roll < 80) {
                long gold = mob.goldMin + random.nextInt(mob.goldMax - mob.goldMin + 1);
                drops.add(Map.of("type", "GOLD", "name", "金币", "gold", gold, "quantity", 1));
            } else if (roll < 99) {
                giveMaterial(username, mob, 1, drops);
            } else {
                giveEquip(username, mob.equipKey, drops);
            }
        }
        return drops;
    }

    private void giveMaterial(String username, IdleLifeConfig.Monster mob, int n, List<Map<String, Object>> drops) {
        Item item = new Item();
        item.setUsername(username);
        item.setItemKey(mob.materialKey);
        item.setItemType("MATERIAL");
        item.setSlot("none");
        item.setName(mob.material);
        item.setQuantity(n);
        item.setLevel(mob.level);
        item.setDesc("制作材料，可在家园或交易中用于制作装备/药水");
        repo.insertItem(item);
        drops.add(Map.of("type", "MATERIAL", "name", mob.material, "quantity", n));
    }

    private void giveEquip(String username, String eqKey, List<Map<String, Object>> drops) {
        IdleLifeConfig.EquipDef def = IdleLifeConfig.EQUIPS.get(eqKey);
        if (def == null) return;
        Item item = new Item();
        item.setUsername(username);
        item.setItemKey(def.key);
        item.setItemType("EQUIPMENT");
        item.setSlot(def.slot);
        item.setName(def.name);
        item.setQuantity(1);
        item.setLevel(def.level);
        item.setAttack(def.attack);
        item.setPhysDef(def.physDef);
        item.setMagicDef(def.magicDef);
        item.setDodge(def.dodge);
        item.setHp(def.hp);
        item.setDesc("装备等级 " + def.level);
        repo.insertItem(item);
        drops.add(Map.of("type", "EQUIPMENT", "name", def.name, "quantity", 1, "itemKey", def.key, "slot", def.slot));
    }

    // ==================== 回合制战斗模拟 ====================

    public static final class BattleResult {
        public boolean win;
        public int potionUsed;
        public List<Map<String, Object>> rounds = new ArrayList<>();
    }

    private BattleResult simulateBattle(Stats ps, IdleLifeConfig.Monster mob, IdleLifeSave save, String username) {
        BattleResult res = new BattleResult();
        long pMaxHp = ps.maxHp;
        long pHp = pMaxHp;
        long mHp = mob.hp;
        int maxRounds = 200;
        boolean potionSet = save.getPotionItem() != null;
        IdleLifeConfig.PotionDef potion = potionSet ? IdleLifeConfig.POTIONS.get(save.getPotionItem()) : null;
        boolean potionAvailable = potion != null;

        for (int r = 1; r <= maxRounds; r++) {
            Map<String, Object> round = new LinkedHashMap<>();
            List<String> log = new ArrayList<>();
            // 玩家回合
            if (roll(100 - mob.dodge)) { // 命中
                long dmg = Math.max(1, ps.attack - mob.physDef);
                mHp -= dmg;
                log.add("你攻击" + mob.name + "，造成 " + dmg + " 点伤害");
            } else {
                log.add("你攻击" + mob.name + "，被闪避了");
            }
            if (mHp <= 0) {
                mHp = 0;
                round.put("round", r);
                round.put("playerHp", pHp);
                round.put("monsterHp", mHp);
                round.put("log", log);
                res.rounds.add(round);
                res.win = true;
                return res;
            }
            // 怪物回合
            if (roll(100 - ps.dodge)) { // 命中
                long dmg = Math.max(1, mob.attack - ps.physDef);
                pHp -= dmg;
                log.add(mob.name + "攻击你，造成 " + dmg + " 点伤害");
            } else {
                log.add(mob.name + "攻击你，被你闪避了");
            }
            // 自动使用药水：血量低于50%且背包有药水
            if (potionAvailable && pHp < pMaxHp / 2 && pHp > 0 && potion != null) {
                Item pot = repo.findItem(username, potion.key).orElse(null);
                if (pot != null && pot.getQuantity() > 0) {
                    pHp = Math.min(pMaxHp, pHp + potion.heal);
                    repo.consumeItem(username, potion.key, 1);
                    res.potionUsed++;
                    log.add("你使用了" + potion.name + "，恢复 " + potion.heal + " 点生命");
                }
            }
            round.put("round", r);
            round.put("playerHp", Math.max(0, pHp));
            round.put("monsterHp", Math.max(0, mHp));
            round.put("log", log);
            res.rounds.add(round);
            if (pHp <= 0) {
                res.win = false;
                return res;
            }
        }
        // 超回合上限判负（打不动的情况）
        res.win = false;
        return res;
    }

    private boolean roll(double percent) {
        return random.nextInt(100) < percent;
    }

    // ==================== 视图 ====================

    /** 存档对外视图（含计算属性与装备详情） */
    public Map<String, Object> saveView(IdleLifeSave save) {
        Stats st = computeStats(save);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("username", save.getUsername());
        view.put("level", save.getLevel());
        view.put("exp", save.getExp());
        view.put("expToNext", expToNext(save.getLevel()));
        view.put("gold", save.getGold());
        view.put("currentDungeon", save.getCurrentDungeon());
        view.put("potionItem", save.getPotionItem());
        view.put("battlesWon", save.getBattlesWon());
        view.put("bossesKilled", save.getBossesKilled());
        // 属性
        view.put("stats", Map.of(
            "attack", st.attack, "baseAttack", st.baseAttack,
            "physDef", st.physDef, "basePhysDef", st.basePhysDef,
            "magicDef", st.magicDef, "baseMagicDef", st.baseMagicDef,
            "dodge", Math.round(st.dodge * 10) / 10.0, "baseDodge", Math.round(st.baseDodge * 10) / 10.0,
            "maxHp", st.maxHp, "baseMaxHp", st.baseMaxHp));
        // 装备槽位详情
        Map<String, Object> equipment = new LinkedHashMap<>();
        String[] slots = {"helmet", "chest", "legs", "boots"};
        for (String slot : slots) {
            String key = st.equipment.get(slot);
            if (key != null) {
                IdleLifeConfig.EquipDef def = IdleLifeConfig.EQUIPS.get(key);
                equipment.put(slot, def == null ? null : equipView(def));
            } else {
                equipment.put(slot, null);
            }
        }
        view.put("equipment", equipment);
        // boss 倒计时
        long now = System.currentTimeMillis();
        long remain = Math.max(0, IdleLifeConfig.BOSS_INTERVAL_MS - (now - save.getBossLastBattle()));
        view.put("bossCountdown", remain);
        return view;
    }

    public Map<String, Object> equipView(IdleLifeConfig.EquipDef def) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", def.key);
        m.put("name", def.name);
        m.put("slot", def.slot);
        m.put("level", def.level);
        m.put("attack", def.attack);
        m.put("physDef", def.physDef);
        m.put("magicDef", def.magicDef);
        m.put("dodge", def.dodge);
        m.put("hp", def.hp);
        return m;
    }

    private Map<String, Object> monsterView(IdleLifeConfig.Monster mob, boolean isBoss) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", mob.key);
        m.put("name", mob.name);
        m.put("level", mob.level);
        m.put("attack", mob.attack);
        m.put("physDef", mob.physDef);
        m.put("magicDef", mob.magicDef);
        m.put("dodge", mob.dodge);
        m.put("hp", mob.hp);
        m.put("exp", mob.exp);
        m.put("goldMin", mob.goldMin);
        m.put("goldMax", mob.goldMax);
        m.put("material", mob.material);
        m.put("boss", isBoss);
        return m;
    }

    /** 前端展示用的副本/装备/药水配置 */
    public Map<String, Object> configView() {
        Map<String, Object> view = new LinkedHashMap<>();
        List<Map<String, Object>> dungeons = new ArrayList<>();
        for (IdleLifeConfig.Dungeon d : IdleLifeConfig.DUNGEONS) {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.id);
            dm.put("name", d.name);
            dm.put("desc", d.desc);
            dm.put("reqLevel", d.reqLevel);
            List<Map<String, Object>> mobs = new ArrayList<>();
            for (IdleLifeConfig.Monster mob : d.monsters) {
                mobs.add(monsterView(mob, false));
            }
            Map<String, Object> boss = monsterView(d.boss, true);
            boss.put("equipName", IdleLifeConfig.EQUIPS.get(d.boss.equipKey).name);
            dm.put("monsters", mobs);
            dm.put("boss", boss);
            dungeons.add(dm);
        }
        view.put("dungeons", dungeons);

        List<Map<String, Object>> potions = new ArrayList<>();
        for (IdleLifeConfig.PotionDef p : IdleLifeConfig.POTIONS.values()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("key", p.key);
            pm.put("name", p.name);
            pm.put("reqLevel", p.reqLevel);
            pm.put("heal", p.heal);
            potions.add(pm);
        }
        view.put("potions", potions);
        view.put("bossIntervalSec", IdleLifeConfig.BOSS_INTERVAL_MS / 1000);
        view.put("modes", List.of(MODE_BATTLE, MODE_FARM, MODE_TRADE));
        return view;
    }

    /** 背包视图（含药水剩余量） */
    public Map<String, Object> inventoryView(String username) {
        Map<String, Object> view = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Item it : getInventory(username)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemKey", it.getItemKey());
            m.put("itemType", it.getItemType());
            m.put("slot", it.getSlot());
            m.put("name", it.getName());
            m.put("quantity", it.getQuantity());
            m.put("level", it.getLevel());
            m.put("attack", it.getAttack());
            m.put("physDef", it.getPhysDef());
            m.put("magicDef", it.getMagicDef());
            m.put("dodge", it.getDodge());
            m.put("hp", it.getHp());
            m.put("desc", it.getDesc());
            items.add(m);
        }
        view.put("items", items);
        return view;
    }
}
