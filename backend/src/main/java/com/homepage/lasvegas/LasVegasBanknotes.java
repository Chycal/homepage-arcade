package com.homepage.lasvegas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 拉斯维加斯钞票堆工具。
 *
 * 官方配件（alea 2012 规则书，编号 230512）：
 *   5 张 × $60,000 / $70,000 / $80,000 / $90,000
 *   6 张 × $10,000 / $40,000 / $50,000
 *   8 张 × $20,000 / $30,000
 *   合计 54 张，总价值 $2,500,000
 */
public final class LasVegasBanknotes {

    /** 钞票总张数 */
    public static final int TOTAL_NOTES = 54;

    /** 全部钞票总价值（用于守恒校验） */
    public static final int TOTAL_VALUE = 2_500_000;

    private LasVegasBanknotes() {
    }

    /** 构建并洗混 54 张钞票 */
    public static List<Integer> buildDeck(Random random) {
        List<Integer> notes = new ArrayList<>(TOTAL_NOTES);
        add(notes, 60000, 5);
        add(notes, 70000, 5);
        add(notes, 80000, 5);
        add(notes, 90000, 5);
        add(notes, 10000, 6);
        add(notes, 40000, 6);
        add(notes, 50000, 6);
        add(notes, 20000, 8);
        add(notes, 30000, 8);
        Collections.shuffle(notes, random);
        return notes;
    }

    private static void add(List<Integer> notes, int value, int count) {
        for (int i = 0; i < count; i++) {
            notes.add(value);
        }
    }

    /** 一组钞票的总面额 */
    public static int totalValue(List<Integer> notes) {
        int sum = 0;
        for (int note : notes) {
            sum += note;
        }
        return sum;
    }
}
