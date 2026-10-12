package com.homepage.lasvegas;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 赌场结算规则 —— 纯函数，不持有任何状态。
 *
 * 官方规则（alea 2012）：
 *  1. 先检查该赌场是否有 ≥2 名玩家的骰子数相同；这些玩家「全部」作废，
 *     收回骰子且从该赌场什么都拿不到。
 *     （官方例 2：2/2/1/1 → 四人全灭，该赌场无人得分。）
 *  2. 其余玩家按骰子数「降序」依次领取该赌场面额从高到低的钞票，
 *     每人最多 1 张；钞票不够时排在后面的玩家空手。
 *  3. 结算后仍剩余的钞票面朝下放回钞票堆底部。
 *
 * 由于步骤 1 已把「同数量」的组整组作废，步骤 2 中剩余玩家的骰子数必然互不相同，
 * 因此降序排列不存在并列歧义。
 */
public final class LasVegasPayout {

    private LasVegasPayout() {
    }

    /**
     * 结算单个赌场。
     *
     * @param dieValue  赌场点数 1-6
     * @param banknotes 该赌场当前持有的钞票面额
     * @param diceCount 座位 -> 该玩家在此赌场的骰子数（可含 0 或负值，会被忽略）
     */
    public static CasinoPayout settle(int dieValue,
                                      List<Integer> banknotes,
                                      Map<Integer, Integer> diceCount) {

        // ---- 1. 按骰子数分组，找出平手作废的玩家 ----
        Map<Integer, List<Integer>> byCount = new TreeMap<>();
        for (Map.Entry<Integer, Integer> entry : diceCount.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count <= 0) {
                continue;
            }
            byCount.computeIfAbsent(count, k -> new ArrayList<>()).add(entry.getKey());
        }

        Set<Integer> voided = new TreeSet<>();
        for (List<Integer> seats : byCount.values()) {
            if (seats.size() >= 2) {
                voided.addAll(seats);
            }
        }

        // ---- 2. 剩余玩家按骰子数降序，依次领最高面额钞票 ----
        List<Map.Entry<Integer, Integer>> ranked = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : diceCount.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count > 0 && !voided.contains(entry.getKey())) {
                ranked.add(new AbstractMap.SimpleEntry<>(entry.getKey(), count));
            }
        }
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<Integer> notes = new ArrayList<>(banknotes);
        notes.sort(Comparator.reverseOrder());

        LinkedHashMap<Integer, Integer> awards = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<Integer, Integer> entry : ranked) {
            if (index >= notes.size()) {
                break; // 钞票不够，后面的玩家空手而归
            }
            awards.put(entry.getKey(), notes.get(index));
            index++;
        }

        // ---- 3. 剩余钞票 ----
        List<Integer> leftover = new ArrayList<>(notes.subList(index, notes.size()));

        return new CasinoPayout(dieValue, new ArrayList<>(voided), awards, leftover);
    }
}
