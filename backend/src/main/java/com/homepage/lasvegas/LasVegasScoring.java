package com.homepage.lasvegas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 排名与胜负判定 —— 纯函数，与游戏状态解耦，便于独立测试。
 *
 * 官方规则（alea 2012）：
 *   "The player with the most is the winner. In the event of a tie, then the winner
 *    is the player with the most banknotes. If there is still a tie then there are
 *    multiple winners."
 *
 * 即：先比钱 → 再比钞票「张数」→ 仍相同则并列胜利。
 */
public final class LasVegasScoring {

    private LasVegasScoring() {
    }

    /** 某名玩家的战绩快照 */
    public static class Standing {
        private final int seat;
        private final int money;
        private final int banknotes;

        public Standing(int seat, int money, int banknotes) {
            this.seat = seat;
            this.money = money;
            this.banknotes = banknotes;
        }

        public int getSeat() {
            return seat;
        }

        public int getMoney() {
            return money;
        }

        public int getBanknotes() {
            return banknotes;
        }
    }

    /** 按「钱降序 → 钞票张数降序」排序，返回新列表，不修改入参 */
    public static List<Standing> ranking(List<Standing> standings) {
        List<Standing> sorted = new ArrayList<>(standings);
        sorted.sort((a, b) -> {
            if (a.money != b.money) {
                return Integer.compare(b.money, a.money);
            }
            return Integer.compare(b.banknotes, a.banknotes);
        });
        return Collections.unmodifiableList(sorted);
    }

    /**
     * 判定胜者座位。
     * 钱最多者胜；平手比钞票张数；仍平手则并列。
     */
    public static List<Integer> winnerSeats(List<Standing> standings) {
        if (standings.isEmpty()) {
            return Collections.emptyList();
        }
        int bestMoney = Integer.MIN_VALUE;
        for (Standing s : standings) {
            bestMoney = Math.max(bestMoney, s.money);
        }
        int bestNotes = Integer.MIN_VALUE;
        for (Standing s : standings) {
            if (s.money == bestMoney) {
                bestNotes = Math.max(bestNotes, s.banknotes);
            }
        }
        List<Integer> winners = new ArrayList<>();
        for (Standing s : standings) {
            if (s.money == bestMoney && s.banknotes == bestNotes) {
                winners.add(s.seat);
            }
        }
        Collections.sort(winners);
        return winners;
    }
}
