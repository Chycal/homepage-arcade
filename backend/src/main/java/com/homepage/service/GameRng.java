package com.homepage.service;

import java.util.*;

/**
 * 确定性伪随机数生成器（LCG 算法）
 *
 * 与前端 JS 使用相同算法，确保两端生成完全一致的食物序列。
 * 算法：state = (state * 1664525 + 1013904223) & 0xFFFFFFFF
 * 这是 Numerical Recipes 中的经典参数，32 位溢出行为在 Java/JS 中一致。
 */
public class GameRng {

    private int state;

    public GameRng(int seed) {
        this.state = seed;
    }

    /**
     * 返回 [0, 1) 的随机浮点数
     */
    public double next() {
        // Java int 溢出自动截断为低 32 位，等价于 JS (x >>> 0)
        state = state * 1664525 + 1013904223;
        return (state & 0xFFFFFFFFL) / 4294967296.0;
    }

    /**
     * 返回 [0, bound) 的随机整数
     */
    public int nextInt(int bound) {
        return (int) (next() * bound);
    }
}
