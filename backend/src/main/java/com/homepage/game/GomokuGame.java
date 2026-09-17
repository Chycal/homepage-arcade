package com.homepage.game;

import java.util.*;

/**
 * 五子棋游戏引擎，纯逻辑无状态依赖
 */
public class GomokuGame {

    public static final int SIZE = 15;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

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

    public int getCurrentPlayer() { return currentPlayer; }
    public int getWinner() { return winner; }
    public boolean isOver() { return winner != 0; }
    public int getMoveCount() { return moveHistory.size(); }

    public int[] getLastMove() {
        return moveHistory.isEmpty() ? null : moveHistory.get(moveHistory.size() - 1);
    }

    /** 五连检测 */
    private boolean checkWin(int row, int col, int player) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
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
