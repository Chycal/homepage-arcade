package com.homepage.lasvegas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 《拉斯维加斯》(Las Vegas, Rüdiger Dorn / alea 2012) 纯规则引擎。
 *
 * 与 Spring、WebSocket、数据库完全无关，可独立单元测试。
 * 规则依据：alea 官方规则书（© 2011 Rüdiger Dorn / © 2012 Ravensburger，编号 230512）。
 *
 * 核心规则：
 *  - 2-5 名玩家，每人 8 颗同色骰；6 个赌场（点数 1-6）；54 张钞票；共 4 轮。
 *  - 行动：掷出自己「全部」剩余骰 → 必须选「一个」点数 → 该点数的「全部」骰子放到对应赌场。
 *  - 结算：同一点数骰子数相同的玩家「全部作废」；其余按骰子数降序领面额从高到低的钞票。
 *  - 胜负：钱最多者胜；平手比钞票张数；再平手则并列胜利。
 *
 * 未实现（按需求确认）：AI 对手、中立骰变体（2-4 人官方变体）。
 */
public class LasVegasGame {

    public static final int TOTAL_ROUNDS = 4;
    public static final int DICE_PER_PLAYER = 8;
    public static final int CASINO_COUNT = 6;
    /** 每个赌场每轮起始金额下限 */
    public static final int MIN_CASINO_VALUE = 50_000;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 5;

    public enum Phase {
        /** 放置骰子阶段 */
        PLACING,
        /** 4 轮结束，游戏已结束 */
        GAME_OVER
    }

    /** 骰子来源，便于测试注入确定性序列 */
    @FunctionalInterface
    public interface DiceRoller {
        /** @return 1-6 */
        int roll();
    }

    /** 玩家状态 */
    public static class Player {
        private final String id;
        private final int seat;
        private int diceLeft;
        private int money;
        private int banknoteCount;

        Player(String id, int seat) {
            this.id = id;
            this.seat = seat;
        }

        public String getId() {
            return id;
        }

        public int getSeat() {
            return seat;
        }

        public int getDiceLeft() {
            return diceLeft;
        }

        public int getMoney() {
            return money;
        }

        public int getBanknoteCount() {
            return banknoteCount;
        }
    }

    /** 赌场状态 */
    public static class Casino {
        private final int dieValue;
        private final List<Integer> banknotes = new ArrayList<>();
        private final Map<Integer, Integer> diceCount = new LinkedHashMap<>();

        Casino(int dieValue) {
            this.dieValue = dieValue;
        }

        public int getDieValue() {
            return dieValue;
        }
    }

    private final List<Player> players = new ArrayList<>();
    private final Casino[] casinos = new Casino[CASINO_COUNT + 1];
    private final Deque<Integer> deck = new ArrayDeque<>();
    private final DiceRoller roller;

    private int round = 0;
    private int startPlayerIdx = 0;
    private int currentIdx = 0;
    private Phase phase = Phase.PLACING;
    private List<Integer> lastRoll = null;
    private List<CasinoPayout> lastPayout = new ArrayList<>();

    public LasVegasGame(List<String> playerIds) {
        this(playerIds, () -> ThreadLocalRandom.current().nextInt(6) + 1, new Random());
    }

