package com.homepage.lasvegas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钞票堆构成测试。
 * 面额与张数取自 alea 官方规则书配件清单原文：
 *   「54 banknotes (5 each of $60,000, $70,000, $80,000 and $90,000;
 *     6 each of $10,000, $40,000 and $50,000; 8 each of $20,000 and $30,000)」
 */
class LasVegasBanknotesTest {

    private static Map<Integer, Long> histogram(List<Integer> notes) {
        return notes.stream().collect(Collectors.groupingBy(n -> n, Collectors.counting()));
    }

    @Test
    @DisplayName("共 54 张钞票")
    void hasFiftyFourNotes() {
        assertThat(LasVegasBanknotes.buildDeck(new Random(1)))
                .hasSize(LasVegasBanknotes.TOTAL_NOTES);
    }

    @Test
    @DisplayName("总面额为 $2,500,000")
    void totalValueIsCorrect() {
        List<Integer> deck = LasVegasBanknotes.buildDeck(new Random(1));

        assertThat(LasVegasBanknotes.totalValue(deck))
                .isEqualTo(LasVegasBanknotes.TOTAL_VALUE)
                .isEqualTo(2_500_000);
    }

    @Test
    @DisplayName("各面额张数与规则书一致")
    void denominationCountsMatchRulebook() {
        Map<Integer, Long> counts = histogram(LasVegasBanknotes.buildDeck(new Random(7)));

        assertThat(counts).containsOnlyKeys(
                10000, 20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000);
        assertThat(counts.get(10000)).isEqualTo(6L);
        assertThat(counts.get(20000)).isEqualTo(8L);
        assertThat(counts.get(30000)).isEqualTo(8L);
        assertThat(counts.get(40000)).isEqualTo(6L);
        assertThat(counts.get(50000)).isEqualTo(6L);
        assertThat(counts.get(60000)).isEqualTo(5L);
        assertThat(counts.get(70000)).isEqualTo(5L);
        assertThat(counts.get(80000)).isEqualTo(5L);
        assertThat(counts.get(90000)).isEqualTo(5L);
    }

    @Test
    @DisplayName("不同随机源产生不同顺序，但牌面集合相同")
    void shufflesButKeepsSameMultiset() {
        List<Integer> a = LasVegasBanknotes.buildDeck(new Random(1));
        List<Integer> b = LasVegasBanknotes.buildDeck(new Random(2));

        assertThat(histogram(a)).isEqualTo(histogram(b));
        assertThat(a).isNotEqualTo(b);
    }
}
