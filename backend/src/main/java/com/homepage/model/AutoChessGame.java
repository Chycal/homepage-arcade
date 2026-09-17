package com.homepage.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 自走棋游戏状态与逻辑
 */
public class AutoChessGame {

    // ============ 常量 ============
    public static final int BOARD_ROWS = 4;
    public static final int BOARD_COLS = 7;
    public static final int BENCH_SIZE = 8;
    public static final int SHOP_SIZE = 5;

    // ============ 英雄定义(含技能) ============
    public static final ChampionDef[] ALL_CHAMPIONS = {
        // Tier 1 (1费) - 白棋
        new ChampionDef("圣骑士",  1, new String[]{"贵族","骑士团"}, 380, 65, "physical", 30, 15,
            "圣光裁决", "single", 200, 0, 55, 8, 1.5),
        new ChampionDef("神射手",  1, new String[]{"贵族","游侠"},   350, 70, "physical", 20, 10,
            "穿云箭", "single", 250, 0, 50, 10, 1.5),
        new ChampionDef("帝国将军", 1, new String[]{"帝国","骑士团"}, 380, 70, "physical", 28, 15,
            "军令", "buff_team", 20, 0, 70, 10, 2.0),
        new ChampionDef("狂战士",  1, new String[]{"野兽","格斗家"}, 420, 60, "physical", 22, 8,
            "狂暴", "buff_self", 50, 0, 45, 8, 1.5),
        // Tier 2 (2费) - 绿棋
        new ChampionDef("光明游侠", 2, new String[]{"贵族","游侠"},   380, 80, "physical", 25, 20,
            "光之箭雨", "aoe", 150, 0, 60, 12, 2.5),
        new ChampionDef("九尾妖狐", 2, new String[]{"野兽","法师"},   350, 85, "magic",   12, 35,
            "魅惑", "stun_single", 5, 0, 55, 10, 2.5),
        new ChampionDef("暗影刺客", 2, new String[]{"帝国","刺客"},   350, 90, "physical", 15, 12,
            "暗杀", "single", 300, 0, 50, 10, 2.0),
        new ChampionDef("冰霜射手", 2, new String[]{"极冰","游侠"},   350, 80, "physical", 20, 15,
            "冰霜箭", "single", 200, 3, 55, 10, 2.0),
        // Tier 3 (3费) - 蓝棋
        new ChampionDef("剑圣",    3, new String[]{"帝国","剑客"},    450, 100, "physical", 35, 22,
            "旋风斩", "aoe", 180, 0, 65, 12, 3.0),
        new ChampionDef("冰霜骑士", 3, new String[]{"极冰","骑士团"}, 550, 70, "physical", 45, 28,
            "寒冰护盾", "shield", 30, 0, 70, 10, 3.0),
        new ChampionDef("虚空巨兽", 3, new String[]{"虚空","格斗家"}, 580, 75, "physical", 40, 20,
            "虚空践踏", "stun_aoe", 3, 0, 75, 10, 3.0),
        new ChampionDef("火焰巫师", 3, new String[]{"恶魔","法师"},   400, 100, "magic",   15, 42,
            "火球术", "single", 280, 0, 55, 12, 3.5),
        // Tier 4 (4费) - 紫棋
        new ChampionDef("龙骑士",    4, new String[]{"贵族","骑士团"}, 720, 115, "physical", 50, 35,
            "龙息", "aoe_all", 150, 0, 80, 15, 3.5),
        new ChampionDef("牛头人酋长", 4, new String[]{"野兽","格斗家"}, 820, 95,  "physical", 55, 25,
            "战争践踏", "stun_aoe", 4, 0, 85, 12, 3.5),
        new ChampionDef("冰霜女巫",   4, new String[]{"极冰","法师"},   580, 130, "magic",    20, 58,
            "暴风雪", "aoe_all", 160, 0, 90, 15, 4.0),
        new ChampionDef("虚空行者",   4, new String[]{"虚空","刺客"},   600, 145, "physical",  28, 30,
            "虚空穿梭", "teleport", 250, 0, 60, 12, 3.0),
        // Tier 5 (5费) - 金棋
        new ChampionDef("神圣天使",   5, new String[]{"贵族","剑客"},    900, 145, "physical", 60, 50,
            "天使降临", "revive", 50, 0, 100, 18, 4.5),
        new ChampionDef("帝国皇帝",   5, new String[]{"帝国","骑士团"},  950, 135, "physical", 55, 40,
            "帝王之怒", "aoe_all", 250, 0, 100, 18, 4.0),
        new ChampionDef("冰霜巨龙",   5, new String[]{"极冰","野兽"},    880, 130, "magic",    45, 60,
            "冰霜吐息", "aoe_all", 200, 2, 100, 18, 4.5),
        new ChampionDef("深渊恶魔",   5, new String[]{"恶魔","法师"},    780, 160, "magic",     22, 65,
            "恶魔契约", "single", 400, 0, 85, 15, 4.0),
    };

    // ============ 羁绊定义 (需要 >= 2 激活) ============
    public static final List<SynergyDef> SYNERGIES = List.of(
        new SynergyDef("贵族",   2, "贵族英雄 +120 生命值"),
        new SynergyDef("骑士团", 2, "骑士团英雄减免 18% 伤害, +100 HP"),
        new SynergyDef("游侠",   2, "游侠英雄 +30% 攻速"),
        new SynergyDef("帝国",   2, "帝国英雄 +35% 攻击力"),
        new SynergyDef("野兽",   2, "全体 +20% 攻速"),
        new SynergyDef("法师",   2, "法师英雄 +40% 攻击力"),
        new SynergyDef("极冰",   2, "极冰攻击有 20% 概率眩晕 1 秒"),
        new SynergyDef("格斗家", 2, "格斗家英雄 +180 生命值"),
        new SynergyDef("刺客",   1, "刺客暴击伤害 +50%"),
        new SynergyDef("剑客",   1, "剑客有 30% 概率连击"),
        new SynergyDef("虚空",   1, "虚空无视 20% 护甲"),
        new SynergyDef("恶魔",   1, "恶魔额外 +15% 攻击力")
    );

    // ============ 装备系统 ============
    public static class EquipmentItem {
        public String id;
        public String name;
        public String type;         // "weapon" | "armor" | "accessory"
        public int tier;            // 1=普通 2=稀有 3=史诗
        public int atkBonus;
        public int defBonus;
        public int magicDefBonus;
        public double atkSpeedBonus;
        public int hpBonus;
        public double dodgeRate;    // 闪避率
        public double critRate;     // 暴击率加成
        public double lifesteal;    // 物理吸血
        public int arpen;           // 护甲穿透
        public int mrpen;           // 魔抗穿透
        public double reflectDmg;   // 反弹物理伤害比例
        public int goldPerRound;    // 每回合金币
        public String desc;
        public EquipmentItem() {}
        public EquipmentItem(String id, String name, String type, int tier,
                             int atkBonus, int defBonus, int magicDefBonus, double atkSpeedBonus,
                             int hpBonus, double dodgeRate, double critRate, double lifesteal,
                             int arpen, int mrpen, double reflectDmg, int goldPerRound, String desc) {
            this.id = id; this.name = name; this.type = type; this.tier = tier;
            this.atkBonus = atkBonus; this.defBonus = defBonus; this.magicDefBonus = magicDefBonus;
            this.atkSpeedBonus = atkSpeedBonus; this.hpBonus = hpBonus;
            this.dodgeRate = dodgeRate; this.critRate = critRate; this.lifesteal = lifesteal;
            this.arpen = arpen; this.mrpen = mrpen;
            this.reflectDmg = reflectDmg; this.goldPerRound = goldPerRound; this.desc = desc;
        }
    }

