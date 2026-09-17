package com.homepage.repository;

import com.homepage.model.IdleLifeSave;
import com.homepage.model.Item;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 挂机生活 - 存档与背包数据访问层
 */
@Repository
public class IdleLifeRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<IdleLifeSave> saveMapper = (rs, rowNum) -> {
        IdleLifeSave s = new IdleLifeSave();
        s.setId(rs.getLong("id"));
        s.setUserId(rs.getLong("user_id"));
        s.setUsername(rs.getString("username"));
        s.setLevel(rs.getInt("level"));
        s.setExp(rs.getLong("exp"));
        s.setGold(rs.getLong("gold"));
        s.setCurrentDungeon(rs.getInt("current_dungeon"));
        s.setPotionItem(rs.getString("potion_item"));
        s.setHelmet(rs.getString("helmet"));
        s.setChest(rs.getString("chest"));
        s.setLegs(rs.getString("legs"));
        s.setBoots(rs.getString("boots"));
        s.setBossLastBattle(rs.getLong("boss_last_battle"));
        s.setBattlesWon(rs.getLong("battles_won"));
        s.setBossesKilled(rs.getLong("bosses_killed"));
        s.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        Timestamp ut = rs.getTimestamp("updated_at");
        s.setUpdatedAt(ut != null ? ut.toLocalDateTime() : null);
        return s;
    };

    private final RowMapper<Item> itemMapper = (rs, rowNum) -> {
        Item it = new Item();
        it.setId(rs.getLong("id"));
        it.setUsername(rs.getString("username"));
        it.setItemKey(rs.getString("item_key"));
        it.setItemType(rs.getString("item_type"));
        it.setSlot(rs.getString("slot"));
        it.setName(rs.getString("name"));
        it.setQuantity(rs.getInt("quantity"));
        it.setLevel(rs.getInt("level"));
        it.setAttack(rs.getInt("attack"));
        it.setPhysDef(rs.getInt("phys_def"));
        it.setMagicDef(rs.getInt("magic_def"));
        it.setDodge(rs.getDouble("dodge"));
        it.setHp(rs.getInt("hp"));
        it.setDesc(rs.getString("item_desc"));
        return it;
    };

    public IdleLifeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 存档 ====================

    public Optional<IdleLifeSave> findSaveByUsername(String username) {
        var list = jdbc.query("SELECT * FROM idle_life_saves WHERE username = ?", saveMapper, username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public IdleLifeSave insertSave(IdleLifeSave s) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO idle_life_saves (user_id, username, level, exp, gold, current_dungeon, " +
                "potion_item, helmet, chest, legs, boots, boss_last_battle, battles_won, bosses_killed, " +
                "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, s.getUserId());
            ps.setString(2, s.getUsername());
            ps.setInt(3, s.getLevel());
            ps.setLong(4, s.getExp());
            ps.setLong(5, s.getGold());
            ps.setInt(6, s.getCurrentDungeon());
            ps.setString(7, s.getPotionItem());
            ps.setString(8, s.getHelmet());
            ps.setString(9, s.getChest());
            ps.setString(10, s.getLegs());
            ps.setString(11, s.getBoots());
            ps.setLong(12, s.getBossLastBattle());
            ps.setLong(13, s.getBattlesWon());
            ps.setLong(14, s.getBossesKilled());
            LocalDateTime now = LocalDateTime.now();
            ps.setTimestamp(15, Timestamp.valueOf(now));
            ps.setTimestamp(16, Timestamp.valueOf(now));
            return ps;
        }, kh);
        Number id = kh.getKey();
        if (id != null) s.setId(id.longValue());
        return s;
    }

    public void updateSave(IdleLifeSave s) {
        jdbc.update(
            "UPDATE idle_life_saves SET level=?, exp=?, gold=?, current_dungeon=?, potion_item=?, " +
            "helmet=?, chest=?, legs=?, boots=?, boss_last_battle=?, battles_won=?, bosses_killed=?, " +
            "updated_at=? WHERE id=?",
            s.getLevel(), s.getExp(), s.getGold(), s.getCurrentDungeon(), s.getPotionItem(),
            s.getHelmet(), s.getChest(), s.getLegs(), s.getBoots(), s.getBossLastBattle(),
            s.getBattlesWon(), s.getBossesKilled(), Timestamp.valueOf(LocalDateTime.now()), s.getId());
    }

    // ==================== 背包 ====================

    public List<Item> findItems(String username) {
        return jdbc.query("SELECT * FROM idle_life_items WHERE username = ? ORDER BY item_type, id",
                itemMapper, username);
    }

    public Optional<Item> findItem(String username, String itemKey) {
        var list = jdbc.query("SELECT * FROM idle_life_items WHERE username = ? AND item_key = ?",
                itemMapper, username, itemKey);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void insertItem(Item item) {
        jdbc.update(
            "INSERT INTO idle_life_items (username, item_key, item_type, slot, name, quantity, level, " +
            "attack, phys_def, magic_def, dodge, hp, item_desc) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)",
            item.getUsername(), item.getItemKey(), item.getItemType(), item.getSlot(), item.getName(),
            item.getQuantity(), item.getLevel(), item.getAttack(), item.getPhysDef(), item.getMagicDef(),
            item.getDodge(), item.getHp(), item.getDesc());
    }

    /** 扣减物品数量，数量为0时删除 */
    public void consumeItem(String username, String itemKey, int amount) {
        jdbc.update("UPDATE idle_life_items SET quantity = quantity - ? WHERE username = ? AND item_key = ?",
                amount, username, itemKey);
        jdbc.update("DELETE FROM idle_life_items WHERE username = ? AND item_key = ? AND quantity <= 0",
                username, itemKey);
    }
}
