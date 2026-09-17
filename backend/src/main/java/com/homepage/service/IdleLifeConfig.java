package com.homepage.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 挂机生活 - 静态游戏配置
 * 定义副本、怪物、装备、素材、药水等所有数值数据。
 * 数值设计原则：
 *  - 小怪：同级裸装玩家可稳胜
 *  - Boss：比副本等级要求高约10级且穿戴该副本全套装备的玩家可战胜，低等级玩家必败
 *  - 装备不做百分比增幅，全部为固定数值
 */
public final class IdleLifeConfig {

    /** 每升一级所需经验 = level * EXP_PER_LEVEL */
    public static final int EXP_PER_LEVEL = 60;
    /** boss 战间隔（毫秒） */
    public static final long BOSS_INTERVAL_MS = 600_000L;

    public static final class Monster {
        public final String key;
        public final String name;
        public final int level;
        public final int attack;
        public final int physDef;
        public final int magicDef;
        public final double dodge;   // %
        public final int hp;
        public final int exp;
        public final int goldMin;
        public final int goldMax;
        public final String material;    // 素材名（掉落素材时用）
        public final String materialKey; // 素材 itemKey
        public final String equipKey;    // 专属装备 itemKey

        public Monster(String key, String name, int level, int attack, int physDef, int magicDef,
                       double dodge, int hp, int exp, int goldMin, int goldMax,
                       String materialKey, String material, String equipKey) {
            this.key = key;
            this.name = name;
            this.level = level;
            this.attack = attack;
            this.physDef = physDef;
            this.magicDef = magicDef;
            this.dodge = dodge;
            this.hp = hp;
            this.exp = exp;
            this.goldMin = goldMin;
            this.goldMax = goldMax;
            this.materialKey = materialKey;
            this.material = material;
            this.equipKey = equipKey;
        }
    }

    public static final class Dungeon {
        public final int id;
        public final String name;
        public final String desc;
        public final int reqLevel;         // 建议等级
        public final List<Monster> monsters;
        public final Monster boss;