    public static final EquipmentItem[] ALL_EQUIPMENT = {
        // ===== 武器 (weapon) =====
        new EquipmentItem("longsword",   "长剑",       "weapon", 1, 15, 0, 0, 0,   0, 0,    0,    0,   0, 0, 0, 0,     "攻击力+15"),
        new EquipmentItem("stormsowrd",  "暴风大剑",    "weapon", 2, 30, 0, 0, 0,   0, 0,    0,    0,   0, 0, 0, 0,     "攻击力+30"),
        new EquipmentItem("arpenblade",  "破甲之刃",    "weapon", 2, 15, 0, 0, 0,   0, 0,    0,    0,  10, 0, 0, 0,     "攻击力+15，无视10点护甲"),
        new EquipmentItem("arcanestaff", "秘法权杖",    "weapon", 2, 20, 0, 0, 0,   0, 0,    0,    0,   0, 8, 0, 0,     "攻击力+20，无视8点魔抗"),
        new EquipmentItem("ultstaff",    "无极之杖",    "weapon", 3, 35, 0, 0, 0,   0, 0,    0,    0,   0, 0, 0, 0,     "攻击力+35"),
        new EquipmentItem("infinity",    "无尽之刃",    "weapon", 3, 25, 0, 0, 0,   0, 0, 0.15,    0,   0, 0, 0, 0,     "攻击力+25，暴击率+15%"),
        // ===== 护甲 (armor) =====
        new EquipmentItem("chainmail",   "锁子甲",      "armor",  1, 0, 20, 0, 0,   0, 0,    0,    0,   0, 0, 0, 0,     "物理防御+20"),
        new EquipmentItem("magerobe",    "法师长袍",    "armor",  1, 0,  0, 20, 0,  0, 0,    0,    0,   0, 0, 0, 0,     "法术防御+20"),
        new EquipmentItem("thornmail",   "荆棘铠甲",    "armor",  2, 0, 25, 0, 0,   0, 0,    0,    0,   0, 0, 0.15, 0, "物理防御+25，反弹15%物理伤害"),
        new EquipmentItem("mrcloak",     "抗魔斗篷",    "armor",  2, 0,  0, 30, 0,  80, 0,   0,    0,   0, 0, 0, 0,    "法术防御+30，生命+80"),
        new EquipmentItem("guardplate",  "守护板甲",    "armor",  3, 0, 20, 0, 0, 120, 0,    0,    0,   0, 0, 0, 0,    "物理防御+20，生命+120"),
        new EquipmentItem("aegis",       "永生之盾",    "armor",  3, 0, 15, 15, 0, 100, 0,   0,    0,   0, 0, 0, 0,    "双防+15，生命+100"),
        // ===== 辅助装备 (accessory) =====
        new EquipmentItem("boots",       "速度之靴",    "accessory", 1, 0, 0, 0, 0,   0, 0.12, 0,    0,   0, 0, 0, 0,  "闪避率+12%"),
        new EquipmentItem("zerkerboots", "狂战士胫甲",  "accessory", 2, 0, 0, 0, 0.3, 0, 0,    0,    0,   0, 0, 0, 0,  "攻速+30%"),
        new EquipmentItem("sagestone",   "贤者之石",    "accessory", 2, 0, 0, 0, 0,   0, 0,    0,    0,   0, 0, 0, 2,  "每回合额外获得2金币"),
        new EquipmentItem("vampmask",    "吸血面具",    "accessory", 2, 0, 0, 0, 0,   0, 0,    0, 0.18,   0, 0, 0, 0,  "物理吸血+18%"),
        new EquipmentItem("energyorb",   "能量宝珠",    "accessory", 3, 0, 0, 0, 0, 180, 0,    0,    0,   0, 0, 0, 0,  "生命+180"),
        new EquipmentItem("luckycoin",   "命运硬币",    "accessory", 3, 0, 0, 0, 0,   0, 0.08, 0.08, 0,   0, 0, 0, 0,  "闪避率+8%，暴击率+8%"),
    };

    // 升级所需 XP 表 (index = 当前等级 1-7)
    private static final int[] XP_TABLE = {0, 2, 2, 4, 6, 8, 10, 14};

    // 可上场人数 表
    private static final int[] FIELD_SIZE = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    // 商店刷新概率 [level][tier]   level 1-8, tier 0-4
    private static final int[][] SHOP_ODDS = {
        {}, // level 0 unused
        {100, 0, 0, 0, 0},       // Lv1  - 仅白棋
        {100, 0, 0, 0, 0},       // Lv2  - 仅白棋
        {70, 30, 0, 0, 0},       // Lv3  - 白/绿
        {50, 40, 10, 0, 0},      // Lv4  - 白/绿/蓝
        {35, 40, 22, 3, 0},      // Lv5  - 出现紫棋
        {22, 30, 30, 15, 3},     // Lv6  - 出现金棋
        {15, 25, 30, 22, 8},     // Lv7
        {10, 20, 25, 28, 17},    // Lv8
    };

    // ============ 数据结构 ============
    public static class ChampionDef {
        public String name;
        public int tier;            // 1-5
        public String[] traits;
        public int baseHp;
        public int baseAtk;
        public String atkType;      // "physical" | "magic"
        public int baseDef;         // 物理防御
        public int baseMagicDef;    // 法术防御
        // 技能系统
        public String skillName;    // 技能名称
        public String skillType;    // single / aoe / aoe_all / heal / shield / buff_self / buff_team / stun_single / stun_aoe / revive / teleport
        public int skillValue;      // 技能倍率(百分比整数,如200=200%ATK)或治疗百分比
        public int skillExtra;      // 额外参数(眩晕tick数/护盾百分比/治疗百分比等)
        public int maxMana;         // 施放技能所需法力
        public int manaPerAttack;   // 每次攻击恢复法力
        public double manaPerSecond; // 每秒自然恢复法力
        public ChampionDef(String name, int tier, String[] traits, int hp, int atk, String atkType,
                           int def, int mdef,
                           String skillName, String skillType, int skillValue, int skillExtra,
                           int maxMana, int manaPerAttack, double manaPerSecond) {
            this.name = name; this.tier = tier; this.traits = traits;
            this.baseHp = hp; this.baseAtk = atk;
            this.atkType = atkType; this.baseDef = def; this.baseMagicDef = mdef;
            this.skillName = skillName; this.skillType = skillType;
            this.skillValue = skillValue; this.skillExtra = skillExtra;
            this.maxMana = maxMana; this.manaPerAttack = manaPerAttack; this.manaPerSecond = manaPerSecond;
        }
    }

    public static class SynergyDef {
        public String name;
        public int required;        // 所需数量
        public String desc;
        public SynergyDef(String name, int required, String desc) {
            this.name = name; this.required = required; this.desc = desc;
        }
    }

    /** 英雄实例 */
    public static class Champion {
        public String uid;          // 唯一 ID
        public String name;         // 英雄名称
        public int tier;            // 费用
        public String[] traits;
        public int star;            // 星级 1/2/3
        public int hp;
        public int maxHp;
        public int atk;
        public double atkSpeed;     // 每秒攻击次数
        public double atkCooldown;  // 攻击冷却(秒)
        public String atkType;      // "physical" | "magic"
        public int def;             // 物理防御
        public int magicDef;        // 法术防御
        public transient int boardRow = -1;
        public transient int boardCol = -1;
        public transient boolean isPlayerSide;
        public List<String> equipment = new ArrayList<>(); // 装备ID列表 (最多3件)
        // 技能系统 (transient,仅在战斗中有效)
        public transient int currentMana = 0;
        public transient int maxMana = 0;
        public transient int manaPerAttack = 0;
        public transient double manaPerSecond = 0;
        public transient String skillName = "";
        public transient String skillType = "";
        public transient int skillValue = 0;
        public transient int skillExtra = 0;

        public Champion copy() {
            Champion c = new Champion();
            c.uid = uid; c.name = name; c.tier = tier;
            c.traits = traits; c.star = star;
            c.hp = hp; c.maxHp = maxHp; c.atk = atk;
            c.atkSpeed = atkSpeed; c.atkCooldown = atkCooldown;
            c.atkType = atkType; c.def = def; c.magicDef = magicDef;
            c.equipment = new ArrayList<>(this.equipment);
            c.currentMana = currentMana; c.maxMana = maxMana;
            c.manaPerAttack = manaPerAttack; c.manaPerSecond = manaPerSecond;
            c.skillName = skillName; c.skillType = skillType;
            c.skillValue = skillValue; c.skillExtra = skillExtra;
            return c;
        }
    }

    // ============ 游戏状态 ============
    public int gold;
    public int level;       // 1-8
    public int xp;          // 当前经验
    public int hp;          // 玩家血量
    public int round;
    public String phase;    // "planning" | "battle" | "gameover"
    public String battleLog;
    public int maxFieldSize() { return FIELD_SIZE[level]; }

    // 棋盘: key = "row,col"
    public Map<String, Champion> board = new LinkedHashMap<>();
    // 备战席
    public List<Champion> bench = new ArrayList<>();
    // 商店
    public List<Champion> shop = new ArrayList<>();
    // 敌方棋盘
    public Map<String, Champion> enemyBoard = new LinkedHashMap<>();
    // 活跃羁绊
    public List<Map<String, Object>> activeSynergies = new ArrayList<>();
    // 羁绊计数
    public Map<String, Integer> traitCount = new LinkedHashMap<>();
    // 上一局战斗日志
    public List<String> lastRoundLog = new ArrayList<>();
    // 战斗事件（用于前端动画播放）
    public List<Map<String, Object>> battleEvents = new ArrayList<>();

    // ============ 内部随机数 ============
    @JsonIgnore
    private final Random rng = new Random();

    // 最后访问时间戳（用于过期清理）
    public long lastAccessTime = System.currentTimeMillis();

    // 敌人来源: "system" 系统生成 / "ghost" 其他玩家阵容
    public String enemySource = "system";

    // 当前游戏会话ID（用于快照过滤自身）
    @JsonIgnore
    public String gameSessionId = "";

    // ============ 装备系统字段 ============
    public List<String> equipmentInventory = new ArrayList<>();       // 未装备的道具ID列表

    // ============ 快照常量 ============
    private static final String SNAPSHOT_DIR = "snapshots";
    private static final int MAX_SNAPSHOTS_PER_ROUND = 10; // 每回合最多保留10个幽灵阵容
    private static ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

    // ============ 构造函数 ============
    public AutoChessGame() {
        this.gold = 5;
        this.level = 1;
        this.xp = 0;
        this.hp = 100;
        this.round = 1;
        this.phase = "planning";

        // 发一个随机的 1 费英雄
        Champion starter = createChampion(randomTier1Def());
        bench.add(starter);

        // 刷新商店
        refreshShopInternal();
        // 生成敌方预览
        generateEnemy();
        updateSynergies();
    }

    // ============ 公共方法 ============

    /** 刷新商店 (花 2 金币) */
    public String refreshShop() {
        if (phase.equals("battle")) return "战斗中无法刷新";
        if (phase.equals("gameover")) return "游戏已结束";
        if (gold < 2) return "金币不足";
        gold -= 2;
        refreshShopInternal();
        return null;
    }

