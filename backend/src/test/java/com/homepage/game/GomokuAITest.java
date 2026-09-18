package com.homepage.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五子棋 AI 单元测试：空棋盘开局、必胜点把握、威胁封堵、合法落子
 */
class GomokuAITest {

    private static final int SIZE = GomokuGame.SIZE;
    private final Random random = new Random(42); // 固定种子保证可复现

    /** 在棋盘上摆一条水平连子 */
    private static void line(int[][] board, int row, int colFrom, int count, int player) {
        for (int i = 0; i < count; i++) board[row][colFrom + i] = player;
    }

    /** 判断 row 行是否存在黑棋五连 */
    private static boolean blackHasFiveInRow(int[][] board, int row) {
        int run = 0;
        for (int c = 0; c < SIZE; c++) {
            run = board[row][c] == GomokuGame.BLACK ? run + 1 : 0;
            if (run >= 5) return true;
        }
        return false;
    }

    /** 随机撒几颗散子制造中盘局面 */
    private void scatter(int[][] board, int player, int count) {
        int placed = 0;
        while (placed < count) {
            int r = random.nextInt(SIZE), c = random.nextInt(SIZE);
            if (board[r][c] == 0) { board[r][c] = player; placed++; }
        }
    }

    @Nested
    @DisplayName("开局与合法落子")
    class LegalMoves {

        @Test
        @DisplayName("空棋盘直接抢天元 (7,7)")
        void emptyBoardTakesCenter() {
            int[][] board = new int[SIZE][SIZE];
            int[] mv = GomokuAI.findBestMove(board, GomokuGame.BLACK);
            assertThat(mv).containsExactly(7, 7);
        }

        @Test
        @DisplayName("中盘局面只落空位且不越界")
        void midgameMoveIsValid() {
            int[][] board = new int[SIZE][SIZE];
            board[7][7] = GomokuGame.BLACK;
            board[7][8] = GomokuGame.WHITE;
            board[8][7] = GomokuGame.WHITE;
            scatter(board, GomokuGame.BLACK, 3);
            scatter(board, GomokuGame.WHITE, 3);

            int[] mv = GomokuAI.findBestMove(board, GomokuGame.BLACK);
            assertThat(mv).isNotNull();
            assertThat(mv[0]).isBetween(0, SIZE - 1);
            assertThat(mv[1]).isBetween(0, SIZE - 1);
            assertThat(board[mv[0]][mv[1]]).isZero();
        }

        @Test
        @DisplayName("执白与执黑都能给出合法落子")
        void bothColorsWork() {
            int[][] board = new int[SIZE][SIZE];
            board[7][7] = GomokuGame.BLACK;
            int[] asWhite = GomokuAI.findBestMove(board, GomokuGame.WHITE);
            assertThat(asWhite).isNotNull();
            assertThat(board[asWhite[0]][asWhite[1]]).isZero();
        }
    }

    @Nested
    @DisplayName("攻防判断")
    class AttackAndDefense {

        @Test
        @DisplayName("自己有四连时完成五连（抓住制胜点）")
        void takesWinningMove() {
            int[][] board = new int[SIZE][SIZE];
            line(board, 7, 3, 4, GomokuGame.BLACK);      // 黑 (7,3)-(7,6) 四连
            board[9][9] = GomokuGame.WHITE;
            board[9][10] = GomokuGame.WHITE;

            int[] mv = GomokuAI.findBestMove(board, GomokuGame.BLACK);
            board[mv[0]][mv[1]] = GomokuGame.BLACK;
            // 黑棋必须形成五连（补 (7,2) 或 (7,7) 均可）
            assertThat(blackHasFiveInRow(board, 7))
                    .as("AI 应在 (%d,%d) 完成五连", mv[0], mv[1])
                    .isTrue();
        }

        @Test
        @DisplayName("对方四连两头开放时，必堵其中一端")
        void blocksOpenFour() {
            int[][] board = new int[SIZE][SIZE];
            line(board, 7, 3, 4, GomokuGame.WHITE);      // 白四连 (7,3)-(7,6)，两端开放
            board[0][0] = GomokuGame.BLACK;
            board[0][1] = GomokuGame.BLACK;

            int[] mv = GomokuAI.findBestMove(board, GomokuGame.BLACK);
            boolean blocksLeft = (mv[0] == 7 && mv[1] == 2);
            boolean blocksRight = (mv[0] == 7 && mv[1] == 7);
            assertThat(blocksLeft || blocksRight)
                    .as("AI 应堵在 (7,2) 或 (7,7)，实际 (%d,%d)", mv[0], mv[1])
                    .isTrue();
        }

        @Test
        @DisplayName("自己能成五时优先于封堵对方四连（攻大于防）")
        void winPreferredOverBlock() {
            int[][] board = new int[SIZE][SIZE];
            line(board, 7, 3, 4, GomokuGame.BLACK);      // 黑四连，可成五
            line(board, 10, 3, 4, GomokuGame.WHITE);     // 白也四连

            int[] mv = GomokuAI.findBestMove(board, GomokuGame.BLACK);
            board[mv[0]][mv[1]] = GomokuGame.BLACK;
            assertThat(blackHasFiveInRow(board, 7))
                    .as("AI 应优先自己成五，实际 (%d,%d)", mv[0], mv[1])
                    .isTrue();
        }

        @Test
        @DisplayName("对方冲四（一端被封）同样会被封堵唯一活点")
        void blocksRushFour() {
            int[][] board = new int[SIZE][SIZE];
            line(board, 7, 2, 4, GomokuGame.WHITE);      // 白 (7,2)-(7,5)
            board[7][1] = GomokuGame.BLACK;              // 左端被黑封死，只剩 (7,6) 一端
            board[0][0] = GomokuGame.BLACK;

            int[] mv = GomokuAI.findBestMove(board, GomokuGame.BLACK);
            assertThat(mv[0] == 7 && mv[1] == 6)
                    .as("AI 应堵在 (7,6)，实际 (%d,%d)", mv[0], mv[1])
                    .isTrue();
        }
    }
}
