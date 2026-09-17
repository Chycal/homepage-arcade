package com.homepage.model;

import java.time.LocalDateTime;

/**
 * 挂机生活 - 玩家存档
 */
public class IdleLifeSave {
    private Long id;
    private Long userId;
    private String username;
    private int level = 1;
    private long exp = 0;
    private long gold = 0;
    private int currentDungeon = 0;      // 0=未在挂机，>0 为副本id
    private String potionItem;           // 战斗中携带的药水 key，null 表示不携带
    private String helmet;               // 已装备的头盔 itemKey
    private String chest;                // 已装备的胸甲 itemKey
    private String legs;                 // 已装备的腿甲 itemKey
    private String boots;                // 已装备的靴子 itemKey
    private long bossLastBattle = 0;     // 上次 boss 战时间戳（毫秒）
    private long battlesWon = 0;         // 累计击败小怪数
    private long bossesKilled = 0;       // 累计击败 boss 数
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public long getExp() { return exp; }
    public void setExp(long exp) { this.exp = exp; }
    public long getGold() { return gold; }
    public void setGold(long gold) { this.gold = gold; }
    public int getCurrentDungeon() { return currentDungeon; }
    public void setCurrentDungeon(int currentDungeon) { this.currentDungeon = currentDungeon; }
    public String getPotionItem() { return potionItem; }
    public void setPotionItem(String potionItem) { this.potionItem = potionItem; }
    public String getHelmet() { return helmet; }
    public void setHelmet(String helmet) { this.helmet = helmet; }
    public String getChest() { return chest; }
    public void setChest(String chest) { this.chest = chest; }
    public String getLegs() { return legs; }
    public void setLegs(String legs) { this.legs = legs; }
    public String getBoots() { return boots; }
    public void setBoots(String boots) { this.boots = boots; }
    public long getBossLastBattle() { return bossLastBattle; }
    public void setBossLastBattle(long bossLastBattle) { this.bossLastBattle = bossLastBattle; }
    public long getBattlesWon() { return battlesWon; }
    public void setBattlesWon(long battlesWon) { this.battlesWon = battlesWon; }
    public long getBossesKilled() { return bossesKilled; }
    public void setBossesKilled(long bossesKilled) { this.bossesKilled = bossesKilled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