        public Dungeon(int id, String name, String desc, int reqLevel,
                       List<Monster> monsters, Monster boss) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.reqLevel = reqLevel;
            this.monsters = monsters;
            this.boss = boss;
        }
    }

    public static final class EquipDef {
        public final String key;
        public final String name;
        public final String slot;    // helmet/chest/legs/boots
        public final int level;
        public final int attack;
        public final int physDef;
        public final int magicDef;
        public final double dodge;
        public final int hp;

        public EquipDef(String key, String name, String slot, int level,
                        int attack, int physDef, int magicDef, double dodge, int hp) {
            this.key = key;
            this.name = name;
            this.slot = slot;
            this.level = level;
            this.attack = attack;
            this.physDef = physDef;
            this.magicDef = magicDef;
            this.dodge = dodge;
            this.hp = hp;
        }
    }

    public static final class PotionDef {
        public final String key;
        public final String name;
        public final int reqLevel;
        public final int heal;

        public PotionDef(String key, String name, int reqLevel, int heal) {
            this.key = key;
            this.name = name;
            this.reqLevel = reqLevel;
            this.heal = heal;
        }
    }

    /** 副本列表（按 id 顺序） */
    public static final List<Dungeon> DUNGEONS = new ArrayList<>();
    /** 装备定义表 */
    public static final Map<String, EquipDef> EQUIPS = new LinkedHashMap<>();
    /** 药水定义表 */
    public static final Map<String, PotionDef> POTIONS = new LinkedHashMap<>();

    // ======================== 副本1：魔兽森林 ========================
    static {
        // ---- 小怪（1级，无装备要求）----
        // 魔兽狼 - 掉落狼皮头盔
        DUNGEONS.add(new Dungeon(1, "魔兽森林", "初出茅庐的冒险者之森，林中魔兽尚不凶猛", 0,
            List.of(
                new Monster("wolf", "魔兽狼", 1, 8, 3, 3, 8, 50, 25, 3, 8,
                    "mat_wolf", "狼皮", "eq_wolf_helmet"),
                new Monster("boar", "野猪", 1, 7, 5, 2, 4, 70, 25, 3, 8,
                    "mat_boar", "野猪皮", "eq_boar_chest"),
                new Monster("snake", "毒蛇", 1, 9, 2, 4, 14, 40, 25, 3, 8,
                    "mat_snake", "蛇皮", "eq_snake_legs"),
                new Monster("slime", "史莱姆", 1, 6, 4, 4, 2, 90, 25, 3, 8,
                    "mat_slime", "史莱姆凝胶", "eq_slime_boots")
            ),
            new Monster("wolfking", "森林狼王", 10, 55, 20, 16, 10, 900, 300, 100, 200,
                "mat_wolfking", "狼王鬃毛", "eq_wolfking_helmet")));

        // ---- 装备（1级）----
        EQUIPS.put("eq_wolf_helmet", new EquipDef("eq_wolf_helmet", "狼皮头盔", "helmet", 1, 2, 1, 0, 0, 15));
        EQUIPS.put("eq_boar_chest", new EquipDef("eq_boar_chest", "野猪胸甲", "chest", 1, 2, 2, 0, 0, 20));
        EQUIPS.put("eq_snake_legs", new EquipDef("eq_snake_legs", "蛇皮腿甲", "legs", 1, 2, 0, 1, 1, 15));
        EQUIPS.put("eq_slime_boots", new EquipDef("eq_slime_boots", "软泥靴子", "boots", 1, 1, 2, 0, 0, 10));
        // ---- 装备（10级，boss 掉落）----
        EQUIPS.put("eq_wolfking_helmet", new EquipDef("eq_wolfking_helmet", "狼王头盔", "helmet", 10, 7, 5, 0, 0, 45));
        EQUIPS.put("eq_wolfking_chest", new EquipDef("eq_wolfking_chest", "狼王胸甲", "chest", 10, 7, 6, 2, 0, 55));
        EQUIPS.put("eq_wolfking_legs", new EquipDef("eq_wolfking_legs", "狼王腿甲", "legs", 10, 6, 5, 0, 2, 45));
        EQUIPS.put("eq_wolfking_boots", new EquipDef("eq_wolfking_boots", "狼王靴子", "boots", 10, 5, 5, 0, 0, 35));
    }

    // ======================== 副本2：火焰山前小径 ========================
    static {
        DUNGEONS.add(new Dungeon(2, "火焰山前小径", "炽热山麓的小径，火兽横行，需15级以上实力", 15,
            List.of(
                new Monster("lizard", "火蜥蜴", 15, 60, 22, 16, 12, 420, 235, 15, 30,
                    "mat_lizard", "火蜥蜴鳞", "eq_lizard_helmet"),
                new Monster("magboar", "熔岩野猪", 15, 56, 28, 12, 6, 520, 235, 15, 30,
                    "mat_magboar", "熔岩硬壳", "eq_magboar_chest"),
                new Monster("firebird", "火焰鸟", 15, 66, 16, 20, 22, 330, 235, 15, 30,
                    "mat_firebird", "火焰羽毛", "eq_firebird_legs"),
                new Monster("scorp", "焦土蝎", 15, 52, 26, 18, 8, 480, 235, 15, 30,
                    "mat_scorp", "焦土甲壳", "eq_scorp_boots")
            ),
            new Monster("magmatroll", "熔岩巨魔", 25, 120, 52, 38, 12, 2400, 900, 500, 800,
                "mat_magmatroll", "熔岩核心", "eq_magmatroll_helmet")));

        // ---- 装备（15级）----
        EQUIPS.put("eq_lizard_helmet", new EquipDef("eq_lizard_helmet", "火蜥蜴头盔", "helmet", 15, 9, 4, 2, 0, 55));
        EQUIPS.put("eq_magboar_chest", new EquipDef("eq_magboar_chest", "熔岩胸甲", "chest", 15, 9, 6, 0, 0, 70));
        EQUIPS.put("eq_firebird_legs", new EquipDef("eq_firebird_legs", "火羽腿甲", "legs", 15, 8, 4, 2, 2, 55));
        EQUIPS.put("eq_scorp_boots", new EquipDef("eq_scorp_boots", "灰烬靴子", "boots", 15, 7, 5, 0, 0, 45));
        // ---- 装备（25级，boss 掉落）----
        EQUIPS.put("eq_magmatroll_helmet", new EquipDef("eq_magmatroll_helmet", "熔岩巨魔头盔", "helmet", 25, 16, 10, 0, 0, 90));
        EQUIPS.put("eq_magmatroll_chest", new EquipDef("eq_magmatroll_chest", "熔岩巨魔胸甲", "chest", 25, 16, 12, 4, 0, 110));
        EQUIPS.put("eq_magmatroll_legs", new EquipDef("eq_magmatroll_legs", "熔岩巨魔腿甲", "legs", 25, 14, 10, 0, 3, 90));
        EQUIPS.put("eq_magmatroll_boots", new EquipDef("eq_magmatroll_boots", "熔岩巨魔靴子", "boots", 25, 12, 10, 0, 0, 70));
    }

    // ======================== 副本3：火焰山内 ========================
    static {
        DUNGEONS.add(new Dungeon(3, "火焰山内", "火焰山腹地，烈焰滔天，仅强者可入，需30级以上", 30,
            List.of(
                new Monster("flamedemon", "烈焰魔", 30, 120, 42, 32, 14, 900, 460, 40, 80,
                    "mat_flamedemon", "烈焰精华", "eq_flamedemon_helmet"),
                new Monster("fireelement", "火元素", 30, 115, 52, 25, 8, 1100, 460, 40, 80,
                    "mat_fireelement", "元素核心", "eq_fireelement_chest"),
                new Monster("lavadragon", "岩浆龙", 30, 130, 35, 38, 20, 750, 460, 40, 80,
                    "mat_lavadragon", "岩浆龙鳞", "eq_lavadragon_legs"),
                new Monster("guard", "火焰守卫", 30, 110, 48, 35, 10, 1000, 460, 40, 80,
                    "mat_guard", "守卫徽记", "eq_guard_boots")
            ),
            new Monster("flameking", "炎魔之王", 40, 200, 90, 70, 15, 5200, 2000, 2000, 3000,
                "mat_flameking", "炎魔之核", "eq_flameking_helmet")));

        // ---- 装备（30级）----
        EQUIPS.put("eq_flamedemon_helmet", new EquipDef("eq_flamedemon_helmet", "烈焰头盔", "helmet", 30, 16, 7, 3, 0, 90));
        EQUIPS.put("eq_fireelement_chest", new EquipDef("eq_fireelement_chest", "烈焰胸甲", "chest", 30, 16, 10, 0, 0, 110));
        EQUIPS.put("eq_lavadragon_legs", new EquipDef("eq_lavadragon_legs", "岩浆腿甲", "legs", 30, 14, 7, 3, 3, 90));
        EQUIPS.put("eq_guard_boots", new EquipDef("eq_guard_boots", "烈焰靴子", "boots", 30, 12, 8, 0, 0, 70));
        // ---- 装备（40级，boss 掉落）----
        EQUIPS.put("eq_flameking_helmet", new EquipDef("eq_flameking_helmet", "炎魔之王头盔", "helmet", 40, 28, 18, 0, 0, 150));
        EQUIPS.put("eq_flameking_chest", new EquipDef("eq_flameking_chest", "炎魔之王胸甲", "chest", 40, 28, 20, 8, 0, 180));
        EQUIPS.put("eq_flameking_legs", new EquipDef("eq_flameking_legs", "炎魔之王腿甲", "legs", 40, 25, 18, 0, 4, 150));
        EQUIPS.put("eq_flameking_boots", new EquipDef("eq_flameking_boots", "炎魔之王靴子", "boots", 40, 22, 18, 0, 0, 120));
    }

    // ======================== 药水 ========================
    static {
        POTIONS.put("potion_1", new PotionDef("potion_1", "1级治疗药水", 1, 60));
        POTIONS.put("potion_15", new PotionDef("potion_15", "15级治疗药水", 15, 250));
        POTIONS.put("potion_30", new PotionDef("potion_30", "30级治疗药水", 30, 600));
    }

    private IdleLifeConfig() {}

    public static Dungeon getDungeon(int id) {
        for (Dungeon d : DUNGEONS) {
            if (d.id == id) return d;
        }
        return null;
    }
}