    /** 购买商店槽位 0-4 */
    public String buy(int slot) {
        if (phase.equals("battle")) return "战斗中无法购买";
        if (phase.equals("gameover")) return "游戏已结束";
        if (slot < 0 || slot >= shop.size()) return "无效槽位";
        Champion c = shop.get(slot);
        if (c == null) return "该槽位无英雄";
        int price = c.tier * 1; // 棋子价格 = 费用*1, 1费=1金/2费=2金/3费=3金/4费=4金/5费=5金
        if (gold < price) return "金币不足";

        gold -= price;
        shop.set(slot, null);

        // 先加入备战席
        bench.add(c);

        // 迭代合并：可能连锁升星 (1星→2星→3星)
        boolean merged;
        do {
            merged = tryMergeStar(c.name);
        } while (merged);

        updateSynergies();
        return null;
    }

    /** 尝试对某名字的英雄，从1星开始找3个同星合并升星，返回是否发生了合并 */
    private boolean tryMergeStar(String name) {
        for (int star = 1; star < 3; star++) {  // 1星→2星, 2星→3星
            List<Champion> same = new ArrayList<>();
            for (Champion b : bench) {
                if (b.name.equals(name) && b.star == star) same.add(b);
            }
            for (Champion b : board.values()) {
                if (b.name.equals(name) && b.star == star) same.add(b);
            }
            if (same.size() >= 3) {
                // 保留第一件的装备，其余2件的装备归还背包
                Champion keeper = same.get(0);
                for (int i = 1; i < 3; i++) {
                    if (!same.get(i).equipment.isEmpty()) {
                        equipmentInventory.addAll(same.get(i).equipment);
                    }
                }
                // 移除3个同星，创建1个高星
                removeChampionInstance(same.get(0));
                removeChampionInstance(same.get(1));
                removeChampionInstance(same.get(2));
                Champion upgraded = createChampion(findDef(name));
                // 继承装备（保留同一类型仅一件）
                List<String> keptEquip = new ArrayList<>();
                for (String eqId : keeper.equipment) {
                    EquipmentItem eq = findEquipment(eqId);
                    if (eq != null) {
                        boolean typeExists = keptEquip.stream().anyMatch(eid -> {
                            EquipmentItem existing = findEquipment(eid);
                            return existing != null && existing.type.equals(eq.type);
                        });
                        if (!typeExists) keptEquip.add(eqId);
                        else equipmentInventory.add(eqId); // 同类型冲突的归还
                    }
                }
                upgraded.equipment = keptEquip;
                upgraded.star = star + 1;
                // 属性倍率：2星=2xHP+1.8xATK, 3星=4xHP+3.24xATK
                double hpMult = Math.pow(2, upgraded.star - 1);
                double atkMult = Math.pow(1.8, upgraded.star - 1);
                upgraded.hp = (int)(upgraded.hp * hpMult);
                upgraded.maxHp = (int)(upgraded.maxHp * hpMult);
                upgraded.atk = (int)(upgraded.atk * atkMult);
                bench.add(upgraded);
                return true;
            }
        }
        return false;
    }

    /** 出售板凳上的英雄 */
    public String sell(String uid) {
        if (phase.equals("battle")) return "战斗中无法出售";
        Champion toRemove = null;
        for (Champion b : bench) {
            if (b.uid.equals(uid)) { toRemove = b; break; }
        }
        if (toRemove == null) {
            // 也可能在棋盘上
            for (Champion b : board.values()) {
                if (b.uid.equals(uid)) { toRemove = b; break; }
            }
        }
        if (toRemove == null) return "找不到该英雄";
        // 出售时装备归还背包
        if (!toRemove.equipment.isEmpty()) {
            equipmentInventory.addAll(toRemove.equipment);
            toRemove.equipment.clear();
        }
        int refund = toRemove.tier * 1; // 不管任何星级，都只退费tier*1
        gold += refund;
        removeChampionInstance(toRemove);
        updateSynergies();
        return null;
    }

    /** 放置英雄：bench -> board */
    public String placeOnBoard(String uid, int row, int col) {
        if (phase.equals("battle")) return "战斗中无法操作";
        if (phase.equals("gameover")) return "游戏已结束";
        if (row < 0 || row >= BOARD_ROWS || col < 0 || col >= BOARD_COLS) return "无效位置";
        String key = row + "," + col;
        if (board.containsKey(key)) return "该位置已有英雄";

        // 检查上场数量
        if (board.size() >= maxFieldSize()) return "上场人数已达上限 (Lv." + level + " 最多 " + maxFieldSize() + " 人)";

        Champion c = null;
        for (Champion b : bench) {
            if (b.uid.equals(uid)) { c = b; break; }
        }
        if (c == null) {
            // 也可能从棋盘移动到棋盘
            c = board.get(uid);
            if (c == null) return "英雄不在备战席";
            // 移除旧位置
            String oldKey = null;
            for (Map.Entry<String, Champion> e : board.entrySet()) {
                if (e.getValue().uid.equals(uid)) { oldKey = e.getKey(); break; }
            }
            if (oldKey != null) board.remove(oldKey);
        } else {
            bench.remove(c);
        }
        board.put(key, c);
        updateSynergies();
        return null;
    }

    /** 下阵：board -> bench */
    public String moveToBench(String uid) {
        if (phase.equals("battle")) return "战斗中无法操作";
        if (phase.equals("gameover")) return "游戏已结束";
        if (bench.size() >= BENCH_SIZE) return "备战席已满";

        String foundKey = null;
        for (Map.Entry<String, Champion> e : board.entrySet()) {
            if (e.getValue().uid.equals(uid)) { foundKey = e.getKey(); break; }
        }
        if (foundKey == null) return "该英雄不在棋盘上";

        Champion c = board.remove(foundKey);
        bench.add(c);
        updateSynergies();
        return null;
    }

    /** 升级 (4 金币买 4 XP) */
    public String buyXp() {
        if (phase.equals("battle")) return "战斗中无法升级";
        if (phase.equals("gameover")) return "游戏已结束";
        if (level >= 8) return "已达最高等级";
        if (gold < 4) return "金币不足";
        gold -= 4;
        xp += 4;
        return tryLevelUp();
    }

    /** 开始战斗 */
    public String startBattle() {
        if (phase.equals("battle")) return "战斗已在进行";
        if (phase.equals("gameover")) return "游戏已结束";
        if (board.isEmpty()) return "请至少上场一个英雄";

        // 保存当前阵容快照（供其他玩家遭遇幽灵阵容）
        saveSnapshot();

        // 模拟战斗
        battleLog = simulateBattle();
        phase = "battle";

        // 判断结果
        boolean playerWin = enemyBoard.isEmpty();
        if (!playerWin) {
            int damage = 2 + round / 2;
            for (Champion e : enemyBoard.values()) {
                damage += e.star;
            }
            damage = Math.min(damage, 20);
            hp -= damage;
            int xpLoss = 1;
            xp += xpLoss;
            tryLevelUp();
            battleLog = "你输了！受到 " + damage + " 点伤害。+" + xpLoss + " 经验。\n" + battleLog;
            if (hp <= 0) {
                hp = 0;
                phase = "gameover";
                battleLog += "\n血量归零，游戏结束！你存活了 " + round + " 轮。";
            }
        } else {
            int xpReward = 2;
            xp += xpReward;
            tryLevelUp();
            battleLog = "你赢了！+" + xpReward + " 经验。\n" + battleLog;
        }

        return null;
    }

    /** 下一回合 */
    public String nextRound() {
        if (phase.equals("planning")) return "当前还在准备阶段";
        if (phase.equals("gameover")) return "游戏已结束，请重新开始";

        round++;
        phase = "planning";

        // 随机装备掉落（每3回合，直接掉进背包）
        String equipMsg = null;
        if (round > 0 && round % 3 == 0) {
            EquipmentItem drop = randomEquipmentDrop();
            if (drop != null) {
                equipmentInventory.add(drop.id);
                equipMsg = drop.name;
            }
        }

        // 收入：基础 4 + 回合/2(上限5) + 利息(上限2) + 装备金币
        int income = 4 + Math.min(round / 2, 5) + Math.min(gold / 10, 2);
        // 贤者之石加成
        int equipGold = 0;
        for (Champion c : board.values()) {
            for (String eqId : c.equipment) {
                EquipmentItem eq = findEquipment(eqId);
                if (eq != null) equipGold += eq.goldPerRound;
            }
        }
        income += equipGold;
        gold += income;

        // 清空商店、重新刷新
        shop.clear();
        refreshShopInternal();

        // 生成敌方预览
        generateEnemy();

        // 恢复棋盘棋子满血
        for (Champion c : board.values()) {
            c.hp = c.maxHp;
        }

        lastRoundLog = new ArrayList<>();
        if (battleLog != null) {
            lastRoundLog = Arrays.asList(battleLog.split("\n"));
        }
        battleLog = null;

        StringBuilder msg = new StringBuilder("获得 " + income + " 金币");
        if (equipGold > 0) msg.append("（含装备加成 +").append(equipGold).append("）");
        if (equipMsg != null) msg.append(" | 🎁 掉落装备: ").append(equipMsg);
        return msg.toString();
    }

    // ============ 私有方法 ============

    private void refreshShopInternal() {
        shop.clear();
        for (int i = 0; i < SHOP_SIZE; i++) {
            int tier = rollTier();
            shop.add(createChampion(randomDef(tier)));
        }
    }

