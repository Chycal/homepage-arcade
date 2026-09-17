package com.homepage.model;

/**
 * 挂机生活 - 物品模型
 * itemType: EQUIPMENT(装备) / MATERIAL(素材) / POTION(药水)
 * slot: helmet(头盔) / chest(胸甲) / legs(腿甲) / boots(靴子) / none
 */
public class Item {
    private Long id;
    private String username;
    private String itemKey;    // 唯一标识，如 eq_wolf_helmet / mat_wolf / potion_1
    private String itemType;
    private String slot;
    private String name;
    private int quantity;
    private int level;         // 装备/药水等级要求（素材为0）
    private int attack;        // 攻击力加成
    private int physDef;       // 物理防御加成
    private int magicDef;      // 法术防御加成
    private double dodge;      // 闪避加成（%）
    private int hp;            // 生命加成（装备）/ 回复量（药水）
    private String desc;       // 描述

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getAttack() { return attack; }
    public void setAttack(int attack) { this.attack = attack; }
    public int getPhysDef() { return physDef; }
    public void setPhysDef(int physDef) { this.physDef = physDef; }
    public int getMagicDef() { return magicDef; }
    public void setMagicDef(int magicDef) { this.magicDef = magicDef; }
    public double getDodge() { return dodge; }
    public void setDodge(double dodge) { this.dodge = dodge; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
}
