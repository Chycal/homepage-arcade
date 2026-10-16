package com.homepage.lasvegas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 《拉斯维加斯》AI 对手的「投资逻辑」。
 *
 * 需求来源：房主可以在大厅添加 AI 对手；AI 的目标是「赚更多的钱」；
 * 允许 AI 自行判断并尝试一些高风险操作。
 *
 * 与 {@link LasVegasGame} 一样是纯逻辑：不依赖 Spring / WebSocket / 数据库，可独立单元测试；
 * 风格与五子棋的 {@code GomokuAI} 一致（静态工具类 + 可注入随机源）。
 *
 * <h2>为什么不能只看自己的骰子数</h2>
 * 官方结算规则里「骰子数相同的玩家全部作废」，所以「我在某赌场放了 4 颗」并不代表能拿钱：
 * 只要还有别人最终也是 4 颗，两个人都颗粒无收。收益取决于 <em>最终</em> 的相对排名，
 * 而对手后续还会继续放骰子，因此必须对「对手接下来会怎么放」做估计。
 *
 * <h2>决策模型：蒙特卡洛推演 + 风险偏好</h2>
 * <ol>
 *   <li>本次掷出的每个点数都是一个候选（选了就要把该点数的<b>全部</b>骰子放进去）。</li>
 *   <li>对每个候选做 {@value #DEFAULT_ROLLOUTS} 次随机推演：AI 先落子，
 *       然后按「对手模型」把本轮剩余的所有行动模拟完。</li>
 *   <li>用官方结算规则（直接复用 {@link LasVegasPayout#settle}，保证与真实对局完全一致）
 *       算出这一轮 AI 能拿到多少钱。</li>
 *   <li>统计期望收益 {@code EV} 与高水位分位 {@code q}，
 *       最终打分 {@code score = EV + aggression × (q − EV)}。</li>
 * </ol>
 *
 * <h2>高风险操作怎么来的</h2>
 * {@code aggression}（0-1）就是风险偏好：
 * <ul>
 *   <li>只看 {@code EV}（aggression=0）→ 稳健，选平均收益最高的。</li>
 *   <li>把 {@code q}（前 10% 好情况下的收益）也算进来 → 愿意为了「赢得多」接受「经常空手」。</li>
 * </ul>
 * 它由两部分决定：AI 的固有性格（{@link Style}）+ <b>当前的落后程度</b>。
 * 落后越多、剩余轮次越少，aggression 越高——领先时求稳，快输了就梭哈，
 * 这正是「判断之后再去赌」而不是无脑乱赌。
 *
 * <h2>对手模型</h2>
 * 真实对手是人，行为不可知，因此每次推演从三种模型里随机抽一种，保证 AI 不依赖某个特定假设：
 * <ul>
 *   <li>「理性型」（80%）：按一手评估（收益 × 被追平风险折扣）选点。</li>
 *   <li>「莽夫型」（10%）：只看骰子数，喜欢往人多的地方堆。</li>
 *   <li>「新手型」（10%）：在可选点数里随机乱放。</li>
 * </ul>
 */
public final class LasVegasAi {

    /** 每个候选点数的推演次数（越大越准、越慢） */
    public static final int DEFAULT_ROLLOUTS = 100;

    /** 高水位分位，用于衡量「高上限」 */
    public static final double HIGH_QUANTILE = 0.90;

    /** 赌场数量（与引擎保持一致） */
    private static final int CASINOS = LasVegasGame.CASINO_COUNT;

    private LasVegasAi() {
    }

    // ==================================================================
    // 风险人格
    // ==================================================================

    /** AI 性格。名字会显示在大厅里，让玩家知道对手是什么路数。 */
    public enum Style {

        /** 稳健：只看期望收益，不冒险 */
        SOLID("稳健"),

        /** 激进：期望相近时偏向高上限的打法 */
        BOLD("激进"),

        /** 赌徒：落后时敢把一大把骰子压在拥挤的高价值赌场 */
        WILD("赌徒");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        /** 中文名，用于前端展示 */
        public String getLabel() {
            return label;
        }

        /** 宽松解析，无法识别时回落到 {@link #SOLID} */
        public static Style of(String name) {
            if (name == null) {
                return SOLID;
            }
            String key = name.trim().toUpperCase();
            for (Style style : values()) {
                if (style.name().equals(key)) {
                    return style;
                }
            }
            return SOLID;
        }
    }

    // ==================================================================
    // 状态快照
    // ==================================================================

    /**
     * 决策所需的只读状态快照。
     *
     * 之所以不直接吃 {@link LasVegasGame}，是为了让单元测试可以手工构造任意局面，
     * 而不必依赖「掷出什么点数」这种随机过程。
     */
    public static final class View {

        private final int playerCount;
        private final int round;
        /** [7][playerCount] 各赌场已放置的骰子数 */
        private final int[][] dice;
        /** [playerCount] 各玩家本轮剩余骰子 */
        private final int[] diceLeft;
        /** [7][] 各赌场钞票，降序（对手模型按名次直接取用） */
        private final int[][] notesDesc;
        /** [7] 各赌场钞票张数 */
        private final int[] noteCount;
        /** [7] 各赌场钞票，供 {@link LasVegasPayout#settle} 使用 */
        private final List<Integer>[] notesList;
        /** [playerCount] 各玩家累计奖金 */
        private final int[] money;

        /**
         * @param playerCount 玩家数（含 AI）
         * @param round       当前轮次 1-4
         * @param dice        [7][playerCount] 各赌场已放置的骰子数（索引 0 不使用）
         * @param diceLeft    [playerCount] 各玩家剩余骰子
         * @param notes       [7][] 各赌场钞票面额
         * @param money       [playerCount] 各玩家累计奖金
         */
        @SuppressWarnings("unchecked")
        public View(int playerCount, int round, int[][] dice, int[] diceLeft,
                    int[][] notes, int[] money) {
            if (playerCount < 1) {
                throw new IllegalArgumentException("玩家数必须为正");
            }
            this.playerCount = playerCount;
            this.round = round;
            this.dice = new int[CASINOS + 1][playerCount];
            for (int v = 1; v <= CASINOS; v++) {
                if (dice[v] != null) {
                    System.arraycopy(dice[v], 0, this.dice[v], 0, playerCount);
                }
            }
            this.diceLeft = diceLeft == null ? new int[playerCount] : diceLeft.clone();
            this.money = money == null ? new int[playerCount] : money.clone();
            this.notesDesc = new int[CASINOS + 1][];
            this.noteCount = new int[CASINOS + 1];
            this.notesList = new List[CASINOS + 1];
            for (int v = 1; v <= CASINOS; v++) {
                int[] raw = notes == null || notes[v] == null ? new int[0] : notes[v].clone();
                Arrays.sort(raw);
                int len = raw.length;
                this.noteCount[v] = len;
                int[] desc = new int[len];
                for (int i = 0; i < len; i++) {
                    desc[i] = raw[len - 1 - i];
                }
                this.notesDesc[v] = desc;
                List<Integer> list = new ArrayList<>(len);
                for (int note : desc) {
                    list.add(note);
                }
                this.notesList[v] = Collections.unmodifiableList(list);
            }
        }

        public int getPlayerCount() {
            return playerCount;
        }

        public int getRound() {
            return round;
        }

        public int diceAt(int dieValue, int seat) {
            return dice[dieValue][seat];
        }

        public int diceLeftOf(int seat) {
            return diceLeft[seat];
        }

        public int moneyOf(int seat) {
            return money[seat];
        }

        public int noteCountAt(int dieValue) {
            return noteCount[dieValue];
        }

        /** 第 index 张钞票面额（已按降序排列） */
        public int noteAt(int dieValue, int index) {
            return notesDesc[dieValue][index];
        }
    }

    /** 从真实引擎局面生成快照 */
    public static View viewOf(LasVegasGame game) {
        if (game == null) {
            throw new IllegalArgumentException("game 不能为空");
        }
        int n = game.getPlayerCount();
        int[][] dice = new int[CASINOS + 1][n];
        int[][] notes = new int[CASINOS + 1][];
        int[] left = new int[n];
        int[] money = new int[n];
        for (int v = 1; v <= CASINOS; v++) {
            for (Map.Entry<Integer, Integer> entry : game.getCasinoDice(v).entrySet()) {
                int seat = entry.getKey();
                if (seat >= 0 && seat < n) {
                    dice[v][seat] = entry.getValue() == null ? 0 : entry.getValue();
                }
            }
            List<Integer> banknotes = game.getCasinoBanknotes(v);
            int[] arr = new int[banknotes.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = banknotes.get(i);
            }
            notes[v] = arr;
        }
        for (int seat = 0; seat < n; seat++) {
            left[seat] = game.getPlayer(seat).getDiceLeft();
            money[seat] = game.getPlayer(seat).getMoney();
        }
        return new View(n, game.getRound(), dice, left, notes, money);
    }

    // ==================================================================
    // 决策结果
    // ==================================================================

    /** 一次决策的完整过程，便于测试与日志 */
    public static final class Decision {

        private final int value;
        private final int count;
        private final int[] candidates;
        private final double[] ev;
        private final double[] upside;
        private final double aggression;

        Decision(int value, int count, int[] candidates, double[] ev, double[] upside,
                 double aggression) {
            this.value = value;
            this.count = count;
            this.candidates = candidates;
            this.ev = ev;
            this.upside = upside;
            this.aggression = aggression;
        }

        /** 选中的点数 */
        public int getValue() {
            return value;
        }

        /** 选中点数对应的骰子数 */
        public int getCount() {
            return count;
        }

        public int[] getCandidates() {
            return candidates.clone();
        }

        /** 各候选的期望收益 */
        public double[] getExpectedGains() {
            return ev.clone();
        }

        /** 各候选的高水位收益（前 10% 好情况） */
        public double[] getUpsideGains() {
            return upside.clone();
        }

        /** 本次决策采用的风险偏好 0-1 */
        public double getAggression() {
            return aggression;
        }
    }

    // ==================================================================
    // 对外入口
    // ==================================================================

    /** 按当前局面替「当前玩家」选出点数（默认稳健型） */
    public static int chooseValue(LasVegasGame game, List<Integer> roll, Random random) {
        return chooseValue(game, roll, Style.SOLID, random);
    }

    /** 按当前局面替「当前玩家」选出点数 */
    public static int chooseValue(LasVegasGame game, List<Integer> roll, Style style,
                                  Random random) {
        if (game == null) {
            throw new IllegalArgumentException("game 不能为空");
        }
        return decide(viewOf(game), game.getCurrentPlayerIndex(), roll, style, random).getValue();
    }

    // ------------------------------------------------------------------
    // 房规：跳过（过牌）决策
    // ------------------------------------------------------------------

    /** 稳健型要求的净收益门槛 */
    private static final double SKIP_REQUIRED_SOLID = 15_000;
    /** 激进型要求的净收益门槛 */
    private static final double SKIP_REQUIRED_BOLD = 5_000;

    /**
     * 房规：判断 AI 掷完骰子后该不该「跳过」本回合。
     *
     * 跳过 = 不放骰子、骰子全部保留、扣 {@link LasVegasGame#SKIP_COST}，下轮可以重掷全部骰子。
     * 所以这本质上是一个「花 1 万买一次重掷」的买卖：只有当这次投骰确实很差、
     * 而重掷的期望明显更高时才划算。
     *
     * 决策本身是确定性的（不掷随机数），保证同一局面永远得到同一答案。
     *
     * @return true 表示建议跳过
     */
    public static boolean shouldSkip(LasVegasGame game, Style style) {
        if (game == null || !game.canSkip()) {
            return false;
        }
        int me = game.getCurrentPlayerIndex();
        return shouldSkip(viewOf(game), me, game.getLastRoll(),
                game.getPlayer(me).getSkipsLeft(), style);
    }

    /**
     * 跳过判定的可测试入口：用两个便宜的一手评估做比较，不跑蒙特卡洛
     * （跳过判定每回合最多触发两次，没必要为它付出 100 次推演的代价）。
     *
     *  - {@code bestNow}：按当前投骰结果能拿到的最好收益。
     *  - {@code fresh}：假设重掷，按「每个点数平均分到 diceLeft/6 颗」估算的最好收益。
     * 两者之差再减去跳过成本就是这次跳过的净收益；性格越激进越容易掏钱赌。
     *
     * @param skipsLeft 当前玩家本轮还剩的跳过次数，已用光则直接返回 false
     */
    public static boolean shouldSkip(View view, int me, List<Integer> roll, int skipsLeft,
                                     Style style) {
        if (view == null) {
            throw new IllegalArgumentException("view 不能为空");
        }
        if (me < 0 || me >= view.getPlayerCount()) {
            throw new IllegalArgumentException("座位号越界: " + me);
        }
        if (skipsLeft <= 0) {
            return false;
        }
        Map<Integer, Integer> options = optionsOf(roll);
        if (options.isEmpty()) {
            return false;
        }

        double bestNow = 0;
        for (Map.Entry<Integer, Integer> option : options.entrySet()) {
            bestNow = Math.max(bestNow, onePlyScore(view.dice, view.diceLeft, view, me,
                    option.getKey(), option.getValue(), false));
        }
        double net = bestFreshRollEstimate(view, me) - bestNow - LasVegasGame.SKIP_COST;

        // 赌徒型不设门槛：只要估算下来是正收益就赌
        double required = style == Style.WILD ? 0
                : style == Style.BOLD ? SKIP_REQUIRED_BOLD : SKIP_REQUIRED_SOLID;
        return net > required;
    }

    /**
     * 估算「重掷一次」能拿到的最好收益：按每个点数平均分到 {@code diceLeft / 6} 颗来算。
     *
     * 这是一个刻意简化的估计，只用来和当前这一手做相对比较，不追求绝对准确。
     */
    private static double bestFreshRollEstimate(View view, int me) {
        int typical = Math.max(1, view.diceLeftOf(me) / LasVegasGame.CASINO_COUNT);
        double best = 0;
        for (int value = 1; value <= LasVegasGame.CASINO_COUNT; value++) {
            best = Math.max(best, onePlyScore(view.dice, view.diceLeft, view, me,
                    value, typical, false));
        }
        return best;
    }

    /** 完整决策过程 */
    public static Decision decide(View view, int me, List<Integer> roll, Style style,
                                  Random random) {
        if (view == null) {
            throw new IllegalArgumentException("view 不能为空");
        }
        if (me < 0 || me >= view.getPlayerCount()) {
            throw new IllegalArgumentException("座位号越界: " + me);
        }
        Map<Integer, Integer> options = optionsOf(roll);
        if (options.isEmpty()) {
            throw new IllegalArgumentException("投骰结果为空，无法选择点数");
        }

        List<Integer> keys = new ArrayList<>(options.keySet());
        Collections.sort(keys);
        // 只有一种选择时无需推演
        if (keys.size() == 1) {
            int only = keys.get(0);
            return new Decision(only, options.get(only), new int[]{only},
                    new double[]{0}, new double[]{0}, 0);
        }

        int rollouts = DEFAULT_ROLLOUTS;
        long baseSeed = random == null ? System.nanoTime() : random.nextLong();

        int[] candidates = new int[keys.size()];
        double[] ev = new double[keys.size()];
        double[] upside = new double[keys.size()];
        double[] gains = new double[rollouts];

        for (int i = 0; i < keys.size(); i++) {
            int value = keys.get(i);
            int count = options.get(value);
            candidates[i] = value;
            for (int k = 0; k < rollouts; k++) {
                // 公共随机数：所有候选共用同一批随机流，比较才公平（大幅降低噪声）
                Random rnd = new Random(baseSeed + k * 0x9E3779B97F4A7C15L);
                gains[k] = simulateGain(view, me, value, count, rnd);
            }
            Arrays.sort(gains);
            ev[i] = mean(gains);
            upside[i] = quantile(gains, HIGH_QUANTILE);
        }

        double aggression = aggressionOf(view, me, style);

        int bestIdx = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < candidates.length; i++) {
            double score = ev[i] + aggression * Math.max(0, upside[i] - ev[i]);
            if (score > bestScore + 1e-9) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return new Decision(candidates[bestIdx], options.get(candidates[bestIdx]),
                candidates, ev, upside, aggression);
    }

    /**
     * 风险偏好 0-1。
     *
     * 固有性格给出基准值；再根据「落后程度 ÷ 剩余可争夺金额」往上加。
     * 领先时不加（求稳），落后越多越敢赌，为翻盘接受高风险。
     */
    static double aggressionOf(View view, int me, Style style) {
        double base;
        switch (style == null ? Style.SOLID : style) {
            case WILD:
                base = 0.55;
                break;
            case BOLD:
                base = 0.30;
                break;
            case SOLID:
            default:
                base = 0.05;
                break;
        }
        int leaderMoney = 0;
        for (int seat = 0; seat < view.getPlayerCount(); seat++) {
            leaderMoney = Math.max(leaderMoney, view.moneyOf(seat));
        }
        int deficit = Math.max(0, leaderMoney - view.moneyOf(me));
        int roundsLeft = Math.max(1, LasVegasGame.TOTAL_ROUNDS - view.getRound() + 1);
        // 剩下几轮里「理论上还能抢到」的钱：总钞票 / 轮数 / 人数
        double winnable = (double) LasVegasBanknotes.TOTAL_VALUE
                / LasVegasGame.TOTAL_ROUNDS / view.getPlayerCount();
        double pressure = Math.min(1.0, deficit / Math.max(1.0, winnable * roundsLeft));
        return clamp(base + 0.65 * pressure, 0.0, 1.0);
    }

    // ==================================================================
    // 蒙特卡洛推演
    // ==================================================================

    /**
     * 推演「我选择 value 点数的 count 颗骰子」之后，本轮结束我能拿到多少钱。
     *
     * 完全按引擎的行动顺序推进（当前玩家之后，顺时针找下一个还有骰子的玩家），
     * 并用官方结算规则算钱。
     */
    static double simulateGain(View view, int me, int value, int count, Random rnd) {
        int n = view.getPlayerCount();
        int[][] dice = new int[CASINOS + 1][];
        for (int v = 1; v <= CASINOS; v++) {
            dice[v] = view.dice[v].clone();
        }
        int[] left = view.diceLeft.clone();

        // 1. 我落子
        dice[value][me] += count;
        left[me] -= count;

        // 2. 模拟本轮剩余的所有行动（含我自己后续的回合）
        int idx = me;
        int guard = n * LasVegasGame.DICE_PER_PLAYER + 8;
        while (guard-- > 0) {
            int next = -1;
            for (int i = 1; i <= n; i++) {
                int seat = (idx + i) % n;
                if (left[seat] > 0) {
                    next = seat;
                    break;
                }
            }
            if (next < 0) {
                break;
            }
            // 自己的后续回合按「理性」推演（我知道自己会怎么打）；
            // 别人的回合按混合对手模型推演（人是不可预测的）。
            takeTurn(dice, left, view, next, rnd, next == me);
            idx = next;
        }

        // 3. 官方结算，只统计我自己的收入
        Map<Integer, Integer> diceMap = new HashMap<>();
        double gain = 0;
        for (int v = 1; v <= CASINOS; v++) {
            diceMap.clear();
            for (int seat = 0; seat < n; seat++) {
                int c = dice[v][seat];
                if (c > 0) {
                    diceMap.put(seat, c);
                }
            }
            if (diceMap.isEmpty()) {
                continue;
            }
            CasinoPayout payout = LasVegasPayout.settle(v, view.notesList[v], diceMap);
            Integer award = payout.getAwards().get(me);
            if (award != null) {
                gain += award;
            }
        }
        return gain;
    }

    /** 模拟一次「掷出全部剩余骰子 → 选一个点数 → 全部放进去」 */
    private static void takeTurn(int[][] dice, int[] left, View view, int seat, Random rnd,
                                 boolean ownTurn) {
        Map<Integer, Integer> rollBuffer = new HashMap<>();
        int diceCount = left[seat];
        for (int i = 0; i < diceCount; i++) {
            int die = 1 + rnd.nextInt(CASINOS);
            rollBuffer.merge(die, 1, Integer::sum);
        }
        int pick = pickValue(dice, left, view, seat, rollBuffer, rnd, ownTurn);
        int count = rollBuffer.getOrDefault(pick, 0);
        if (count <= 0) {
            // 理论上不会发生；兜底防止死循环
            count = 1;
        }
        dice[pick][seat] += count;
        left[seat] -= count;
    }

    /**
     * 选点：先抽一种性格，再据此选点。
     *
     * 真实玩家不可预测，所以这里故意混入一定比例的「莽夫」和「新手」，
     * 让 AI 的策略对这些打法也保持稳健。
     *
     * @param ownTurn true 表示这是「我自己」的后续回合，只按理性模型推演
     */
    private static int pickValue(int[][] dice, int[] left, View view, int seat,
                                 Map<Integer, Integer> options, Random rnd, boolean ownTurn) {
        List<Integer> keys = new ArrayList<>(options.keySet());
        Collections.sort(keys);
        if (keys.size() == 1) {
            return keys.get(0);
        }
        boolean countOnly = false;
        if (!ownTurn) {
            int roll = rnd.nextInt(10);
            if (roll == 0) {
                // 10% 新手：随机乱放
                return keys.get(rnd.nextInt(keys.size()));
            }
            countOnly = roll == 1; // 10% 莽夫：只看骰子数
        }

        int best = keys.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int value : keys) {
            double score = onePlyScore(dice, left, view, seat, value, options.get(value), countOnly);
            if (score > bestScore) {
                bestScore = score;
                best = value;
            }
        }
        return best;
    }

    /**
     * 一手评估：我在该赌场大概能拿到多少，再按「被追平/被超过」的风险打折。
     *
     * @param countOnly true 表示莽夫模型，只在意骰子数
     */
    private static double onePlyScore(int[][] dice, int[] left, View view, int seat,
                                      int value, int count, boolean countOnly) {
        int n = left.length;
        int mine = dice[value][seat] + count;
        int ahead = 0;
        int tied = 0;
        int vulnerable = 0;
        for (int other = 0; other < n; other++) {
            if (other == seat) {
                continue;
            }
            int cur = dice[value][other];
            if (cur > mine) {
                ahead++;
            } else if (cur == mine) {
                // 现在就已经和我一样多：按规则双方都会作废
                tied++;
            } else if (cur + left[other] >= mine) {
                // 还有骰子，随时可能追平我
                vulnerable++;
            }
        }
        int notes = view.noteCountAt(value);
        double gain = 0;
        if (tied == 0 && ahead < notes) {
            gain = view.notesDesc[value][ahead];
        }
        if (countOnly) {
            return gain + count * 1000.0;
        }
        // 已被平手锁死或随时可能被追平 → 打折
        double risk = 1.0 - 0.45 * tied - 0.18 * vulnerable;
        if (risk < 0.05) {
            risk = 0.05;
        }
        return gain * risk + count * 500.0;
    }

    // ==================================================================
    // 小工具
    // ==================================================================

    /** 投骰结果 → 点数 : 骰子数 */
    public static Map<Integer, Integer> optionsOf(List<Integer> roll) {
        Map<Integer, Integer> options = new HashMap<>();
        if (roll != null) {
            for (Integer die : roll) {
                if (die != null && die >= 1 && die <= CASINOS) {
                    options.merge(die, 1, Integer::sum);
                }
            }
        }
        return options;
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return values.length == 0 ? 0 : sum / values.length;
    }

    /** values 必须已升序排序 */
    private static double quantile(double[] values, double q) {
        if (values.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(q * values.length) - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= values.length) {
            idx = values.length - 1;
        }
        return values[idx];
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
