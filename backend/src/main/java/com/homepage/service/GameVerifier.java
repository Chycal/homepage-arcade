package com.homepage.service;

import java.util.*;

import org.springframework.stereotype.Component;

/**
 * 游戏回放验证器
 *
 * 用确定的种子和操作序列重放整个游戏，验证最终分数是否匹配。
 */
@Component
public class GameVerifier {

    // 与前端保持一致
    private static final int GRID_SIZE = 20;
    private static final int INITIAL_SNAKE_LENGTH = 3;

    private final int gridSize;

    public GameVerifier() {
        this(GRID_SIZE);
    }

    public GameVerifier(int gridSize) {
        this.gridSize = gridSize;
    }

    /**
     * 回放结果
     */
    public static class ReplayResult {
        public final boolean success;
        public final int verifiedScore;
        public final String errorMsg;
        public final int totalSteps;
        public final int foodsEaten;

        public ReplayResult(boolean success, int score, String errorMsg, int steps, int foods) {
            this.success = success;
            this.verifiedScore = score;
            this.errorMsg = errorMsg;
            this.totalSteps = steps;
            this.foodsEaten = foods;
        }
    }

    /**
     * 用给定种子和操作序列重放游戏，验证分数
     *
     * @param seed            伪随机种子（后端保密）
     * @param moves           操作序列字符串 "URDL..." (U=上 D=下 L=左 R=右)
     * @param submittedScore  客户端声称的分数
     * @return ReplayResult
     */
    public ReplayResult verify(int seed, String moves, int submittedScore) {
        if (moves == null || moves.isEmpty()) {
            return new ReplayResult(false, 0, "操作序列为空", 0, 0);
        }
        if (moves.length() > 20000) {
            return new ReplayResult(false, 0, "操作序列过长 (>" + 20000 + ")", 0, 0);
        }

        // 完整回放
        ReplayState state = replayFull(seed, moves);
        if (state.errorMsg != null) {
            return new ReplayResult(false, state.score, state.errorMsg, state.steps, state.foodsEaten);
        }
        if (state.isDead()) {
            // 正常死亡，分数有效
        }

        boolean match = (state.score == submittedScore);
        String error = match ? null :
                "分数不匹配：声明 " + submittedScore + "，实际回放 " + state.score;

        return new ReplayResult(match, state.score, error, state.steps, state.foodsEaten);
    }

    /**
     * 部分回放：执行到指定步数，返回当前游戏状态（用于下发食物）
     *
     * @param seed  伪随机种子
     * @param moves 操作序列（可能为空或不完整）
     * @return 当前游戏状态（含蛇身、占据集合、已吃食物数、下一步食物位置等）
     */
    public ReplayState replayToCurrent(int seed, String moves) {
        return replayFull(seed, moves);
    }

    /**
     * 完整回放引擎（共享逻辑）
     */
    private ReplayState replayFull(int seed, String moves) {
        GameRng rng = new GameRng(seed);

        // 初始化蛇（起点同前端：头部 (10,10)，向左展开）
        LinkedList<Point> snake = new LinkedList<>();
        snake.add(new Point(10, 10)); // head
        snake.add(new Point(9, 10));
        snake.add(new Point(8, 10));

        int direction = 3; // 0=UP, 1=DOWN, 2=LEFT, 3=RIGHT
        int score = 0;
        int foodsEaten = 0;
        Set<String> occupied = new HashSet<>();
        for (Point p : snake) occupied.add(p.key());

        // 生成第一个食物
        Point food = spawnFood(rng, occupied);

        // 解析 RLE 压缩格式 "R5;D8;L3;..."，还原总步数
        List<Segment> segments = parseSegments(moves);
        if (segments == null || segments.isEmpty()) {
            ReplayState st = new ReplayState(snake, direction, occupied, food, score, foodsEaten, 0);
            st.errorMsg = "无法解析操作序列";
            return st;
        }

        int step = 0;
        for (Segment seg : segments) {
            int newDir = seg.dir;
            direction = newDir;  // RLE 每段方向固定，不含 180° 反转

            for (int f = 0; f < seg.count; f++) {
                Point head = snake.peekFirst();
                Point newHead = move(head, direction);
                if (newHead == null) {
                    ReplayState st = new ReplayState(snake, direction, occupied, food, score, foodsEaten, step);
                    st.terminal = true;
                    return st;
                }

                boolean selfCollision = false;
                for (Point p : snake) {
                    if (p.x == newHead.x && p.y == newHead.y) {
                        selfCollision = true;
                        break;
                    }
                }
                if (selfCollision) {
                    ReplayState st = new ReplayState(snake, direction, occupied, food, score, foodsEaten, step);
                    st.terminal = true;
                    return st;
                }

                snake.addFirst(newHead);
                occupied.add(newHead.key());

                if (newHead.x == food.x && newHead.y == food.y) {
                    score += 10;
                    foodsEaten++;
                    food = spawnFood(rng, occupied);
                } else {
                    Point tail = snake.removeLast();
                    occupied.remove(tail.key());
                }
                step++;
            }
        }

        return new ReplayState(snake, direction, occupied, food, score, foodsEaten, step);
    }

