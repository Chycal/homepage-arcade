package com.homepage.lasvegas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 排名与胜负判定测试。
 * 依据官方规则书：「先比钱 → 再比钞票张数 → 仍平手则并列胜利」。
 */
class LasVegasScoringTest {

    private static LasVegasScoring.Standing s(int seat, int money, int banknotes) {
        return new LasVegasScoring.Standing(seat, money, banknotes);
    }

    @Nested
    @DisplayName("排名")
    class Ranking {

        @Test
        @DisplayName("按金额降序排列")
        void sortsByMoneyDescending() {
            List<LasVegasScoring.Standing> ranked = LasVegasScoring.ranking(Arrays.asList(
                    s(0, 100000, 3), s(1, 300000, 5), s(2, 200000, 4)));

            assertThat(ranked).extracting(LasVegasScoring.Standing::getSeat)
                    .containsExactly(1, 2, 0);
        }

        @Test
        @DisplayName("金额相同时按钞票张数降序")
        void breaksTieByBanknoteCount() {
            List<LasVegasScoring.Standing> ranked = LasVegasScoring.ranking(Arrays.asList(
                    s(0, 200000, 2), s(1, 200000, 5), s(2, 200000, 3)));

            assertThat(ranked).extracting(LasVegasScoring.Standing::getSeat)
                    .containsExactly(1, 2, 0);
        }

        @Test
        @DisplayName("不修改传入列表")
        void doesNotMutateInput() {
            List<LasVegasScoring.Standing> input =
                    Arrays.asList(s(0, 100000, 1), s(1, 300000, 1));
            LasVegasScoring.ranking(input);

            assertThat(input).extracting(LasVegasScoring.Standing::getSeat).containsExactly(0, 1);
        }
    }

    @Nested
    @DisplayName("胜负判定")
    class Winners {

        @Test
        @DisplayName("钱最多者胜")
        void mostMoneyWins() {
            List<Integer> winners = LasVegasScoring.winnerSeats(Arrays.asList(
                    s(0, 100000, 3), s(1, 250000, 2), s(2, 90000, 6)));

            assertThat(winners).containsExactly(1);
        }

        @Test
        @DisplayName("金额平手时，钞票张数多者胜")
        void tieBrokenByBanknoteCount() {
            List<Integer> winners = LasVegasScoring.winnerSeats(Arrays.asList(
                    s(0, 200000, 3), s(1, 200000, 5), s(2, 100000, 9)));

            assertThat(winners).containsExactly(1);
        }

        @Test
        @DisplayName("金额与张数都相同 → 并列胜利")
        void sharedVictory() {
            List<Integer> winners = LasVegasScoring.winnerSeats(Arrays.asList(
                    s(0, 200000, 4), s(1, 100000, 9), s(2, 200000, 4)));

            assertThat(winners).containsExactly(0, 2);
        }

        @Test
        @DisplayName("全员零分时全部并列")
        void allZeroSharedVictory() {
            List<Integer> winners = LasVegasScoring.winnerSeats(Arrays.asList(
                    s(0, 0, 0), s(1, 0, 0)));

            assertThat(winners).containsExactly(0, 1);
        }

        @Test
        @DisplayName("空列表无胜者")
        void emptyHasNoWinner() {
            assertThat(LasVegasScoring.winnerSeats(Collections.emptyList())).isEmpty();
        }
    }
}
