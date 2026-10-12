package com.homepage.lasvegas;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单个赌场一轮的结算结果（不可变）。
 *
 * @see LasVegasPayout#settle
 */
public class CasinoPayout {

    private final int dieValue;
    private final List<Integer> voidedSeats;
    private final Map<Integer, Integer> awards;
    private final List<Integer> leftover;

    public CasinoPayout(int dieValue,
                        List<Integer> voidedSeats,
                        Map<Integer, Integer> awards,
                        List<Integer> leftover) {
        this.dieValue = dieValue;
        this.voidedSeats = Collections.unmodifiableList(voidedSeats);
        this.awards = Collections.unmodifiableMap(awards);
        this.leftover = Collections.unmodifiableList(leftover);
    }

    /** 赌场点数 1-6 */
    public int getDieValue() {
        return dieValue;
    }

    /** 因骰子数相同而全部作废、颗粒无收的座位（已收回骰子） */
    public List<Integer> getVoidedSeats() {
        return voidedSeats;
    }

    /** 座位 -> 获得的钞票面额（按骰子数降序，每人最多 1 张） */
    public Map<Integer, Integer> getAwards() {
        return awards;
    }

    /** 结算后仍留在赌场、需面朝下放回钞票堆底部的钞票 */
    public List<Integer> getLeftover() {
        return leftover;
    }
}