    private int rollTier() {
        int[] odds = SHOP_ODDS[level];
        int roll = rng.nextInt(100);
        int cumulative = 0;
        for (int t = 0; t < odds.length; t++) {
            cumulative += odds[t];
            if (roll < cumulative) return t + 1;
        }
        return 5; // fallback
    }

    private ChampionDef randomDef(int tier) {
        List<ChampionDef> pool = new ArrayList<>();
        for (ChampionDef d : ALL_CHAMPIONS) {
            if (d.tier == tier) pool.add(d);
        }
        if (pool.isEmpty()) return ALL_CHAMPIONS[0];
        return pool.get(rng.nextInt(pool.size()));
    }

    private ChampionDef randomTier1Def() {
        return randomDef(1);
    }

    private ChampionDef findDef(String name) {
        for (ChampionDef d : ALL_CHAMPIONS) {
            if (d.name.equals(name)) return d;
        }
        return ALL_CHAMPIONS[0];
    }

    private Champion createChampion(ChampionDef def) {
        Champion c = new Champion();
        c.uid = UUID.randomUUID().toString().substring(0, 8);
        c.name = def.name;
        c.tier = def.tier;
        c.traits = def.traits;
        c.star = 1;
        c.maxHp = def.baseHp;
        c.hp = def.baseHp;
        c.atk = def.baseAtk;
        c.atkSpeed = 1.0;  // 每秒攻击 1 次
        c.atkCooldown = 0;
        c.atkType = def.atkType != null ? def.atkType : "physical";
        c.def = def.baseDef;
        c.magicDef = def.baseMagicDef;
        // 初始化技能系统
        c.maxMana = def.maxMana;
        c.currentMana = 0;
        c.manaPerAttack = def.manaPerAttack;
        c.manaPerSecond = def.manaPerSecond;
        c.skillName = def.skillName;
        c.skillType = def.skillType;
        c.skillValue = def.skillValue;
        c.skillExtra = def.skillExtra;
        return c;
    }

    private void removeChampionInstance(Champion c) {
        bench.remove(c);
        String foundKey = null;
        for (Map.Entry<String, Champion> e : board.entrySet()) {
            if (e.getValue().uid.equals(c.uid)) { foundKey = e.getKey(); break; }
        }
        if (foundKey != null) board.remove(foundKey);
    }

    private void updateSynergies() {
        // 统计棋盘上各羁绊数量
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Champion c : board.values()) {
            for (String t : c.traits) {
                counts.merge(t, 1, Integer::sum);
            }
        }
        this.traitCount = counts;

