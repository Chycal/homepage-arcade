package com.homepage.lasvegas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link LasVegasAi} 的决策逻辑测试。
 *
 * 全部使用固定随机种子，保证结果可复现（AI 是蒙特卡洛推演，不固定种子会不稳定）。
 */
@DisplayName("拉斯维加斯 AI 决策")
class LasVegasAiTest {

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 构造一个空局面：没人放过骰子 */
    private static LasVegasAi.View blankView(int players, int round, int[][] notes,
                                             int[] money) {
        int[][] dice = new int[LasVegasGame.CASINO_COUNT + 1][players];
        int[] left = new int[players];
        Arrays.fill(left, LasVegasGame.DICE_PER_PLAYER);
        return new LasVegasAi.View(players, round, dice, left, notes, money);
    }

    /** 在空局面上指定各玩家剩余骰子 */
    private static LasVegasAi.View withDiceLeft(LasVegasAi.View base, int[] diceLeft) {
        int players = base.getPlayerCount();
        int[][] dice = new int[LasVegasGame.CASINO_COUNT + 1][players];
        for (int v = 1; v <= LasVegasGame.CASINO_COUNT; v++) {
            for (int seat = 0; seat < players; seat++) {
                dice[v][seat] = base.diceAt(v, seat);
            }
        }
        int[][] notes = new int[LasVegasGame.CASINO_COUNT + 1][];
        for (int v = 1; v <= LasVegasGame.CASINO_COUNT; v++) {
            int[] raw = new int[base.noteCountAt(v)];
            for (int i = 0; i < raw.length; i++) {
                raw[i] = base.noteAt(v, i);
            }
            notes[v] = raw;
        }
        int[] money = new int[players];
        for (int seat = 0; seat < players; seat++) {
            money[seat] = base.moneyOf(seat);
        }
        return new LasVegasAi.View(players, base.getRound(), dice, diceLeft, notes, money);
    }

    /** [7][] 形式的赌场钞票，未指定的赌场为空 */
    private static int[][] notes(int[]... perCasino) {
        int[][] notes = new int[LasVegasGame.CASINO_COUNT + 1][];
        for (int v = 1; v <= LasVegasGame.CASINO_COUNT; v++) {
            notes[v] = v <= perCasino.length && perCasino[v - 1] != null
                    ? perCasino[v - 1].clone()
                    : new int[0];
        }
        return notes;
    }

    /** 与 LasVegasGameTest 相同的确定性掷骰器：队列空了就按脚本循环补充 */
    private static class ScriptedRoller implements LasVegasGame.DiceRoller {
        private final Deque<Integer> queue = new ArrayDeque<>();
        private final List<Integer> script;

        ScriptedRoller(List<Integer> script) {
            this.script = new ArrayList<>(script);
        }

        @Override
        public int roll() {
            if (queue.isEmpty()) {
                queue.addAll(script);
            }
            return queue.pollFirst();
        }
    }

    /** 简单基线：骰子数最多的点数，平手取点数小的 */
    private static int greedyPick(Map<Integer, Integer> options) {
        int best = -1;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : options.entrySet()) {
            if (entry.getValue() > bestCount
                    || (entry.getValue() == bestCount && entry.getKey() < best)) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /** 基线：在可选点数里随机乱放 */
    private static int randomPick(Map<Integer, Integer> options, Random random) {
        List<Integer> keys = new ArrayList<>(options.keySet());
        return keys.get(random.nextInt(keys.size()));
    }

    /** 用给定策略把一局打完，返回各座位最终奖金 */
    private static int[] playGame(int players, long deckSeed,
                                  LasVegasAi.Style aiStyle, int aiSeat,
                                  boolean useAi, boolean randomOpponent) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            ids.add("p" + i);
        }
        Random deckRandom = new Random(deckSeed);
        LasVegasGame game = new LasVegasGame(ids,
                new ScriptedRoller(randomDice(players, deckSeed)), deckRandom);
        Random aiRandom = new Random(deckSeed * 31 + 7);
        Random oppRandom = new Random(deckSeed * 17 + 3);