    public LasVegasGame(List<String> playerIds, DiceRoller roller, Random deckRandom) {
        if (playerIds == null || playerIds.size() < MIN_PLAYERS || playerIds.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "玩家人数必须为 " + MIN_PLAYERS + "-" + MAX_PLAYERS + " 人");
        }
        this.roller = roller;
        for (int i = 0; i < playerIds.size(); i++) {
            players.add(new Player(playerIds.get(i), i));
        }
        for (int v = 1; v <= CASINO_COUNT; v++) {
            casinos[v] = new Casino(v);
        }
        deck.addAll(LasVegasBanknotes.buildDeck(deckRandom));
        startRound();
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 当前轮次 1-4 */
    public int getRound() {
        return round;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isOver() {
        return phase == Phase.GAME_OVER;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public Player getPlayer(int seat) {
        return players.get(seat);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getCurrentPlayerIndex() {
        return currentIdx;
    }

    /** 本轮的起始玩家座位（每轮结束后左移一位） */
    public int getStartPlayerIndex() {
        return startPlayerIdx;
    }

    public String getCurrentPlayerId() {
        return players.get(currentIdx).getId();
    }

    /** 当前玩家本回合是否已投骰 */
    public boolean hasRolled() {
        return lastRoll != null;
    }

    /** 当前玩家本次投骰结果（已排序），未投骰时为空 */
    public List<Integer> getLastRoll() {
        return lastRoll == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(lastRoll);
    }

    /** 本次投骰的可选点数 -> 骰子数 */
    public Map<Integer, Integer> getOptions() {
        Map<Integer, Integer> options = new LinkedHashMap<>();
        if (lastRoll == null) {
            return options;
        }
        for (int die : lastRoll) {
            options.merge(die, 1, Integer::sum);
        }
        return options;
    }

    /** 指定赌场当前持有的钞票 */
    public List<Integer> getCasinoBanknotes(int dieValue) {
        checkCasino(dieValue);
        return Collections.unmodifiableList(casinos[dieValue].banknotes);
    }

    /** 指定赌场当前的 座位 -> 骰子数 */
    public Map<Integer, Integer> getCasinoDice(int dieValue) {
        checkCasino(dieValue);
        return Collections.unmodifiableMap(casinos[dieValue].diceCount);
    }

    /** 最近一次结算明细（6 个赌场） */
    public List<CasinoPayout> getLastPayout() {
        return Collections.unmodifiableList(lastPayout);
    }

    /** 钞票堆剩余张数 */
    public int getDeckSize() {
        return deck.size();
    }

    /** 钞票堆剩余总面额 */
    public int getDeckTotalValue() {
        return LasVegasBanknotes.totalValue(new ArrayList<>(deck));
    }

    /** 全部玩家的战绩快照（座位顺序） */
    public List<LasVegasScoring.Standing> getStandings() {
        List<LasVegasScoring.Standing> standings = new ArrayList<>();
        for (Player p : players) {
            standings.add(new LasVegasScoring.Standing(p.seat, p.money, p.banknoteCount));
        }
        return standings;
    }

    /** 结算后按钱多、钞票张数多排序的玩家列表 */
    public List<Player> getRanking() {
        List<Player> sorted = new ArrayList<>(players);
        List<LasVegasScoring.Standing> ordered = LasVegasScoring.ranking(getStandings());
        sorted.sort((a, b) -> {
            int ia = indexOfStanding(ordered, a.seat);
            int ib = indexOfStanding(ordered, b.seat);
            return Integer.compare(ia, ib);
        });
        return sorted;
    }

    private static int indexOfStanding(List<LasVegasScoring.Standing> standings, int seat) {
        for (int i = 0; i < standings.size(); i++) {
            if (standings.get(i).getSeat() == seat) {
                return i;
            }
        }
        return standings.size();
    }

    /**
     * 胜者座位。
     * 钱最多者胜；平手则比钞票张数；仍平手则并列胜利。
     */
    public List<Integer> getWinnerSeats() {
        return LasVegasScoring.winnerSeats(getStandings());
    }

    // ------------------------------------------------------------------
    // 行动
    // ------------------------------------------------------------------

    /**
     * 当前玩家掷出全部剩余骰。
     *
     * @return 本次投骰结果（已排序）
     */
    public List<Integer> roll() {
        if (phase != Phase.PLACING) {
            throw new IllegalStateException("当前阶段不能投骰");
        }
        Player player = players.get(currentIdx);
        if (player.diceLeft <= 0) {
            throw new IllegalStateException("该玩家已无骰子");
        }
        if (lastRoll != null) {
            throw new IllegalStateException("本回合已投骰，请先放置骰子");
        }
        List<Integer> result = new ArrayList<>(player.diceLeft);
        for (int i = 0; i < player.diceLeft; i++) {
            result.add(roller.roll());
        }
        Collections.sort(result);
        lastRoll = result;
        return new ArrayList<>(result);
    }

    /**
     * 选择点数，把该点数的「全部」骰子放到对应赌场。
     * 不允许只放一部分。
     */
    public void choose(int value) {
        if (phase != Phase.PLACING) {
            throw new IllegalStateException("当前阶段不能放置骰子");
        }
        if (value < 1 || value > CASINO_COUNT) {
            throw new IllegalArgumentException("点数必须是 1-" + CASINO_COUNT);
        }
        if (lastRoll == null) {
            throw new IllegalStateException("请先投骰");
        }
        int count = 0;
        for (int die : lastRoll) {
            if (die == value) {
                count++;
            }
        }
        if (count == 0) {
            throw new IllegalArgumentException("本次投掷结果中没有点数 " + value);
        }

        Player player = players.get(currentIdx);
        player.diceLeft -= count;
        casinos[value].diceCount.merge(currentIdx, count, Integer::sum);
        lastRoll = null;
        advance();
    }

    // ------------------------------------------------------------------
    // 内部流程
    // ------------------------------------------------------------------

    private void checkCasino(int dieValue) {
        if (dieValue < 1 || dieValue > CASINO_COUNT) {
            throw new IllegalArgumentException("点数必须是 1-" + CASINO_COUNT);
        }
    }

    /** 开始新一轮：重置骰子、清空赌场、补钞至每场 ≥ $50,000 */
    private void startRound() {
        round++;
        for (Player player : players) {
            player.diceLeft = DICE_PER_PLAYER;
        }
        for (int v = 1; v <= CASINO_COUNT; v++) {
            Casino casino = casinos[v];
            casino.banknotes.clear();
            casino.diceCount.clear();
            while (LasVegasBanknotes.totalValue(casino.banknotes) < MIN_CASINO_VALUE
                    && !deck.isEmpty()) {
                casino.banknotes.add(deck.pollFirst());
            }
        }
        currentIdx = startPlayerIdx;
        lastRoll = null;
        phase = Phase.PLACING;
    }

    /** 轮到下一个还有骰子的玩家；都没有则结算 */
    private void advance() {
        int next = nextActivePlayer(currentIdx);
        if (next < 0) {
            payout();
            return;
        }
        currentIdx = next;
        lastRoll = null;
    }

    /** 从 from 之后顺时针找下一个有骰子的玩家；找不到返回 -1 */
    private int nextActivePlayer(int from) {
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int idx = (from + i) % n;
            if (players.get(idx).diceLeft > 0) {
                return idx;
            }
        }
        return -1;
    }

    /** 一轮结束：逐场结算，然后进入下一轮或结束游戏 */
    private void payout() {
        lastPayout = new ArrayList<>();
        for (int v = 1; v <= CASINO_COUNT; v++) {
            Casino casino = casinos[v];
            CasinoPayout result = LasVegasPayout.settle(v, casino.banknotes, casino.diceCount);

            for (Map.Entry<Integer, Integer> award : result.getAwards().entrySet()) {
                Player player = players.get(award.getKey());
                player.money += award.getValue();
                player.banknoteCount++;
            }
            // 剩余钞票面朝下放回钞票堆底部
            for (int note : result.getLeftover()) {
                deck.addLast(note);
            }
            lastPayout.add(result);

            casino.banknotes.clear();
            casino.diceCount.clear();
        }

        if (round >= TOTAL_ROUNDS) {
            phase = Phase.GAME_OVER;
        } else {
            startPlayerIdx = (startPlayerIdx + 1) % players.size();
            startRound();
        }
    }
}