        // 计算活跃羁绊
        List<Map<String, Object>> active = new ArrayList<>();
        for (SynergyDef s : SYNERGIES) {
            int count = counts.getOrDefault(s.name, 0);
            if (count >= s.required) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", s.name);
                m.put("count", count);
                m.put("desc", s.desc);
                m.put("active", true);
                active.add(m);
            }
        }
        this.activeSynergies = active;
    }

    private String tryLevelUp() {
        while (level < 8) {
            int needed = XP_TABLE[level];
            if (xp >= needed) {
                xp -= needed;
                level++;
            } else {
                break;
            }
        }
        if (xp >= XP_TABLE[7] && level == 8) xp = XP_TABLE[7] - 1;
        return null;
    }

    private void generateEnemy() {
        // 前5回合：野怪(PVE)，给玩家发育时间
        if (round <= 5) {
            generateWildMonster();
            return;
        }

        // 第6回合起：始终使用幽灵对手
        if (tryLoadGhostEnemy()) return;

        // 没有幽灵存档：根据玩家棋子数量随机放置1星棋子作为后备
        generateFallbackEnemy();
    }

    /** 野怪生成(PVE) - 前5回合，弱到1-2个1星棋子可过 */
    private void generateWildMonster() {
        enemyBoard.clear();
        String[] monsterNames = {"野狼", "石魔", "毒蜂", "树精", "淤泥怪"};
        int monsterCount;
        double hpScale, atkScale;

        if (round <= 2) {
            monsterCount = 1;
            hpScale = 0.5;   // 血量减半
            atkScale = 0.5;
        } else if (round <= 4) {
            monsterCount = 1 + rng.nextInt(2); // 1-2只
            hpScale = 0.6;
            atkScale = 0.6;
        } else { // round == 5
            monsterCount = 2;
            hpScale = 0.7;
            atkScale = 0.7;
        }

        for (int i = 0; i < monsterCount; i++) {
            int row = rng.nextInt(BOARD_ROWS);
            int col = rng.nextInt(BOARD_COLS);
            String key = row + "," + col;
            if (enemyBoard.containsKey(key)) { i--; continue; }

            String name = monsterNames[rng.nextInt(monsterNames.length)];
            ChampionDef def = randomDef(1); // 1费棋子作为模板
            Champion c = createChampion(def);
            c.uid = "wild_" + name + "_" + i;
            c.name = name + " Lv." + round;
            c.tier = 1;
            c.star = 1;
            c.hp = (int)(c.hp * hpScale);
            c.maxHp = c.hp;
            c.atk = (int)(c.atk * atkScale);
            c.atkSpeed = 1.0; // 野怪攻击速度固定
            c.traits = new String[]{"野怪"};
            c.isPlayerSide = false;
            enemyBoard.put(key, c);
        }
        enemySource = "wild";
        battleLog = "第" + round + "回合：遭遇野怪！\n";
    }

    /** 后备敌人：幽灵存档不存在时，根据玩家棋子数量生成1星随机棋子 */
    private void generateFallbackEnemy() {
        enemyBoard.clear();
        int playerCount = board.size();
        int count = Math.max(1, Math.min(playerCount, 8)); // 至少1个，最多8个

        for (int i = 0; i < count; i++) {
            int row = rng.nextInt(BOARD_ROWS);
            int col = rng.nextInt(BOARD_COLS);
            String key = row + "," + col;
            if (enemyBoard.containsKey(key)) { i--; continue; }

            int tier = rng.nextInt(Math.min(2 + round / 6, 3)) + 1; // 随回合逐渐提升到1-3费
            Champion c = createChampion(randomDef(Math.min(tier, 5)));
            c.star = 1;
            c.isPlayerSide = false;
            enemyBoard.put(key, c);
        }
        enemySource = "fallback";
        battleLog = "本轮对手：随机影子军团（第" + round + "回合，暂无幽灵存档）\n";
    }

    /** 尝试加载幽灵阵容，成功返回true */
    private boolean tryLoadGhostEnemy() {
        try {
            File roundDir = new File(SNAPSHOT_DIR, "round_" + round);
            if (!roundDir.exists() || !roundDir.isDirectory()) return false;

            File[] files = roundDir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) return false;

            // 过滤掉自己的快照
            List<File> candidates = new ArrayList<>();
            for (File f : files) {
                if (gameSessionId.isEmpty() || !f.getName().startsWith(gameSessionId + "_")) {
                    candidates.add(f);
                }
            }
            if (candidates.isEmpty()) return false;

            // 随机选一个
            File chosen = candidates.get(rng.nextInt(candidates.size()));

            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = SNAPSHOT_MAPPER.readValue(chosen, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> champs = (List<Map<String, Object>>) snapshot.get("champions");
            if (champs == null || champs.isEmpty()) return false;

            String ghostSid = (String) snapshot.getOrDefault("sid", "???");
            enemyBoard.clear();
            for (Map<String, Object> cm : champs) {
                int row = ((Number) cm.get("row")).intValue();
                int col = ((Number) cm.get("col")).intValue();
                String key = row + "," + col;

                String name = (String) cm.get("name");
                int tier = ((Number) cm.get("tier")).intValue();
                int star = ((Number) cm.get("star")).intValue();
                int hp = ((Number) cm.get("hp")).intValue();
                int maxHp = ((Number) cm.get("maxHp")).intValue();
                int atk = ((Number) cm.get("atk")).intValue();
                double atkSpeed = ((Number) cm.get("atkSpeed")).doubleValue();
                String atkType = (String) cm.get("atkType");
                int def = ((Number) cm.get("def")).intValue();
                int magicDef = ((Number) cm.get("magicDef")).intValue();

                @SuppressWarnings("unchecked")
                List<String> traits = (List<String>) cm.get("traits");

                Champion c = new Champion();
                c.uid = "ghost_" + name + "_" + row + "_" + col;
                c.name = name;
                c.tier = tier;
                c.traits = traits != null ? traits.toArray(new String[0]) : new String[0];
                c.star = star;
                c.hp = hp;
                c.maxHp = maxHp;
                c.atk = atk;
                c.atkSpeed = atkSpeed;
                c.atkType = atkType;
                c.def = def;
                c.magicDef = magicDef;
                c.isPlayerSide = false;

                // 加载装备
                @SuppressWarnings("unchecked")
                List<String> equipment = (List<String>) cm.get("equipment");
                if (equipment != null) {
                    c.equipment = new ArrayList<>(equipment);
                }

                enemyBoard.put(key, c);
            }

            enemySource = "ghost";
            battleLog = "本轮对手是玩家 " + ghostSid + " 留下的幽灵阵容！\n";
            return true;
        } catch (IOException e) {
            System.err.println("[Ghost] 加载幽灵阵容失败: " + e.getMessage());
            return false;
        }
    }

    /** 系统随机生成敌人 */
    private void generateSystemEnemy() {
        enemyBoard.clear();
        int enemyLevel = Math.min(round, 12);
        int count = Math.min(1 + (round - 1) / 2, 8);  // 每2轮加1个敌人，最多8个
        for (int i = 0; i < count; i++) {
            // 随机位置
            int row = rng.nextInt(BOARD_ROWS);
            int col = rng.nextInt(BOARD_COLS);
            String key = row + "," + col;
            if (enemyBoard.containsKey(key)) { i--; continue; }

            int tier = 1;
            if (enemyLevel >= 12) tier = rng.nextInt(5) + 1;       // 1-5费
            else if (enemyLevel >= 9) tier = rng.nextInt(4) + 1;   // 1-4费
            else if (enemyLevel >= 7) tier = rng.nextInt(3) + 1;   // 1-3费
            else if (enemyLevel >= 4) tier = rng.nextInt(2) + 1;   // 1-2费

            Champion c = createChampion(randomDef(tier));
            c.isPlayerSide = false;
            // 随回合增强（第1回合稍弱保证新手能赢，后续每回合 +5%）
            double scale = round == 1 ? 0.75 : 1.0 + (round - 1) * 0.05;
            c.hp = (int)(c.hp * scale);
            c.maxHp = c.hp;
            c.atk = (int)(c.atk * scale);

            // 中后期给敌人升星，提升威胁
            int enemyStar = rollEnemyStar();
            if (enemyStar > 1) {
                applyStarUpgrade(c, enemyStar);
            }

            enemyBoard.put(key, c);
        }
        enemySource = "system";
    }

    /** 根据回合决定敌人星级: 1-3回全1星, 4-6回50%二星, 7-9回10%三星/60%二星, 10-12回30%三星/50%二星, 13+回50%三星/40%二星 */
    private int rollEnemyStar() {
        int roll = rng.nextInt(100);
        if (round >= 13) {
            if (roll < 50) return 3;
            if (roll < 90) return 2;
            return 1;
        } else if (round >= 10) {
            if (roll < 30) return 3;
            if (roll < 80) return 2;
            return 1;
        } else if (round >= 7) {
            if (roll < 10) return 3;
            if (roll < 70) return 2;
            return 1;
        } else if (round >= 4) {
            if (roll < 50) return 2;
            return 1;
        }
        return 1;
    }

    /** 给敌人升星: 2星 = HPx2 / ATKx1.8, 3星 = HPx4 / ATKx3.24 */
    private void applyStarUpgrade(Champion c, int targetStar) {
        c.star = targetStar;
        double hpMult = Math.pow(2, targetStar - 1);
        double atkMult = Math.pow(1.8, targetStar - 1);
        c.hp = (int)(c.hp * hpMult);
        c.maxHp = (int)(c.maxHp * hpMult);
        c.atk = (int)(c.atk * atkMult);
    }

    /** 保存当前阵容快照（供其他玩家在相同回合遇到幽灵阵容） */
    private void saveSnapshot() {
        if (gameSessionId.isEmpty() || board.isEmpty() || round <= 5) return; // 前5回合野怪关不存档
        try {
            File roundDir = new File(SNAPSHOT_DIR, "round_" + round);
            if (!roundDir.exists()) roundDir.mkdirs();

            // 清理同session旧快照
            File[] oldFiles = roundDir.listFiles((d, name) -> name.startsWith(gameSessionId + "_"));
            if (oldFiles != null) {
                for (File f : oldFiles) f.delete();
            }

            List<Map<String, Object>> champs = new ArrayList<>();
            for (Map.Entry<String, Champion> e : board.entrySet()) {
                String[] parts = e.getKey().split(",");
                Champion c = e.getValue();
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("name", c.name);
                cm.put("tier", c.tier);
                cm.put("star", c.star);
                cm.put("hp", c.maxHp);       // 保存满血状态
                cm.put("maxHp", c.maxHp);
                cm.put("atk", c.atk);
                cm.put("atkSpeed", c.atkSpeed);
                cm.put("atkType", c.atkType);
                cm.put("def", c.def);
                cm.put("magicDef", c.magicDef);
                cm.put("traits", Arrays.asList(c.traits));
                cm.put("equipment", new ArrayList<>(c.equipment));
                cm.put("row", Integer.parseInt(parts[0]));
                cm.put("col", Integer.parseInt(parts[1]));
                champs.add(cm);
            }

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("round", round);
            snapshot.put("sid", gameSessionId);
            snapshot.put("champions", champs);
            snapshot.put("timestamp", System.currentTimeMillis());

            File file = new File(roundDir, gameSessionId + "_" + System.currentTimeMillis() + ".json");
            SNAPSHOT_MAPPER.writeValue(file, snapshot);

            // 每回合最多保留MAX_SNAPSHOTS_PER_ROUND个幽灵阵容，超出则删除最早的
            File[] snapshotFiles = roundDir.listFiles((d, name) -> name.endsWith(".json"));
            if (snapshotFiles != null && snapshotFiles.length > MAX_SNAPSHOTS_PER_ROUND) {
                Arrays.sort(snapshotFiles, Comparator.comparingLong(File::lastModified));
                int deleteCount = snapshotFiles.length - MAX_SNAPSHOTS_PER_ROUND;
                for (int i = 0; i < deleteCount; i++) {
                    snapshotFiles[i].delete();
                }
            }
        } catch (IOException e) {
            System.err.println("[Snapshot] 保存快照失败: " + e.getMessage());
        }
    }

    /** 清理超过7天的旧快照 */
    public static void cleanupOldSnapshots() {
        try {
            File dir = new File(SNAPSHOT_DIR);
            if (!dir.exists() || !dir.isDirectory()) return;
            File[] roundDirs = dir.listFiles(File::isDirectory);
            if (roundDirs == null) return;

            long sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;
            for (File rd : roundDirs) {
                File[] files = rd.listFiles((d, name) -> name.endsWith(".json"));
                if (files != null) {
                    for (File f : files) {
                        if (f.lastModified() < sevenDaysAgo) {
                            f.delete();
                        }
                    }
                }
                // 删除空目录
                String[] remaining = rd.list();
                if (remaining != null && remaining.length == 0) {
                    rd.delete();
                }
            }
        } catch (Exception ignored) {}
    }

    /** 模拟战斗，返回战报并填充 battleEvents */
    private String simulateBattle() {
        battleEvents.clear();

        // 复制英雄用于战斗模拟
        // 统一网格坐标系：玩家棋盘在下方，敌方棋盘在上方
        // 玩家 row=0(上方,靠近敌方)→前排坐标3, row=3(下方,远离敌方)→后排坐标0
        // 敌方 row=0(上方,远离玩家)→后排坐标8, row=3(下方,靠近玩家)→前排坐标5
        // 统一网格: [玩家后排0..前排3] ...[战线].. [敌方前排5..后排8]
        List<BattleUnit> playerUnits = new ArrayList<>();
        for (Map.Entry<String, Champion> e : board.entrySet()) {
            String[] parts = e.getKey().split(",");
            int playerRow = Integer.parseInt(parts[0]);
            int mappedRow = (BOARD_ROWS - 1) - playerRow; // row=0→3(前排), row=3→0(后排)
            BattleUnit bu = new BattleUnit(e.getValue().copy(), true,
                mappedRow, Integer.parseInt(parts[1]));
            playerUnits.add(bu);
        }

        List<BattleUnit> enemyUnits = new ArrayList<>();
        final int ENEMY_ROW_BASE = 5; // 敌方行基址
        for (Map.Entry<String, Champion> e : enemyBoard.entrySet()) {
            String[] parts = e.getKey().split(",");
            int enemyRow = Integer.parseInt(parts[0]);
            int enemyCol = Integer.parseInt(parts[1]);
            // 敌方 row=3(下方,前排)→5, row=0(上方,后排)→8，前排面向玩家前排(row=0→3)
            int mappedRow = ENEMY_ROW_BASE + (BOARD_ROWS - 1 - enemyRow);
            BattleUnit bu = new BattleUnit(e.getValue().copy(), false,
                mappedRow, enemyCol);
            enemyUnits.add(bu);
        }

        // 记录初始血量
        Map<String, Object> initEvt = new LinkedHashMap<>();
        initEvt.put("action", "init");
        List<Map<String, Object>> playerInit = new ArrayList<>();
        for (BattleUnit u : playerUnits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", u.c.uid); m.put("name", u.c.name); m.put("hp", u.c.hp); m.put("maxHp", u.c.maxHp);
            m.put("row", u.row); m.put("col", u.col); m.put("side", "player");
            m.put("maxMana", u.c.maxMana); m.put("currentMana", 0);
            playerInit.add(m);
        }
        List<Map<String, Object>> enemyInit = new ArrayList<>();
        for (BattleUnit u : enemyUnits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uid", u.c.uid); m.put("name", u.c.name); m.put("hp", u.c.hp); m.put("maxHp", u.c.maxHp);
            m.put("row", u.row); m.put("col", u.col); m.put("side", "enemy");
            m.put("maxMana", u.c.maxMana); m.put("currentMana", 0);
            enemyInit.add(m);
        }
        initEvt.put("playerUnits", playerInit);
        initEvt.put("enemyUnits", enemyInit);
        battleEvents.add(initEvt);

        // 应用羁绊加成
        applySynergyBuffs(playerUnits);

        // 应用装备属性加成本体
        for (BattleUnit u : playerUnits) {
            applyEquipmentToUnit(u);
        }

        StringBuilder log = new StringBuilder();
        int tick = 0;
        // 战斗持续到一方全灭为止，不再使用 tick 超时机制
        final int MAX_BATTLE_EVENTS = 300; // 安全上限，防止极端情况下的无限循环

        while (battleEvents.size() < MAX_BATTLE_EVENTS) {
            tick++;
            // 所有单位攻击冷却减少 + 自然回蓝
            for (BattleUnit u : playerUnits) {
                if (u.c.hp <= 0) continue;
                u.c.atkCooldown -= 0.05;
                u.c.currentMana = Math.min(u.c.maxMana, u.c.currentMana + (int)(u.c.manaPerSecond * 0.05));
            }
            for (BattleUnit u : enemyUnits) {
                if (u.c.hp <= 0) continue;
                u.c.atkCooldown -= 0.05;
                u.c.currentMana = Math.min(u.c.maxMana, u.c.currentMana + (int)(u.c.manaPerSecond * 0.05));
            }

            // 玩家单位攻击/施放技能
            for (BattleUnit u : playerUnits) {
                if (u.c.hp <= 0) continue;
                if (u.c.atkCooldown > 0) continue;

                // 检查是否可以释放技能
                if (u.c.currentMana >= u.c.maxMana && u.c.skillType != null && !u.c.skillType.isEmpty()) {
                    u.c.currentMana = 0;
                    u.c.atkCooldown = 1.0 / u.c.atkSpeed;
                    executeSkill(u, enemyUnits, playerUnits, tick, true, battleEvents, log);
                    continue;
                }

                u.c.atkCooldown = 1.0 / u.c.atkSpeed;

                BattleUnit target = findTarget(u, enemyUnits);
                if (target == null) continue;

                // 装备：目标闪避判定
                double targetDodge = getEquipmentDodgeRate(target.c);
                if (targetDodge > 0 && rng.nextDouble() < targetDodge) {
                    log.append(target.c.name).append(" 闪避! ");
                    Map<String, Object> evt = new LinkedHashMap<>();
                    evt.put("tick", tick);
                    evt.put("attackerName", u.c.name);
                    evt.put("attackerSide", "player");
                    evt.put("attackerRow", u.row); evt.put("attackerCol", u.col);
                    evt.put("targetName", target.c.name);
                    evt.put("targetSide", "enemy");
                    evt.put("targetRow", target.row); evt.put("targetCol", target.col);
                    evt.put("action", "dodge");
                    evt.put("damage", 0);
                    evt.put("damageType", "physical");
                    evt.put("targetHpAfter", target.c.hp);
                    evt.put("targetMaxHp", target.c.maxHp);
                    evt.put("stunned", false);
                    battleEvents.add(evt);
                    u.c.currentMana += u.c.manaPerAttack;
                    continue;
                }

                int baseDmg = (int)(u.c.atk * (1.0 + u.buffAtkPct));
                int hitCount = 1;
                String action = "attack";
                boolean stunned = false;

                // 计算实际伤害（考虑防御类型和装备穿透）
                String dmgType = u.c.atkType != null ? u.c.atkType : "physical";
                int targetDef = dmgType.equals("magic") ? target.c.magicDef : target.c.def;
                // 虚空 护甲穿透
                if (hasActiveSynergy("虚空", playerUnits) && hasTrait(u, "虚空") && dmgType.equals("physical")) {
                    targetDef = (int)(targetDef * 0.8);
                }
                // 装备护甲/魔抗穿透
                if (dmgType.equals("physical")) targetDef = Math.max(0, targetDef - getEquipmentArpen(u.c));
                else targetDef = Math.max(0, targetDef - getEquipmentMrpen(u.c));

                int effectiveDmg = Math.max(baseDmg - targetDef, baseDmg / 4);

                // 极冰 晕眩
                if (hasActiveSynergy("极冰", playerUnits) && hasTrait(u, "极冰") && rng.nextInt(100) < 20) {
                    target.stunned = 3;
                    log.append(u.c.name).append(" 眩晕了 ").append(target.c.name).append("! ");
                    stunned = true;
                }
                // 刺客暴击 + 装备暴击率
                double totalCritRate = getEquipmentCritRate(u.c);
                if (hasTrait(u, "刺客")) totalCritRate += 0.30;
                if (totalCritRate > 0 && rng.nextInt(100) < (int)(totalCritRate * 100)) {
                    baseDmg = (int)(baseDmg * 2.0);
                    action = "crit";
                    log.append(u.c.name).append(" 暴击! ");
                }
                // 剑客 连击
                if (hasTrait(u, "剑客") && rng.nextInt(100) < 30) {
                    hitCount = 2;
                    action = "double";
                    log.append(u.c.name).append(" 连击! ");
                }

                int totalDmg = effectiveDmg * hitCount;
                target.c.hp -= totalDmg;
                if (target.c.hp <= 0) {
                    target.c.hp = 0;
                    action = "kill";
                    log.append(target.c.name).append(" 被击杀. ");
                }

                // 装备：物理吸血
                double lifesteal = getEquipmentLifesteal(u.c);
                if (dmgType.equals("physical") && lifesteal > 0) {
                    int heal = (int)(totalDmg * lifesteal);
                    u.c.hp = Math.min(u.c.maxHp, u.c.hp + heal);
                    if (heal > 0) log.append("(").append(u.c.name).append(" 吸血+").append(heal).append(") ");
                }

                // 装备：反弹伤害
                double reflect = getEquipmentReflect(target.c);
                if (dmgType.equals("physical") && reflect > 0) {
                    int reflectDmg = (int)(totalDmg * reflect);
                    u.c.hp -= reflectDmg;
                    if (u.c.hp <= 0) u.c.hp = 0;
                    if (reflectDmg > 0) log.append("(").append(target.c.name).append(" 反弹").append(reflectDmg).append(") ");
                }

                // 普通攻击回蓝
                u.c.currentMana = Math.min(u.c.maxMana, u.c.currentMana + u.c.manaPerAttack);

                // 记录战斗事件
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("tick", tick);
                evt.put("attackerName", u.c.name);
                evt.put("attackerSide", "player");
                evt.put("attackerRow", u.row); evt.put("attackerCol", u.col);
                evt.put("targetName", target.c.name);
                evt.put("targetSide", "enemy");
                evt.put("targetRow", target.row); evt.put("targetCol", target.col);
                evt.put("action", action);
                evt.put("damage", totalDmg);
                evt.put("damageType", dmgType);
                evt.put("targetHpAfter", Math.max(0, target.c.hp));
                evt.put("targetMaxHp", target.c.maxHp);
                evt.put("stunned", stunned);
                battleEvents.add(evt);
            }

            // 敌方单位攻击/施放技能
            for (BattleUnit u : enemyUnits) {
                if (u.c.hp <= 0) continue;
                if (u.stunned > 0) { u.stunned--; continue; }
                if (u.c.atkCooldown > 0) continue;

                // 检查是否可以释放技能
                if (u.c.currentMana >= u.c.maxMana && u.c.skillType != null && !u.c.skillType.isEmpty()) {
                    u.c.currentMana = 0;
                    u.c.atkCooldown = 1.0 / u.c.atkSpeed;
                    executeSkill(u, playerUnits, enemyUnits, tick, false, battleEvents, log);
                    continue;
                }

                u.c.atkCooldown = 1.0 / u.c.atkSpeed;

                BattleUnit target = findTarget(u, playerUnits);
                if (target == null) continue;

                // 装备：玩家闪避判定
                double playerDodge = getEquipmentDodgeRate(target.c);
                if (playerDodge > 0 && rng.nextDouble() < playerDodge) {
                    log.append(target.c.name).append(" 闪避! ");
                    Map<String, Object> evt = new LinkedHashMap<>();
                    evt.put("tick", tick);
                    evt.put("attackerName", u.c.name);
                    evt.put("attackerSide", "enemy");
                    evt.put("attackerRow", u.row); evt.put("attackerCol", u.col);
                    evt.put("targetName", target.c.name);
                    evt.put("targetSide", "player");
                    evt.put("targetRow", target.row); evt.put("targetCol", target.col);
                    evt.put("action", "dodge");
                    evt.put("damage", 0);
                    evt.put("damageType", "physical");
                    evt.put("targetHpAfter", target.c.hp);
                    evt.put("targetMaxHp", target.c.maxHp);
                    evt.put("stunned", false);
                    battleEvents.add(evt);
                    continue;
                }

                String edmgType = u.c.atkType != null ? u.c.atkType : "physical";
                int eTargetDef = edmgType.equals("magic") ? target.c.magicDef : target.c.def;
                // 装备穿透
                if (edmgType.equals("physical")) eTargetDef = Math.max(0, eTargetDef - getEquipmentArpen(u.c));
                else eTargetDef = Math.max(0, eTargetDef - getEquipmentMrpen(u.c));

                int effectiveDmg = Math.max(u.c.atk - eTargetDef, u.c.atk / 4);
                target.c.hp -= effectiveDmg;
                String action = "attack";
                if (target.c.hp <= 0) {
                    target.c.hp = 0;
                    action = "kill";
                    log.append(target.c.name).append(" 被击杀. ");
                }

                // 敌方普通攻击回蓝
                u.c.currentMana = Math.min(u.c.maxMana, u.c.currentMana + u.c.manaPerAttack);

                // 装备：玩家反弹伤害
                double reflect = getEquipmentReflect(target.c);
                if (edmgType.equals("physical") && reflect > 0) {
                    int reflectDmg = (int)(effectiveDmg * reflect);
                    u.c.hp -= reflectDmg;
                    if (u.c.hp <= 0) u.c.hp = 0;
                }

                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("tick", tick);
                evt.put("attackerName", u.c.name);
                evt.put("attackerSide", "enemy");
                evt.put("attackerRow", u.row); evt.put("attackerCol", u.col);
                evt.put("targetName", target.c.name);
                evt.put("targetSide", "player");
                evt.put("targetRow", target.row); evt.put("targetCol", target.col);
                evt.put("action", action);
                evt.put("damage", effectiveDmg);
                evt.put("damageType", edmgType);
                evt.put("targetHpAfter", Math.max(0, target.c.hp));
                evt.put("targetMaxHp", target.c.maxHp);
                evt.put("stunned", false);
                battleEvents.add(evt);
            }

            // 减少眩晕 + 技能Buff递减
            for (BattleUnit u : playerUnits) {
                if (u.stunned > 0) u.stunned--;
                if (u.buffAtkTicks > 0) { u.buffAtkTicks--; if (u.buffAtkTicks == 0) u.buffAtkPct = 0; }
            }
            for (BattleUnit u : enemyUnits) {
                if (u.stunned > 0) u.stunned--;
                if (u.buffAtkTicks > 0) { u.buffAtkTicks--; if (u.buffAtkTicks == 0) u.buffAtkPct = 0; }
            }

            // 检查胜负
            boolean playerAlive = playerUnits.stream().anyMatch(u -> u.c.hp > 0);
            boolean enemyAlive = enemyUnits.stream().anyMatch(u -> u.c.hp > 0);

            if (!playerAlive || !enemyAlive) break;
        }

        // 战斗自然结束，不再需要超时判定

        // 更新实际血量
        for (BattleUnit bu : playerUnits) {
            for (Champion c : board.values()) {
                if (c.uid.equals(bu.c.uid)) c.hp = Math.max(0, bu.c.hp);
            }
        }
        if (enemyUnits.stream().noneMatch(u -> u.c.hp > 0)) {
            enemyBoard.clear();
        } else {
            // 移除死掉的敌方单位
            enemyBoard.entrySet().removeIf(e -> e.getValue() == null || 
                enemyUnits.stream().anyMatch(bu -> bu.c.uid.equals(e.getValue().uid) && bu.c.hp <= 0));
        }

        return log.length() > 0 ? log.toString() : "双方未分出胜负...";
    }

    /** 执行技能效果 */
    private void executeSkill(BattleUnit u, List<BattleUnit> enemies, List<BattleUnit> allies,
                               int tick, boolean isPlayer, List<Map<String, Object>> battleEvents,
                               StringBuilder log) {
        String skillType = u.c.skillType;
        int skillVal = u.c.skillValue;  // 百分比, 200=200%
        int extra = u.c.skillExtra;     // 额外参数(stun tick数等)

        // 应用Buff后的ATK
        double skillAtk = u.c.atk * (1.0 + u.buffAtkPct);

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("tick", tick);
        evt.put("attackerName", u.c.name);
        evt.put("attackerSide", isPlayer ? "player" : "enemy");
        evt.put("attackerRow", u.row);
        evt.put("attackerCol", u.col);
        evt.put("action", "skill");
        evt.put("skillName", u.c.skillName);
        evt.put("skillType", skillType);

        log.append(u.c.name).append(" 释放 [").append(u.c.skillName).append("]! ");

        switch (skillType) {
            case "single": {
                BattleUnit target = findTarget(u, enemies);
                if (target != null) {
                    int dmg = (int)(skillAtk * skillVal / 100);
                    dmg = Math.max(dmg - target.c.def / 2, dmg / 3);
                    target.c.hp -= dmg;
                    if (target.c.hp <= 0) target.c.hp = 0;
                    evt.put("targetName", target.c.name);
                    evt.put("targetSide", isPlayer ? "enemy" : "player");
                    evt.put("targetRow", target.row);
                    evt.put("targetCol", target.col);
                    evt.put("damage", dmg);
                    evt.put("targetHpAfter", Math.max(0, target.c.hp));
                    evt.put("targetMaxHp", target.c.maxHp);
                    log.append("对").append(target.c.name).append("造成").append(dmg).append("伤害. ");
                }
                break;
            }
            case "aoe": {
                BattleUnit primary = findTarget(u, enemies);
                List<BattleUnit> aoeTargets = new ArrayList<>();
                if (primary != null) aoeTargets.add(primary);
                for (BattleUnit e : enemies) {
                    if (e.c.hp <= 0 || e == primary) continue;
                    if (Math.abs(u.row - e.row) <= 1 && Math.abs(u.col - e.col) <= 1)
                        aoeTargets.add(e);
                }
                List<Map<String, Object>> aoeList = new ArrayList<>();
                for (BattleUnit t : aoeTargets) {
                    if (t.c.hp <= 0) continue;
                    int dmg = (int)(skillAtk * skillVal / 100);
                    dmg = Math.max(dmg - t.c.def / 2, dmg / 3);
                    t.c.hp -= dmg;
                    if (t.c.hp <= 0) t.c.hp = 0;
                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("name", t.c.name); tm.put("row", t.row); tm.put("col", t.col);
                    tm.put("damage", dmg); tm.put("hpAfter", t.c.hp); tm.put("maxHp", t.c.maxHp);
                    aoeList.add(tm);
                    log.append(t.c.name).append("(-").append(dmg).append(") ");
                }
                evt.put("aoeTargets", aoeList);
                break;
            }
            case "aoe_all": {
                List<Map<String, Object>> aoeList = new ArrayList<>();
                for (BattleUnit t : enemies) {
                    if (t.c.hp <= 0) continue;
                    int dmg = (int)(skillAtk * skillVal / 100);
                    dmg = Math.max(dmg - t.c.def / 2, dmg / 3);
                    t.c.hp -= dmg;
                    if (t.c.hp <= 0) t.c.hp = 0;
                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("name", t.c.name); tm.put("row", t.row); tm.put("col", t.col);
                    tm.put("damage", dmg); tm.put("hpAfter", t.c.hp); tm.put("maxHp", t.c.maxHp);
                    aoeList.add(tm);
                    log.append(t.c.name).append("(-").append(dmg).append(") ");
                }
                evt.put("aoeTargets", aoeList);
                break;
            }
            case "buff_self": {
                u.buffAtkPct = skillVal / 100.0;
                u.buffAtkTicks = 100; // 5秒 (100 ticks * 0.05s)
                evt.put("buffType", "atk");
                evt.put("buffPct", skillVal);
                log.append("ATK+").append(skillVal).append("% 持续5秒. ");
                break;
            }
            case "buff_team": {
                for (BattleUnit a : allies) {
                    if (a.c.hp <= 0) continue;
                    a.buffAtkPct = skillVal / 100.0;
                    a.buffAtkTicks = 100;
                }
                evt.put("buffType", "atk");
                evt.put("buffPct", skillVal);
                evt.put("buffAllies", true);
                log.append("全体友军ATK+").append(skillVal).append("% 持续5秒. ");
                break;
            }
            case "stun_single": {
                BattleUnit target = findTarget(u, enemies);
                if (target != null) {
                    target.stunned = extra > 0 ? extra : 3;
                    evt.put("targetName", target.c.name);
                    evt.put("targetSide", isPlayer ? "enemy" : "player");
                    evt.put("targetRow", target.row);
                    evt.put("targetCol", target.col);
                    evt.put("stunDuration", extra);
                    log.append("眩晕").append(target.c.name).append("! ");
                }
                break;
            }
            case "stun_aoe": {
                BattleUnit primary = findTarget(u, enemies);
                List<Map<String, Object>> aoeList = new ArrayList<>();
                for (BattleUnit e : enemies) {
                    if (e.c.hp <= 0) continue;
                    if (primary != null && e == primary) { e.stunned = extra; continue; }
                    if (Math.abs(u.row - e.row) <= 1 && Math.abs(u.col - e.col) <= 1)
                        e.stunned = extra;
                }
                if (primary != null) {
                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("name", primary.c.name); tm.put("row", primary.row); tm.put("col", primary.col);
                    tm.put("stunned", true);
                    aoeList.add(tm);
                }
                evt.put("aoeTargets", aoeList);
                evt.put("stunDuration", extra);
                log.append("范围眩晕! ");
                break;
            }
            case "shield": {
                int shieldAmt = u.c.maxHp * skillVal / 100;
                u.c.hp = Math.min(u.c.maxHp, u.c.hp + shieldAmt);
                evt.put("shieldAmount", shieldAmt);
                evt.put("healAmount", shieldAmt);
                log.append("获得").append(shieldAmt).append("点护盾. ");
                break;
            }
            case "revive": {
                BattleUnit deadAlly = null;
                for (int i = allies.size() - 1; i >= 0; i--) {
                    // 找最近阵亡的友军（按加入顺序）
                }
                // 简化处理：治疗所有友军
                int healPct = skillVal;
                List<Map<String, Object>> healList = new ArrayList<>();
                for (BattleUnit a : allies) {
                    if (a.c.hp <= 0) {
                        a.c.hp = a.c.maxHp * healPct / 100;
                        Map<String, Object> hm = new LinkedHashMap<>();
                        hm.put("name", a.c.name); hm.put("row", a.row); hm.put("col", a.col);
                        hm.put("revived", true); hm.put("hpAfter", a.c.hp);
                        healList.add(hm);
                        log.append("复活").append(a.c.name).append("! ");
                    } else {
                        int healAmt = a.c.maxHp * skillVal / 300; // 复活技能对存活单位治疗量为1/3
                        a.c.hp = Math.min(a.c.maxHp, a.c.hp + healAmt);
                        Map<String, Object> hm = new LinkedHashMap<>();
                        hm.put("name", a.c.name); hm.put("row", a.row); hm.put("col", a.col);
                        hm.put("healed", true); hm.put("hpAfter", a.c.hp);
                        healList.add(hm);
                    }
                }
                evt.put("aoeTargets", healList);
                break;
            }
            case "teleport": {
                // 传送到血量最低的敌人旁并攻击
                BattleUnit lowestHp = null;
                int lowestHpVal = Integer.MAX_VALUE;
                for (BattleUnit e : enemies) {
                    if (e.c.hp <= 0) continue;
                    if (e.c.hp < lowestHpVal) { lowestHpVal = e.c.hp; lowestHp = e; }
                }
                if (lowestHp != null) {
                    u.row = lowestHp.row;
                    u.col = Math.max(0, lowestHp.col - 1);
                    int dmg = (int)(skillAtk * skillVal / 100);
                    dmg = Math.max(dmg - lowestHp.c.def / 2, dmg / 3);
                    lowestHp.c.hp -= dmg;
                    if (lowestHp.c.hp <= 0) lowestHp.c.hp = 0;
                    evt.put("targetName", lowestHp.c.name);
                    evt.put("targetSide", isPlayer ? "enemy" : "player");
                    evt.put("targetRow", lowestHp.row);
                    evt.put("targetCol", lowestHp.col);
                    evt.put("damage", dmg);
                    evt.put("targetHpAfter", Math.max(0, lowestHp.c.hp));
                    evt.put("targetMaxHp", lowestHp.c.maxHp);
                    log.append("穿梭至").append(lowestHp.c.name).append("造成").append(dmg).append("伤害. ");
                }
                break;
            }
        }
        battleEvents.add(evt);
    }

    private BattleUnit findTarget(BattleUnit attacker, List<BattleUnit> enemies) {
        BattleUnit closest = null;
        int minDist = Integer.MAX_VALUE;
        for (BattleUnit e : enemies) {
            if (e.c.hp <= 0) continue;
            int dist = Math.abs(attacker.row - e.row) + Math.abs(attacker.col - e.col);
            if (dist < minDist) { minDist = dist; closest = e; }
        }
        return closest;
    }

    private void applySynergyBuffs(List<BattleUnit> units) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, List<BattleUnit>> traitUnits = new LinkedHashMap<>();
        for (BattleUnit u : units) {
            for (String t : u.c.traits) {
                counts.merge(t, 1, Integer::sum);
                traitUnits.computeIfAbsent(t, k -> new ArrayList<>()).add(u);
            }
        }

        for (SynergyDef s : SYNERGIES) {
            int count = counts.getOrDefault(s.name, 0);
            if (count < s.required) continue;
            List<BattleUnit> affected = traitUnits.getOrDefault(s.name, List.of());

            switch (s.name) {
                case "贵族":
                    for (BattleUnit u : affected) { u.c.hp += 120; u.c.maxHp += 120; }
                    break;
                case "骑士团":
                    for (BattleUnit u : affected) { u.c.atk = (int)(u.c.atk * 0.82); u.c.hp += 100; u.c.maxHp += 100; }
                    break;
                case "游侠":
                    for (BattleUnit u : affected) { u.c.atkSpeed *= 1.3; }
                    break;
                case "帝国":
                    for (BattleUnit u : affected) { u.c.atk = (int)(u.c.atk * 1.35); }
                    break;
                case "野兽":
                    for (BattleUnit u : units) { u.c.atkSpeed *= 1.2; }
                    break;
                case "法师":
                    for (BattleUnit u : affected) { u.c.atk = (int)(u.c.atk * 1.4); }
                    break;
                case "格斗家":
                    for (BattleUnit u : affected) { u.c.hp += 180; u.c.maxHp += 180; }
                    break;
                case "恶魔":
                    for (BattleUnit u : affected) { u.c.atk = (int)(u.c.atk * 1.15); }
                    break;
                // 极冰/刺客/剑客/虚空 在战斗过程中处理
            }
        }
    }

    private boolean hasActiveSynergy(String name, List<BattleUnit> units) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BattleUnit u : units) {
            for (String t : u.c.traits) counts.merge(t, 1, Integer::sum);
        }
        for (SynergyDef s : SYNERGIES) {
            if (s.name.equals(name) && counts.getOrDefault(name, 0) >= s.required) return true;
        }
        return false;
    }

    private boolean hasTrait(BattleUnit u, String trait) {
        for (String t : u.c.traits) if (t.equals(trait)) return true;
        return false;
    }

    // ============ 装备系统方法 ============

    /** 根据ID查找装备定义 */
    public static EquipmentItem findEquipment(String id) {
        for (EquipmentItem e : ALL_EQUIPMENT) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    /** 生成装备掉落选择（3选1） */
    /** 随机装备掉落 */
    private EquipmentItem randomEquipmentDrop() {
        if (ALL_EQUIPMENT.length == 0) return null;
        return ALL_EQUIPMENT[rng.nextInt(ALL_EQUIPMENT.length)];
    }

    /** 装备道具到英雄 */
    public String equipItem(String championUid, String equipmentId) {
        if (phase.equals("battle")) return "战斗中无法操作";
        if (phase.equals("gameover")) return "游戏已结束";

        // 在背包中查找装备
        if (!equipmentInventory.contains(equipmentId)) return "背包中没有该装备";

        EquipmentItem eq = findEquipment(equipmentId);
        if (eq == null) return "无效装备";

        // 查找目标英雄
        Champion target = null;
        for (Champion c : board.values()) {
            if (c.uid.equals(championUid)) { target = c; break; }
        }
        if (target == null) {
            for (Champion c : bench) {
                if (c.uid.equals(championUid)) { target = c; break; }
            }
        }
        if (target == null) return "找不到该英雄";

        // 检查装备数量上限
        if (target.equipment.size() >= 3) return "该英雄已佩戴3件装备";

        // 检查同类型装备限制
        for (String eId : target.equipment) {
            EquipmentItem existing = findEquipment(eId);
            if (existing != null && existing.type.equals(eq.type)) {
                return "该英雄已有同类型装备（" + existing.name + "）";
            }
        }

        equipmentInventory.remove(equipmentId);
        target.equipment.add(equipmentId);
        return null;
    }

    /** 卸下英雄装备 */
    public String unequipItem(String championUid, String equipmentId) {
        if (phase.equals("battle")) return "战斗中无法操作";
        if (phase.equals("gameover")) return "游戏已结束";

        Champion target = null;
        for (Champion c : board.values()) {
            if (c.uid.equals(championUid)) { target = c; break; }
        }
        if (target == null) {
            for (Champion c : bench) {
                if (c.uid.equals(championUid)) { target = c; break; }
            }
        }
        if (target == null) return "找不到该英雄";

        if (!target.equipment.contains(equipmentId)) return "该英雄没有这件装备";

        target.equipment.remove(equipmentId);
        equipmentInventory.add(equipmentId);
        return null;
    }

    /** 将装备属性应用到战斗单位 */
    private void applyEquipmentToUnit(BattleUnit bu) {
        for (String eqId : bu.c.equipment) {
            EquipmentItem eq = findEquipment(eqId);
            if (eq == null) continue;
            bu.c.atk += eq.atkBonus;
            bu.c.def += eq.defBonus;
            bu.c.magicDef += eq.magicDefBonus;
            bu.c.atkSpeed *= (1.0 + eq.atkSpeedBonus);
            bu.c.maxHp += eq.hpBonus;
            bu.c.hp += eq.hpBonus;
        }
    }

    /** 获取单位装备的总属性 */
    private double getEquipmentDodgeRate(Champion c) {
        double total = 0;
        for (String eqId : c.equipment) {
            EquipmentItem eq = findEquipment(eqId);
            if (eq != null) total += eq.dodgeRate;
        }
        return total;
    }

    private double getEquipmentCritRate(Champion c) {
        double total = 0;
        for (String eqId : c.equipment) {
            EquipmentItem eq = findEquipment(eqId);
            if (eq != null) total += eq.critRate;
        }
        return total;
    }

    private double getEquipmentLifesteal(Champion c) {
        double total = 0;
        for (String eqId : c.equipment) {
            EquipmentItem eq = findEquipment(eqId);
            if (eq != null) total += eq.lifesteal;
        }
        return total;
    }

    private int getEquipmentArpen(Champion c) {
        int total = 0;
        for (String eqId : c.equipment) {
            EquipmentItem eq = findEquipment(eqId);
            if (eq != null) total += eq.arpen;
        }
        return total;
    }

    private int getEquipmentMrpen(Champion c) {
        int total = 0;
        for (String eqId : c.equipment) {
            EquipmentItem eq = findEquipment(eqId);
            if (eq != null) total += eq.mrpen;
        }
        return total;
    }

    private double getEquipmentReflect(Champion c) {
        double total = 0;
        for (String eqId : c.equipment) {
            EquipmentItem eq = findEquipment(eqId);
            if (eq != null) total += eq.reflectDmg;
        }
        return total;
    }

    // ============ 战斗内部类 ============
    private static class BattleUnit {
        Champion c;
        boolean isPlayer;
        int row, col;
        int stunned = 0;
        int buffAtkTicks = 0;   // ATK增益剩余tick数
        double buffAtkPct = 0;  // ATK增益百分比(0.5=50%)
        BattleUnit(Champion c, boolean isPlayer, int row, int col) {
            this.c = c; this.isPlayer = isPlayer; this.row = row; this.col = col;
            this.c.currentMana = 0;
        }
    }
}