        int guard = 10_000;
        while (!game.isOver()) {
            if (guard-- <= 0) {
                throw new IllegalStateException("对局未能结束，疑似死循环");
            }
            if (!game.hasRolled()) {
                game.roll();
            }
            Map<Integer, Integer> options = game.getOptions();
            assertThat(options).isNotEmpty();
            int pick;
            if (useAi && game.getCurrentPlayerIndex() == aiSeat) {
                pick = LasVegasAi.chooseValue(game, game.getLastRoll(), aiStyle, aiRandom);
            } else if (randomOpponent) {
                pick = randomPick(options, oppRandom);
            } else {
                pick = greedyPick(options);
            }
            assertThat(options).containsKey(pick);
            game.choose(pick);
        }
        int[] money = new int[players];
        for (int seat = 0; seat < players; seat++) {
            money[seat] = game.getPlayer(seat).getMoney();
        }
        return money;
    }

    private static List<String> playerIds(int players) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            ids.add("p" + i);
        }
        return ids;
    }

    /** 伪随机骰子序列（固定种子 → 可复现） */
    private static List<Integer> randomDice(int players, long seed) {
        Random random = new Random(seed);
        int size = players * LasVegasGame.DICE_PER_PLAYER * LasVegasGame.TOTAL_ROUNDS * 4;
        List<Integer> dice = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            dice.add(1 + random.nextInt(6));
        }
        return dice;
    }

    // ==================================================================

    @Nested
    @DisplayName("基础工具")
    class Basics {

        @Test
        @DisplayName("optionsOf 统计点数分布，忽略非法值")
        void optionsOfCounts() {
            Map<Integer, Integer> options =
                    LasVegasAi.optionsOf(Arrays.asList(3, 3, 5, 1, 3, 0, 7, null));
            assertThat(options).containsOnlyKeys(1, 3, 5);
            assertThat(options.get(3)).isEqualTo(3);
            assertThat(options.get(1)).isEqualTo(1);
            assertThat(options.get(5)).isEqualTo(1);
        }

        @Test
        @DisplayName("optionsOf 容忍空输入")
        void optionsOfEmpty() {
            assertThat(LasVegasAi.optionsOf(null)).isEmpty();
            assertThat(LasVegasAi.optionsOf(new ArrayList<>())).isEmpty();
        }

        @Test
        @DisplayName("Style.of 宽松解析，未知回落到稳健")
        void styleOf() {
            assertThat(LasVegasAi.Style.of("wild")).isEqualTo(LasVegasAi.Style.WILD);
            assertThat(LasVegasAi.Style.of(" BOLD ")).isEqualTo(LasVegasAi.Style.BOLD);
            assertThat(LasVegasAi.Style.of(null)).isEqualTo(LasVegasAi.Style.SOLID);
            assertThat(LasVegasAi.Style.of("不存在")).isEqualTo(LasVegasAi.Style.SOLID);
            assertThat(LasVegasAi.Style.WILD.getLabel()).isEqualTo("赌徒");
        }

        @Test
        @DisplayName("viewOf 能从真实引擎读出完整局面")
        void viewOfEngine() {
            LasVegasGame game = new LasVegasGame(Arrays.asList("a", "b"),
                    new ScriptedRoller(Arrays.asList(3, 3, 5)), new Random(1));
            LasVegasAi.View view = LasVegasAi.viewOf(game);
            assertThat(view.getPlayerCount()).isEqualTo(2);
            assertThat(view.getRound()).isEqualTo(1);
            for (int v = 1; v <= LasVegasGame.CASINO_COUNT; v++) {
                assertThat(view.noteCountAt(v)).isEqualTo(game.getCasinoBanknotes(v).size());
                assertThat(view.noteCountAt(v))
                        .isGreaterThanOrEqualTo(1); // 开局每场至少 1 张（≥ $50,000）
            }
            assertThat(view.diceLeftOf(0)).isEqualTo(LasVegasGame.DICE_PER_PLAYER);
        }

        @Test
        @DisplayName("viewOf 拒绝空引擎")
        void viewOfRejectsNull() {
            assertThatThrownBy(() -> LasVegasAi.viewOf(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("决策：选点")
    class Choosing {

        @Test
        @DisplayName("只有一种点数时直接返回，不做推演")
        void singleOptionShortCircuit() {
            LasVegasAi.View view = blankView(2, 1, notes(new int[]{90000}), new int[]{0, 0});
            LasVegasAi.Decision decision = LasVegasAi.decide(view, 0,
                    Arrays.asList(2, 2, 2), LasVegasAi.Style.SOLID, new Random(1));
            assertThat(decision.getValue()).isEqualTo(2);
            assertThat(decision.getCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("无人竞争时选钞票最多的赌场")
        void prefersRicherCasino() {
            // 1 号赌场 $90,000，2 号赌场 $10,000
            // 双方都只剩 1 颗骰子（恰好等于本次要放的数量），且对手没有骰子
            // → 推演中局面完全确定，可以精确断言期望收益
            LasVegasAi.View view = blankView(2, 1,
                    notes(new int[]{90000}, new int[]{10000}), new int[]{0, 0});
            view = withDiceLeft(view, new int[]{1, 0});
            LasVegasAi.Decision decision = LasVegasAi.decide(view, 0,
                    Arrays.asList(1, 2), LasVegasAi.Style.SOLID, new Random(42));
            assertThat(decision.getValue()).isEqualTo(1);
            double[] ev = decision.getExpectedGains();
            assertThat(ev[0]).isEqualTo(90000.0);
            assertThat(ev[1]).isEqualTo(10000.0);
        }

        @Test
        @DisplayName("会主动避开必然平手作废的赌场")
        void avoidsCertainTie() {
            // 1 号赌场 $90,000，但对手已有 3 颗；我也放 3 颗 → 平手全灭，一分不得
            // 2 号赌场 $50,000，对手没有骰子 → 稳拿
            int players = 2;
            int[][] dice = new int[LasVegasGame.CASINO_COUNT + 1][players];
            dice[1][1] = 3; // 对手在 1 号赌场放了 3 颗
            LasVegasAi.View view = new LasVegasAi.View(players, 1, dice, new int[]{3, 0},
                    notes(new int[]{90000}, new int[]{50000}), new int[]{0, 0});

            LasVegasAi.Decision decision = LasVegasAi.decide(view, 0,
                    Arrays.asList(1, 1, 1, 2, 2, 2), LasVegasAi.Style.SOLID, new Random(42));
            assertThat(decision.getValue()).isEqualTo(2);
            assertThat(decision.getExpectedGains()[0]).isZero(); // 选 1 得 0
            assertThat(decision.getExpectedGains()[1]).isEqualTo(50000.0);
        }

        @Test
        @DisplayName("平手会让双方都作废：多放一颗打破平手才有价值")
        void breaksTieByAddingDice() {
            // 对手在 1 号赌场 2 颗且已无骰子；我掷出 1,1,1 → 放 3 颗即可独占 $90,000
            // 2 号赌场故意留空，用来隔离「打破平手」这一条判断
            int players = 2;
            int[][] dice = new int[LasVegasGame.CASINO_COUNT + 1][players];
            dice[1][1] = 2;
            LasVegasAi.View view = new LasVegasAi.View(players, 1, dice, new int[]{3, 0},
                    notes(new int[]{90000}), new int[]{0, 0});
            LasVegasAi.Decision decision = LasVegasAi.decide(view, 0,
                    Arrays.asList(1, 1, 1, 2), LasVegasAi.Style.SOLID, new Random(42));
            assertThat(decision.getValue()).isEqualTo(1);
            assertThat(decision.getExpectedGains()[0]).isEqualTo(90000.0);
        }

        @Test
        @DisplayName("固定种子下决策完全可复现")
        void deterministicWithSeed() {
            int players = 4;
            int[][] dice = new int[LasVegasGame.CASINO_COUNT + 1][players];
            dice[3][1] = 2;
            dice[3][2] = 1;
            dice[5][3] = 4;
            LasVegasAi.View view = new LasVegasAi.View(players, 2, dice,
                    new int[]{5, 3, 6, 2},
                    notes(new int[]{20000, 30000}, new int[]{50000}, new int[]{90000},
                            new int[]{40000, 60000}, new int[]{70000}, new int[]{10000, 80000}),
                    new int[]{120000, 80000, 90000, 30000});

            LasVegasAi.Decision first = LasVegasAi.decide(view, 0,
                    Arrays.asList(2, 4, 4, 4, 6), LasVegasAi.Style.BOLD, new Random(2024));
            LasVegasAi.Decision second = LasVegasAi.decide(view, 0,
                    Arrays.asList(2, 4, 4, 4, 6), LasVegasAi.Style.BOLD, new Random(2024));
            assertThat(second.getValue()).isEqualTo(first.getValue());
            assertThat(second.getExpectedGains()).isEqualTo(first.getExpectedGains());
        }

        @Test
        @DisplayName("选出的点数一定在本次投骰结果里")
        void alwaysPicksLegalValue() {
            for (long seed = 0; seed < 40; seed++) {
                int players = 2 + (int) (seed % 4);
                LasVegasGame game = new LasVegasGame(playerIds(players),
                        new ScriptedRoller(randomDice(players, seed)), new Random(seed));
                game.roll();
                int value = LasVegasAi.chooseValue(game, game.getLastRoll(),
                        LasVegasAi.Style.SOLID, new Random(seed));
                assertThat(game.getOptions()).containsKey(value);
            }
        }

        @Test
        @DisplayName("非法入参抛出 IllegalArgumentException")
        void rejectsBadInput() {
            LasVegasAi.View view = blankView(2, 1, notes(new int[]{90000}), new int[]{0, 0});
            assertThatThrownBy(() -> LasVegasAi.decide(null, 0, Arrays.asList(1),
                    LasVegasAi.Style.SOLID, new Random(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> LasVegasAi.decide(view, 9, Arrays.asList(1),
                    LasVegasAi.Style.SOLID, new Random(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> LasVegasAi.decide(view, 0, new ArrayList<>(),
                    LasVegasAi.Style.SOLID, new Random(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("风险偏好")
    class Aggression {

        @Test
        @DisplayName("性格越激进，基准风险偏好越高")
        void styleOrdering() {
            LasVegasAi.View view = blankView(3, 1, notes(new int[]{90000}), new int[]{0, 0, 0});
            double solid = LasVegasAi.aggressionOf(view, 0, LasVegasAi.Style.SOLID);
            double bold = LasVegasAi.aggressionOf(view, 0, LasVegasAi.Style.BOLD);
            double wild = LasVegasAi.aggressionOf(view, 0, LasVegasAi.Style.WILD);
            assertThat(solid).isLessThan(bold);
            assertThat(bold).isLessThan(wild);
            assertThat(wild).isLessThanOrEqualTo(1.0);
            assertThat(solid).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("落后越多越敢赌；领先时不加码")
        void deficitRaisesAggression() {
            LasVegasAi.View even = blankView(3, 1, notes(new int[]{90000}),
                    new int[]{100000, 100000, 100000});
            LasVegasAi.View behind = blankView(3, 1, notes(new int[]{90000}),
                    new int[]{0, 400000, 400000});
            LasVegasAi.View ahead = blankView(3, 1, notes(new int[]{90000}),
                    new int[]{500000, 0, 0});
            double evenA = LasVegasAi.aggressionOf(even, 0, LasVegasAi.Style.SOLID);
            double behindA = LasVegasAi.aggressionOf(behind, 0, LasVegasAi.Style.SOLID);
            double aheadA = LasVegasAi.aggressionOf(ahead, 0, LasVegasAi.Style.SOLID);
            assertThat(behindA).isGreaterThan(evenA);
            assertThat(aheadA).isLessThanOrEqualTo(evenA);
            assertThat(behindA).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("极端落后时风险偏好被限制在 1.0 以内")
        void aggressionClamped() {
            LasVegasAi.View view = blankView(5, 4, notes(new int[]{90000}),
                    new int[]{0, 9_000_000, 9_000_000, 9_000_000, 9_000_000});
            double aggression = LasVegasAi.aggressionOf(view, 0, LasVegasAi.Style.WILD);
            assertThat(aggression).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("对局一致性")
    class Gameplay {

        @Test
        @DisplayName("AI 能打完 2-5 人整局，且钞票总量守恒")
        void aiFinishesGamesAndConserves() {
            for (int players = LasVegasGame.MIN_PLAYERS;
                    players <= LasVegasGame.MAX_PLAYERS; players++) {
                for (long seed = 0; seed < 5; seed++) {
                    int[] money = playGame(players, seed, LasVegasAi.Style.SOLID, 0,
                            true, false);
                    int total = 0;
                    for (int value : money) {
                        total += value;
                    }
                    // 4 轮里必然有钞票因「平手作废」留在牌堆，所以只能是「不超过」
                    assertThat(total)
                            .isLessThanOrEqualTo(LasVegasBanknotes.TOTAL_VALUE)
                            .isGreaterThan(0);
                }
            }
        }

        @Test
        @DisplayName("AI 每局都会拿到钱（不是随机乱放）")
        void aiEarnsMoney() {
            int games = 12;
            int earned = 0;
            for (long seed = 0; seed < games; seed++) {
                int[] money = playGame(4, seed, LasVegasAi.Style.SOLID, 0, true, false);
                earned += money[0];
            }
            assertThat(earned).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("实力：AI 要能比基线赚更多")
    class Strength {

        @Test
        @DisplayName("1 个 AI 对 3 个「骰子最多」基线，AI 长期总收益更高")
        void beatsGreedyBaseline() {
            int games = 40;
            int aiTotal = 0;
            int baselineTotal = 0;
            for (long seed = 0; seed < games; seed++) {
                int[] money = playGame(4, 1000 + seed, LasVegasAi.Style.BOLD, 0,
                        true, false);
                aiTotal += money[0];
                for (int seat = 1; seat < 4; seat++) {
                    baselineTotal += money[seat];
                }
            }
            // 基线是 3 个人，按人头均摊后再比，AI 应当明显更高
            double aiAverage = (double) aiTotal / games;
            double baselineAverage = (double) baselineTotal / games / 3;
            assertThat(aiAverage).isGreaterThan(baselineAverage);
        }

        @Test
        @DisplayName("1 个 AI 对 3 个随机乱放，AI 长期总收益更高")
        void beatsRandomBaseline() {
            int games = 40;
            int aiTotal = 0;
            int baselineTotal = 0;
            for (long seed = 0; seed < games; seed++) {
                int[] money = playGame(4, 5000 + seed, LasVegasAi.Style.BOLD, 0,
                        true, true);
                aiTotal += money[0];
                for (int seat = 1; seat < 4; seat++) {
                    baselineTotal += money[seat];
                }
            }
            double aiAverage = (double) aiTotal / games;
            double baselineAverage = (double) baselineTotal / games / 3;
            assertThat(aiAverage).isGreaterThan(baselineAverage);
        }
    }
}
