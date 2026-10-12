package com.homepage.lasvegas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 赌场结算规则测试。
 *
 * 全部期望值直接取自 alea 官方规则书（© 2012 Ravensburger，编号 230512）中的
 * 示例原文，不由被测代码推导，以保证测试具备独立的事实来源。
 */
class LasVegasPayoutTest {

    private static Map<Integer, Integer> dice(int... seatCountPairs) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < seatCountPairs.length; i += 2) {
            map.put(seatCountPairs[i], seatCountPairs[i + 1]);
        }
        return map;
    }

    @Nested
    @DisplayName("官方规则书示例")
    class OfficialExamples {

        @Test
        @DisplayName("例1：5 / 3 / 3 / 1 —— 平手的两人全部作废，余者按骰数降序领钞")
        void example1() {
            // 规则书原文：「Anna receives the $80,000 note and Denny the $30,000 note.
            //            The $10,000 note is returned to the bottom of the pile.」
            CasinoPayout payout = LasVegasPayout.settle(2,
                    Arrays.asList(10000, 30000, 80000),
                    dice(0, 5, 1, 3, 2, 3, 3, 1));

            assertThat(payout.getVoidedSeats()).containsExactly(1, 2);
            assertThat(payout.getAwards()).containsExactly(entry(0, 80000), entry(3, 30000));
            assertThat(payout.getLeftover()).containsExactly(10000);
        }

        @Test
        @DisplayName("例2：2 / 1 / 2 / 1 —— 两组均平手，四人全灭，该赌场无人得分")
        void example2() {
            // 规则书原文：「Anna and Carla both added 2 dice ... while Benno and Denny
            //            each added 1: All players take their dice back.」
            CasinoPayout payout = LasVegasPayout.settle(1,
                    Arrays.asList(40000, 40000),
                    dice(0, 2, 1, 1, 2, 2, 3, 1));

            assertThat(payout.getVoidedSeats()).containsExactly(0, 1, 2, 3);
            assertThat(payout.getAwards()).isEmpty();
            assertThat(payout.getLeftover()).containsExactly(40000, 40000);
        }

        @Test
        @DisplayName("例3：钞票不够分时，排在最后的玩家空手而归")
        void example3() {
            // 规则书原文：「Carla receives the $70,000 note and Benno the $20,000 note.
            //            Denny gets nothing.」
            CasinoPayout payout = LasVegasPayout.settle(4,
                    Arrays.asList(70000, 20000),
                    dice(1, 2, 2, 3, 3, 1));

            assertThat(payout.getVoidedSeats()).isEmpty();
            assertThat(payout.getAwards()).containsExactly(entry(2, 70000), entry(1, 20000));
            assertThat(payout.getLeftover()).isEmpty();
        }
    }

    @Nested
    @DisplayName("边界情形")
    class EdgeCases {

        @Test
        @DisplayName("只有一名玩家放骰：独得最高面额钞票")
        void singlePlayerTakesHighestNote() {
            CasinoPayout payout = LasVegasPayout.settle(5,
                    Arrays.asList(10000, 50000, 20000),
                    dice(3, 4));

            assertThat(payout.getAwards()).containsExactly(entry(3, 50000));
            assertThat(payout.getLeftover()).containsExactly(20000, 10000);
        }

        @Test
        @DisplayName("空赌场：无作废、无得奖，钞票全部留存")
        void emptyCasino() {
            CasinoPayout payout = LasVegasPayout.settle(6,
                    Arrays.asList(10000, 40000),
                    Collections.emptyMap());

            assertThat(payout.getVoidedSeats()).isEmpty();
            assertThat(payout.getAwards()).isEmpty();
            assertThat(payout.getLeftover()).containsExactly(40000, 10000);
        }

        @Test
        @DisplayName("骰子数为 0 的玩家视为未参与：不影响平手判定，也不领钞")
        void zeroDiceIgnored() {
            CasinoPayout payout = LasVegasPayout.settle(3,
                    Arrays.asList(10000, 40000, 50000),
                    dice(0, 3, 1, 0, 2, 2));

            // 座位1 无骰不参与；座位0(3颗) 与 座位2(2颗) 数量不同，均不作废
            assertThat(payout.getVoidedSeats()).isEmpty();
            // 3 张钞票充足，但座位1 仍什么都拿不到
            assertThat(payout.getAwards()).containsExactly(entry(0, 50000), entry(2, 40000));
            assertThat(payout.getLeftover()).containsExactly(10000);
        }

        @Test
        @DisplayName("骰子数为 0 的玩家不参与平手判定：与有骰玩家同数量也不会作废")
        void zeroDiceDoesNotCreateTie() {
            CasinoPayout payout = LasVegasPayout.settle(6,
                    Arrays.asList(20000),
                    dice(0, 2, 1, 0));

            // 若把 0 颗也算作一组，座位0/座位1 会被误判平手；正确行为是无人作废
            assertThat(payout.getVoidedSeats()).isEmpty();
            assertThat(payout.getAwards()).containsExactly(entry(0, 20000));
            assertThat(payout.getLeftover()).isEmpty();
        }

        @Test
        @DisplayName("钞票不足以覆盖所有剩余玩家时，多余玩家无奖")
        void notEnoughNotes() {
            CasinoPayout payout = LasVegasPayout.settle(1,
                    Arrays.asList(90000),
                    dice(0, 3, 1, 2, 2, 1));

            assertThat(payout.getAwards()).containsExactly(entry(0, 90000));
            assertThat(payout.getLeftover()).isEmpty();
        }
    }
}
