package com.homepage.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 五子棋游戏引擎，纯逻辑无状态依赖。
 *
 * 规则约定：
 * <ul>
 *   <li>15×15 棋盘，{@link #BLACK} 先手（见构造函数）；</li>
 *   <li>横、竖、两条对角线任一方向连成 5 子即胜（{@link #checkWin}，
 *       超过 5 连也算胜，不做「长连禁手」判定）；</li>
 *   <li>分出胜负后不再接受落子（{@link #place} 直接返回 false）；</li>
 *   <li>悔棋一次撤回两步——对方的回应加上申请方自己那一步（{@link #undo}）。</li>
 * </ul>
 *
 * 本类只管棋盘状态，不涉及网络与 AI：房间层用 {@link #copyBoard} 拿快照去序列化，
 * 落子决策由 {@link GomokuAI} 负责。
 */
public class GomokuGame {

    public static final int SIZE = 15;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    /**
     * 五连检测的四个方向：水平、垂直、主对角线、副对角线。
     *
     * 只列「一半」方向就够了——检测时每个方向都会往正负两侧同时数，
     * 反方向与正方向查的是同一条线。GomokuAI 里有一份同样的方向集合
     * （那边顺序不同，因为 AI 扫描棋型的顺序不影响结果）。
     */
    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private final int[][] board;
    private int currentPlayer;
    private final List<int[]> moveHistory;
    private int winner;

    public GomokuGame() {
        board = new int[SIZE][SIZE];
        currentPlayer = BLACK;
        moveHistory = new ArrayList<>();
        winner = 0;
    }

    /** 落子，返回是否成功 */
    public boolean place(int row, int col, int player) {
        if (winner != 0) return false;
        if (player != currentPlayer) return false;
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return false;
        if (board[row][col] != EMPTY) return false;

        board[row][col] = player;
        moveHistory.add(new int[]{row, col, player});

        if (checkWin(row, col, player)) {
            winner = player;
        } else {
            currentPlayer = (currentPlayer == BLACK) ? WHITE : BLACK;
        }
        return true;
    }

    /** 悔棋（撤回双方各一步：对方回应 + 申请方上一步），返回是否成功 */
    public boolean undo() {
        if (moveHistory.size() < 2) return false;
        int[] oppMove = moveHistory.remove(moveHistory.size() - 1);  // 对方回应
        int[] reqMove = moveHistory.remove(moveHistory.size() - 1);  // 申请方上一步
        board[oppMove[0]][oppMove[1]] = EMPTY;  // 清除对方棋子
        board[reqMove[0]][reqMove[1]] = EMPTY;  // 清除申请方棋子
        currentPlayer = reqMove[2];              // 轮到申请方
        winner = 0;
        return true;
    }

    /** 重置棋局 */
    public void reset() {
        for (int[] row : board) Arrays.fill(row, EMPTY);
        currentPlayer = BLACK;
        moveHistory.clear();
        winner = 0;
    }

    /** 深拷贝棋盘到目标数组 */
    public void copyBoard(int[][] target) {
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(board[i], 0, target[i], 0, SIZE);
        }
    }

    /** 当前该谁落子（{@link #BLACK} 或 {@link #WHITE}） */
    public int getCurrentPlayer() { return currentPlayer; }

    /** 胜者（{@link #BLACK} 或 {@link #WHITE}）；尚未分出胜负时为 {@link #EMPTY} */
    public int getWinner() { return winner; }

    /** 是否已分出胜负 */
    public boolean isOver() { return winner != 0; }

    /** 已落子总数（悔棋会减少） */
    public int getMoveCount() { return moveHistory.size(); }

    /** 最后一步 {@code {row, col, player}}；棋盘为空时返回 null */
    public int[] getLastMove() {
        return moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
    }

    /** 五连检测：以刚落下的 (row, col) 为中心，四个方向各自向正负两侧数同色子 */
    private boolean checkWin(int row, int col, int player) {
        for (int[] d : DIRS) {
            int cnt = 1;
            for (int i = 1; i < 5; i++) {
                int r = row + d[0] * i, c = col + d[1] * i;
                if (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == player) cnt++;
                else break;
            }
            for (int i = 1; i < 5; i++) {
                int r = row - d[0] * i, c = col - d[1] * i;
                if (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == player) cnt++;
                else break;
            }
            if (cnt >= 5) return true;
        }
        return false;
    }
}
