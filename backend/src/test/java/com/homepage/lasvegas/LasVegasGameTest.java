package com.homepage.lasvegas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 游戏流程测试。
 *
 * 使用脚本化骰子（{@link ScriptedRoller}）以获得确定性结果；
 * 期望值取自官方规则书，而非被测代码。
 */
class LasVegasGameTest {

    /** 按预设序列依次返回点数，用尽后循环 */
    private static class ScriptedRoller implements LasVegasGame.DiceRoller {
        private final Deque<Integer> queue;
        private final List<Integer> script;

        ScriptedRoller(List<Integer> script) {
            this.script = script;
            this.queue = new ArrayDeque<>(script);
        }

        @Override
        public int roll() {
            if (queue.isEmpty()) {
                queue.addAll(script);
            }
            return queue.pollFirst();
        }
    }

    /** 固定返回同一点数 */
    private static LasVegasGame.DiceRoller always(int value) {
        return () -> value;
    }

    private static List<String> players(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add("player" + i);
        }
        return ids;
    }

    /** 一直以「选最大点数组」的策略把一局打完 */
    private static void playToEnd(LasVegasGame game) {
        int guard = 0;
        while (!game.isOver()) {
            if (++guard > 10_000) {
                throw new IllegalStateException("对局未能结束，疑似死循环");
            }
            if (!game.hasRolled()) {
                game.roll();
            }
            Map<Integer, Integer> options = game.getOptions();
            int value = options.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElseThrow()
                    .getKey();
            game.choose(value);
        }
    }

    /**
     * 场上可见的总金额：玩家现金 + 赌场钞票 + 牌堆。
     *
     * 含房规后它等于 {@code $2,500,000 + 补贴 − 跳过罚金}，见
     * {@link #assertMoneyConserved}。
     */
    private static int totalMoneyInPlay(LasVegasGame game) {
        int sum = 0;
        for (LasVegasGame.Player p : game.getPlayers()) {
            sum += p.getMoney();
        }
        for (int v = 1; v <= LasVegasGame.CASINO_COUNT; v++) {
            sum += LasVegasBanknotes.totalValue(game.getCasinoBanknotes(v));
        }
        sum += game.getDeckTotalValue();
        return sum;
    }

    /**
     * 房规下的守恒断言：场上的钱 = 官方钞票总额 + 净注入金额。
     *
     * 跳过罚金是「销毁」掉的（不转给任何人），所以净注入 = 补贴 − 罚金，
     * 这正是 {@link LasVegasGame#getInjectedMoney()} 的语义。
     */
    private static void assertMoneyConserved(LasVegasGame game, String message) {
        assertThat(totalMoneyInPlay(game))
                .as(message)
                .isEqualTo(LasVegasBanknotes.TOTAL_VALUE + game.getInjectedMoney());
    }

    @Nested
    @DisplayName("开局设置")
    class Setup {

        @Test
        @DisplayName("支持 2-5 人")
        void supportsTwoToFivePlayers() {
            for (int n = LasVegasGame.MIN_PLAYERS; n <= LasVegasGame.MAX_PLAYERS; n++) {
                LasVegasGame game = new LasVegasGame(players(n), always(1), new Random(1));
                assertThat(game.getPlayerCount()).isEqualTo(n);
            }
        }

        @Test
        @DisplayName("少于 2 人或超过 5 人时拒绝开局")
        void rejectsInvalidPlayerCount() {
            assertThatThrownBy(() -> new LasVegasGame(players(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LasVegasGame(players(6)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("每人 8 颗骰子")
        void eachPlayerHasEightDice() {
            LasVegasGame game = new LasVegasGame(players(3), always(1), new Random(1));

            assertThat(game.getPlayers())
                    .allSatisfy(p -> assertThat(p.getDiceLeft())
                            .isEqualTo(LasVegasGame.DICE_PER_PLAYER));
        }

        @Test
        @DisplayName("6 个赌场，每场起始金额至少 $50,000")
        void everyCasinoHasAtLeastFiftyThousand() {
            LasVegasGame game = new LasVegasGame(players(4), always(1), new Random(3));

            for (int v = 1; v <= LasVegasGame.CASINO_COUNT; v++) {
                int total = LasVegasBanknotes.totalValue(game.getCasinoBanknotes(v));
                assertThat(total)
                        .as("赌场 %d 的起始金额", v)
                        .isGreaterThanOrEqualTo(LasVegasGame.MIN_CASINO_VALUE);
            }
        }

        @Test
        @DisplayName("开局轮次为 1，状态为放置中")
        void startsAtRoundOne() {
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));

            assertThat(game.getRound()).isEqualTo(1);
            assertThat(game.getPhase()).isEqualTo(LasVegasGame.Phase.PLACING);
            assertThat(game.isOver()).isFalse();
        }
    }

    @Nested
    @DisplayName("投骰与放置")
    class RollingAndPlacing {

        @Test
        @DisplayName("掷出与剩余骰数相同数量的骰子，结果已排序")
        void rollsAllRemainingDice() {
            LasVegasGame game = new LasVegasGame(players(2),
                    new ScriptedRoller(Arrays.asList(5, 3, 1, 3, 6, 2, 4, 3)), new Random(1));

            List<Integer> roll = game.roll();

            assertThat(roll).hasSize(8).containsExactly(1, 2, 3, 3, 3, 4, 5, 6);
            assertThat(game.getOptions()).containsOnlyKeys(1, 2, 3, 4, 5, 6);
            assertThat(game.getOptions().get(3)).isEqualTo(3);
        }

        @Test
        @DisplayName("必须放置该点数的全部骰子（不可只放一部分）")
        void placesAllDiceOfChosenValue() {
            LasVegasGame game = new LasVegasGame(players(2),
                    new ScriptedRoller(Arrays.asList(5, 3, 1, 3, 6, 2, 4, 3)), new Random(1));
            game.roll();

            game.choose(3);

            assertThat(game.getCasinoDice(3)).containsEntry(0, 3);
            assertThat(game.getPlayer(0).getDiceLeft()).isEqualTo(5);
        }

        @Test
        @DisplayName("不能选择本次未掷出的点数")
        void rejectsValueNotRolled() {
            LasVegasGame game = new LasVegasGame(players(2),
                    new ScriptedRoller(Arrays.asList(1, 1, 1, 1, 1, 1, 1, 1)), new Random(1));
            game.roll();

            assertThatThrownBy(() -> game.choose(4))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("未投骰不能放置")
        void rejectsChooseBeforeRoll() {
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));

            assertThatThrownBy(() -> game.choose(1))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("同一回合不能连续投骰两次")
        void rejectsDoubleRoll() {
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));
            game.roll();

            assertThatThrownBy(game::roll).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("放置后轮到下一位玩家")
        void advancesToNextPlayer() {
            LasVegasGame game = new LasVegasGame(players(3), always(1), new Random(1));

            assertThat(game.getCurrentPlayerIndex()).isZero();
            game.roll();
            game.choose(1);
            assertThat(game.getCurrentPlayerIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("点数必须在 1-6 之间")
        void rejectsOutOfRangeValue() {
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));
            game.roll();

            assertThatThrownBy(() -> game.choose(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> game.choose(7)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("轮次与结算")
    class RoundsAndPayout {

        @Test
        @DisplayName("每人 8 颗全放完后本场结算，同点数平手全部作废")
        void tiedCasinoPaysNobody() {
            // 两人各把 8 颗骰子放到赌场 1 → 8 对 8，平手作废
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));
            game.roll();
            game.choose(1);
            game.roll();
            game.choose(1);

            assertThat(game.getRound()).isEqualTo(2); // 已进入第 2 轮
            // 赌场一分没发出去，所以两人手里的钱就是第 1、2 两轮的补贴
            assertThat(game.getPlayers()).allSatisfy(p ->
                    assertThat(p.getMoney()).isEqualTo(LasVegasGame.ROUND_SUBSIDY * 2));
            assertThat(game.getLastPayout()).hasSize(6);
            assertThat(game.getLastPayout().get(0).getVoidedSeats()).containsExactly(0, 1);
        }

        @Test
        @DisplayName("独占领先后获得该场最高面额钞票，并计 1 张")
        void soleLeaderTakesHighestNote() {
            // 玩家0 全放赌场1；玩家1 全放赌场2
            LasVegasGame game = new LasVegasGame(players(2),
                    new ScriptedRoller(scriptedRounds(1, 2)), new Random(5));
            game.roll();
            game.choose(1);
            game.roll();
            game.choose(2);

            assertThat(game.getPlayer(0).getBanknoteCount()).isEqualTo(1);
            assertThat(game.getPlayer(1).getBanknoteCount()).isEqualTo(1);
            // 除钞票收益外还有 2 轮补贴
            assertThat(game.getPlayer(0).getMoney())
                    .isGreaterThan(LasVegasGame.ROUND_SUBSIDY * 2);
            assertThat(game.getPlayer(1).getMoney())
                    .isGreaterThan(LasVegasGame.ROUND_SUBSIDY * 2);
        }

        @Test
        @DisplayName("每轮结束后起始玩家左移一位")
        void startPlayerRotatesLeft() {
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));
            assertThat(game.getStartPlayerIndex()).isZero();

            game.roll();
            game.choose(1);
            game.roll();
            game.choose(1);

            assertThat(game.getRound()).isEqualTo(2);
            assertThat(game.getStartPlayerIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("恰好 4 轮后游戏结束")
        void endsAfterExactlyFourRounds() {
            LasVegasGame game = new LasVegasGame(players(3), always(1), new Random(1));
            playToEnd(game);

            assertThat(game.getRound()).isEqualTo(LasVegasGame.TOTAL_ROUNDS);
            assertThat(game.isOver()).isTrue();
            assertThat(game.getPhase()).isEqualTo(LasVegasGame.Phase.GAME_OVER);
        }

        @Test
        @DisplayName("游戏结束后不能再行动")
        void noActionsAfterGameOver() {
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));
            playToEnd(game);

            assertThatThrownBy(game::roll).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> game.choose(1)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("2-5 人局都能正常跑完 4 轮")
        void completesForEveryPlayerCount() {
            for (int n = LasVegasGame.MIN_PLAYERS; n <= LasVegasGame.MAX_PLAYERS; n++) {
                LasVegasGame game = new LasVegasGame(players(n), always(1), new Random(n));
                playToEnd(game);

                assertThat(game.isOver()).as("%d 人局应当结束", n).isTrue();
                assertThat(game.getRound()).isEqualTo(LasVegasGame.TOTAL_ROUNDS);
            }
        }
    }

    @Nested
    @DisplayName("金额守恒")
    class Conservation {

        @Test
        @DisplayName("整局过程中「钞票 + 净注入」恒等于场上总额")
        void moneyIsConservedThroughoutGame() {
            LasVegasGame game = new LasVegasGame(players(4),
                    new ScriptedRoller(scriptedRounds(1, 2, 3, 4)), new Random(11));

            assertMoneyConserved(game, "开局");

            int guard = 0;
            while (!game.isOver()) {
                if (++guard > 10_000) {
                    throw new IllegalStateException("对局未能结束");
                }
                if (!game.hasRolled()) {
                    game.roll();
                }
                Map<Integer, Integer> options = game.getOptions();
                int value = options.keySet().iterator().next();
                game.choose(value);

                assertMoneyConserved(game,
                        String.format("第 %d 轮、第 %d 步后", game.getRound(), guard));
            }
        }

        @Test
        @DisplayName("跳过会真的扣钱，且罚金从场上消失（净注入 = 补贴 − 罚金）")
        void skippingDestroysMoney() {
            LasVegasGame game = new LasVegasGame(players(2),
                    new ScriptedRoller(scriptedRounds(1, 2)), new Random(7));

            int afterGrant = game.getPlayer(0).getMoney();
            assertThat(afterGrant).isEqualTo(LasVegasGame.ROUND_SUBSIDY);
            assertMoneyConserved(game, "第 1 轮开局");

            // 玩家0 掷完就跳过：骰子保留、扣 1 万、轮到玩家1
            game.roll();
            int diceBefore = game.getPlayer(0).getDiceLeft();
            game.skip();

            assertThat(game.getPlayer(0).getDiceLeft()).as("跳过不消耗骰子").isEqualTo(diceBefore);
            assertThat(game.getPlayer(0).getMoney())
                    .isEqualTo(LasVegasGame.ROUND_SUBSIDY - LasVegasGame.SKIP_COST);
            assertThat(game.getPlayer(0).getSkipsLeft())
                    .isEqualTo(LasVegasGame.SKIPS_PER_ROUND - 1);
            assertThat(game.getTotalSkipCost()).isEqualTo(LasVegasGame.SKIP_COST);
            assertThat(game.getInjectedMoney())
                    .isEqualTo(game.getTotalSubsidy() - game.getTotalSkipCost());
            assertThat(game.getCurrentPlayerIndex()).as("轮到下家").isEqualTo(1);
            assertThat(game.hasRolled()).as("新玩家的投骰状态已清空").isFalse();
            // 罚金不转给任何人：场上总额比补贴少了这 1 万
            assertMoneyConserved(game, "跳过一次后");
        }

        @Test
        @DisplayName("每人每轮最多跳过 2 次，第 3 次被拒绝")
        void skipIsCappedPerRound() {
            LasVegasGame game = new LasVegasGame(players(2),
                    new ScriptedRoller(scriptedRounds(1, 1)), new Random(3));

            // 让玩家0 连续跳过两次（中间玩家1 每次都直接放骰子）
            game.roll();
            game.skip();
            assertThat(game.getPlayer(0).getSkipsLeft()).isEqualTo(1);
            // 玩家1 出一手，把行动权交回来
            game.roll();
            game.choose(1);
            assertThat(game.getCurrentPlayerIndex()).isEqualTo(0);

            game.roll();
            game.skip();
            assertThat(game.getPlayer(0).getSkipsLeft()).isZero();
            assertThat(game.getPlayer(0).getMoney())
                    .isEqualTo(LasVegasGame.ROUND_SUBSIDY - 2 * LasVegasGame.SKIP_COST);
            // 玩家1 的 8 颗骰子已全部放进赌场，所以跳过之后直接又轮到玩家0
            assertThat(game.getCurrentPlayerIndex()).isEqualTo(0);
            game.roll();

            assertThat(game.canSkip()).isFalse();
            assertThatThrownBy(game::skip)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("跳过次数已用完");
            assertMoneyConserved(game, "用满跳过次数后");
        }

        @Test
        @DisplayName("新一轮会把跳过次数重置为 2")
        void skipsResetEachRound() {
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));

            // 第 1 轮：两人各放 8 颗 → 平手作废 → 进入第 2 轮
            game.roll();
            game.choose(1);
            game.roll();
            game.choose(1);

            assertThat(game.getRound()).isEqualTo(2);
            assertThat(game.getPlayers()).allSatisfy(p ->
                    assertThat(p.getSkipsLeft()).isEqualTo(LasVegasGame.SKIPS_PER_ROUND));
        }

        @Test
        @DisplayName("没投骰时不能跳过")
        void cannotSkipBeforeRolling() {
            LasVegasGame game = new LasVegasGame(players(2), always(1), new Random(1));

            assertThat(game.canSkip()).isFalse();
            assertThatThrownBy(game::skip)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("请先投骰");
        }

        @Test
        @DisplayName("随机对局 200 局均满足守恒与骰子数守恒")
        void randomGamesStayConsistent() {
            for (int seed = 0; seed < 200; seed++) {
                int playerCount = 2 + (seed % 4);
                Random diceRandom = new Random(seed * 31L + 7);
                LasVegasGame game = new LasVegasGame(players(playerCount),
                        () -> diceRandom.nextInt(6) + 1, new Random(seed));
                playToEnd(game);

                assertMoneyConserved(game, String.format("seed=%d", seed));

                // 每轮最多在 6 个赌场各得 1 张钞票 → 4 轮至多 24 张
                assertThat(game.getPlayers()).allSatisfy(p ->
                        assertThat(p.getBanknoteCount())
                                .isBetween(0, LasVegasGame.TOTAL_ROUNDS * LasVegasGame.CASINO_COUNT));

                assertThat(game.getWinnerSeats()).isNotEmpty();
            }
        }
    }

    /** 生成「玩家0 恒投 a、玩家1 恒投 b …」的循环脚本 */
    private static List<Integer> scriptedRounds(int... valuesPerPlayer) {
        List<Integer> script = new ArrayList<>();
        for (int round = 0; round < LasVegasGame.TOTAL_ROUNDS + 1; round++) {
            for (int value : valuesPerPlayer) {
                for (int i = 0; i < LasVegasGame.DICE_PER_PLAYER; i++) {
                    script.add(value);
                }
            }
        }
        return script;
    }
}