    /** RLE 压缩段 */
    private static class Segment {
        final int dir;
        final int count;
        Segment(int dir, int count) { this.dir = dir; this.count = count; }
    }

    /** 解析 RLE 格式: "R5;D8;L3;U2" -> List<Segment> */
    private List<Segment> parseSegments(String moves) {
        if (moves == null || moves.isEmpty()) return Collections.emptyList();
        List<Segment> segs = new ArrayList<>();
        for (String token : moves.split(";")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            char c = token.charAt(0);
            int dir = directionFromChar(c);
            if (dir < 0) return null;
            int count;
            try {
                count = Integer.parseInt(token.substring(1));
            } catch (NumberFormatException e) {
                return null;
            }
            if (count <= 0) return null;
            segs.add(new Segment(dir, count));
        }
        return segs;
    }

    /**
     * 完整游戏状态（用于部分回放的结果传递，也用于内部 replayFull）
     */
    public static class ReplayState {
        public final LinkedList<Point> snake;
        public final int direction;
        public final Set<String> occupied;
        public final Point currentFood;  // 当前在场上的食物
        public final int score;
        public final int foodsEaten;
        public final int steps;
        public String errorMsg;
        public boolean terminal;

        ReplayState(LinkedList<Point> snake, int direction, Set<String> occupied,
                    Point currentFood, int score, int foodsEaten, int steps) {
            this.snake = snake;
            this.direction = direction;
            this.occupied = occupied;
            this.currentFood = currentFood;
            this.score = score;
            this.foodsEaten = foodsEaten;
            this.steps = steps;
            this.terminal = false;
        }

        public boolean isDead() { return terminal; }
    }

    // ===== 内部方法 =====

    private int directionFromChar(char c) {
        switch (c) {
            case 'U': return 0;
            case 'D': return 1;
            case 'L': return 2;
            case 'R': return 3;
            default:  return -1;
        }
    }

    private boolean isOpposite(int d1, int d2) {
        return (d1 == 0 && d2 == 1) || (d1 == 1 && d2 == 0) ||
               (d1 == 2 && d2 == 3) || (d1 == 3 && d2 == 2);
    }

    private Point move(Point p, int dir) {
        int nx = p.x, ny = p.y;
        switch (dir) {
            case 0: ny--; break; // UP
            case 1: ny++; break; // DOWN
            case 2: nx--; break; // LEFT
            case 3: nx++; break; // RIGHT
        }
        if (nx < 0 || nx >= gridSize || ny < 0 || ny >= gridSize) return null;
        return new Point(nx, ny);
    }

    /**
     * 和前端完全相同的食物生成逻辑
     */
    private Point spawnFood(GameRng rng, Set<String> occupied) {
        int total = gridSize * gridSize;
        int available = total - occupied.size();
        if (available <= 0) return new Point(-1, -1);

        int target = rng.nextInt(available);
        int count = 0;
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                if (!occupied.contains(x + "," + y)) {
                    if (count == target) return new Point(x, y);
                    count++;
                }
            }
        }
        // fallback（不应该走到这里）
        return new Point(0, 0);
    }

    public static class Point {
        public final int x, y;
        public Point(int x, int y) { this.x = x; this.y = y; }
        String key() { return x + "," + y; }
    }
}
