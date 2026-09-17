package com.homepage.game;

/**
 * 五子棋 AI：基于局面评分的启发式搜索
 */
public class GomokuAI {

    // 评分权重
    private static final int FIVE    = 1_000_000;
    private static final int LIVE4   = 100_000;
    private static final int RUSH4   = 10_000;
    private static final int LIVE3   = 5_000;
    private static final int SLEEP3  = 500;
    private static final int LIVE2   = 200;
    private static final int SLEEP2  = 50;
    private static final int LIVE1   = 10;

    // 方向向量
    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    /**
     * 寻找最佳落子位置
     * @param board 当前棋盘（15x15，0=空，1=黑，2=白）
     * @param player AI 执子（BLACK 或 WHITE）
     * @return {row, col}，失败返回 null
     */
    public static int[] findBestMove(int[][] board, int player) {
        int size = board.length;
        int opponent = (player == GomokuGame.BLACK) ? GomokuGame.WHITE : GomokuGame.BLACK;
        int bestScore = -1;
        int[] bestMove = null;

        // 空棋盘 → 抢天元
        boolean isEmpty = true;
        outer:
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (board[r][c] != 0) { isEmpty = false; break outer; }
        if (isEmpty) return new int[]{7, 7};

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] != 0) continue;

                int attack = evalPos(board, r, c, player);
                int defense = evalPos(board, r, c, opponent);
                int score = attack + (int) (defense * 0.95);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = new int[]{r, c};
                }
            }
        }

        return bestMove;
    }

    /**
     * 评估在 (row, col) 落 player 子的价值
     */
    private static int evalPos(int[][] board, int row, int col, int player) {
        int score = 0;
        for (int[] d : DIRS) {
            score += evalDir(board, row, col, player, d[0], d[1]);
        }
        return score;
    }

    /**
     * 评估单方向上 (row, col) 落子的价值
     */
    private static int evalDir(int[][] board, int row, int col, int player, int dr, int dc) {
        int size = board.length;

        // 正方向连续数
        int posCount = 0;
        for (int i = 1; i < 5; i++) {
            int r = row + dr * i, c = col + dc * i;
            if (r >= 0 && r < size && c >= 0 && c < size && board[r][c] == player) posCount++;
            else break;
        }
        int posEndR = row + dr * (posCount + 1);
        int posEndC = col + dc * (posCount + 1);
        boolean posOpen = posEndR >= 0 && posEndR < size && posEndC >= 0 && posEndC < size
                && board[posEndR][posEndC] == 0;

        // 反方向连续数
        int negCount = 0;
        for (int i = 1; i < 5; i++) {
            int r = row - dr * i, c = col - dc * i;
            if (r >= 0 && r < size && c >= 0 && c < size && board[r][c] == player) negCount++;
            else break;
        }
        int negEndR = row - dr * (negCount + 1);
        int negEndC = col - dc * (negCount + 1);
        boolean negOpen = negEndR >= 0 && negEndR < size && negEndC >= 0 && negEndC < size
                && board[negEndR][negEndC] == 0;

        int total = posCount + negCount + 1; // +1 = 落子位本身

        return scoreLine(total, posOpen, negOpen);
    }

    /** 根据连子数和开口数返回评分 */
    private static int scoreLine(int count, boolean openA, boolean openB) {
        if (count >= 5) return FIVE;

        boolean bothOpen = openA && openB;
        boolean oneOpen  = openA || openB;

        switch (count) {
            case 4: return bothOpen ? LIVE4 : RUSH4;
            case 3: return bothOpen ? LIVE3 : SLEEP3;
            case 2: return bothOpen ? LIVE2 : SLEEP2;
            case 1: return bothOpen ? LIVE1 : 0;
            default: return 0;
        }
    }
}
