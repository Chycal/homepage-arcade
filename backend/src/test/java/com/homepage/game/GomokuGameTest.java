package com.homepage.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五子棋引擎单元测试：胜负判定、非法落子、悔棋、重开。
 * 落子严格按引擎轮换规则（黑先，黑白交替），模拟真实对局。
 */
class GomokuGameTest {

    private GomokuGame game;

    @BeforeEach
    void setUp() {
        game = new GomokuGame();
    }

    /**
     * 按对局顺序落子：偶数步黑棋、奇数步白棋。
     * moves[i] = {row, col}。
     */
    private void play(int[][] moves) {
        for (int i = 0; i < moves.length; i++) {
            int player = (i % 2 == 0) ? GomokuGame.BLACK : GomokuGame.WHITE;
            assertThat(game.place(moves[i][0], moves[i][1], player))
                    .as("第 %d 步 (%d,%d)", i, moves[i][0], moves[i][1])
                    .isTrue();
        }
    }

    /**
     * 构造一局：黑棋在 (row,col) 沿 (dr,dc) 五连获胜。
     * 白棋应手放在 col=14 的离散点（(1,14)(3,14)(5,14)(7,14)，互不成五连，且与所有黑线路径不相交）。
     */
    private int[][] blackWinningLine(int row, int col, int dr, int dc) {
        int[][] moves = new int[9][];
        int[][] whites = {{1, 14}, {3, 14}, {5, 14}, {7, 14}};
        for (int i = 0; i < 5; i++) {
            moves[2 * i] = new int[]{row + dr * i, col + dc * i};
            if (i < 4) moves[2 * i + 1] = whites[i];
        }
        return moves;
    }

    @Nested
    @DisplayName("胜负判定")
    class WinDetection {

        @Test
        @DisplayName("横向五连获胜")
        void horizontalWin() {
            play(blackWinningLine(7, 3, 0, 1));
            assertThat(game.getWinner()).isEqualTo(GomokuGame.BLACK);
            assertThat(game.isOver()).isTrue();
        }

        @Test
        @DisplayName("纵向五连获胜")
        void verticalWin() {
            play(blackWinningLine(2, 5, 1, 0));
            assertThat(game.getWinner()).isEqualTo(GomokuGame.BLACK);
        }

        @Test
        @DisplayName("主对角线五连获胜")
        void diagonalWin() {
            play(blackWinningLine(3, 3, 1, 1));
            assertThat(game.getWinner()).isEqualTo(GomokuGame.BLACK);
        }

        @Test
        @DisplayName("反对角线五连获胜")
        void antiDiagonalWin() {
            play(blackWinningLine(3, 11, 1, -1));
            assertThat(game.getWinner()).isEqualTo(GomokuGame.BLACK);
        }

        @Test
        @DisplayName("白棋五连同样获胜")
        void whiteWinsToo() {
            // 黑棋散子应手，白棋纵向连五（第 9 步黑先成五）
            play(new int[][]{
                    {0, 0}, {10, 5}, {0, 1}, {11, 5}, {0, 2}, {12, 5}, {0, 3}, {13, 5}, {0, 4}, {14, 5}
            });
            assertThat(game.getWinner()).isEqualTo(GomokuGame.BLACK);
            // 白棋此时只有纵向四连
            assertThat(game.getMoveCount()).isEqualTo(9);
        }

        @Test
        @DisplayName("四连不获胜")
        void fourInARowIsNotWin() {
            play(new int[][]{
                    {7, 3}, {1, 14}, {7, 4}, {3, 14}, {7, 5}, {5, 14}, {7, 6}, {7, 14}
            });
            assertThat(game.isOver()).isFalse();
            assertThat(game.getWinner()).isZero();
        }

        @Test
        @DisplayName("中间断开的五子不获胜")
        void brokenLineIsNotWin() {
            play(new int[][]{
                    {7, 3}, {1, 14}, {7, 4}, {3, 14}, {7, 5}, {5, 14}, {7, 6}, {7, 14}, {7, 8}, {9, 14}
            });
            assertThat(game.isOver()).isFalse();
        }

        @Test
        @DisplayName("紧贴棋盘左缘的五连获胜（方向越界不误判）")
        void winAtBoardEdge() {
            play(blackWinningLine(0, 0, 0, 1));
            assertThat(game.getWinner()).isEqualTo(GomokuGame.BLACK);
        }

        @Test
        @DisplayName("角落反向五连获胜（不越界）")
        void winAtBoardCorner() {
            play(blackWinningLine(14, 14, -1, -1));
            assertThat(game.getWinner()).isEqualTo(GomokuGame.BLACK);
        }

        @Test
        @DisplayName("六连同样判定获胜（cnt >= 5）")
        void overlineAlsoWins() {
            // 黑: 3,4,5,6,8 先断开放置，最后补 7 → 形成 3..8 六连
            play(new int[][]{
                    {7, 3}, {1, 14}, {7, 4}, {3, 14}, {7, 5}, {5, 14}, {7, 6}, {7, 14}, {7, 8}, {9, 14}, {7, 7}
            });
            assertThat(game.getWinner()).isEqualTo(GomokuGame.BLACK);
        }
    }

    @Nested
    @DisplayName("落子规则")
    class PlacementRules {

        @Test
        @DisplayName("黑棋先行，白先下被拒")
        void blackGoesFirst() {
            assertThat(game.getCurrentPlayer()).isEqualTo(GomokuGame.BLACK);
            assertThat(game.place(7, 7, GomokuGame.WHITE)).isFalse();
            assertThat(game.place(7, 7, GomokuGame.BLACK)).isTrue();
        }

        @Test
        @DisplayName("落子成功后轮换到对方")
        void turnAlternates() {
            game.place(7, 7, GomokuGame.BLACK);
            assertThat(game.getCurrentPlayer()).isEqualTo(GomokuGame.WHITE);
            game.place(7, 8, GomokuGame.WHITE);
            assertThat(game.getCurrentPlayer()).isEqualTo(GomokuGame.BLACK);
        }

        @Test
        @DisplayName("不能重复落子")
        void cannotPlaceOnOccupied() {
            game.place(7, 7, GomokuGame.BLACK);
            assertThat(game.place(7, 7, GomokuGame.WHITE)).isFalse();
        }

        @Test
        @DisplayName("不能越界落子")
        void cannotPlaceOutOfBounds() {
            assertThat(game.place(-1, 0, GomokuGame.BLACK)).isFalse();
            assertThat(game.place(0, -1, GomokuGame.BLACK)).isFalse();
            assertThat(game.place(GomokuGame.SIZE, 0, GomokuGame.BLACK)).isFalse();
            assertThat(game.place(0, GomokuGame.SIZE, GomokuGame.BLACK)).isFalse();
        }

        @Test
        @DisplayName("不是轮到自己时落子被拒")
        void cannotPlaceOutOfTurn() {
            game.place(7, 7, GomokuGame.BLACK);
            assertThat(game.place(7, 8, GomokuGame.BLACK)).isFalse();
        }

        @Test
        @DisplayName("分出胜负后不能再落子")
        void cannotPlaceAfterGameOver() {
            play(blackWinningLine(7, 3, 0, 1));
            assertThat(game.isOver()).isTrue();
            assertThat(game.place(0, 0, GomokuGame.WHITE)).isFalse();
        }

        @Test
        @DisplayName("落子记录与最后一步")
        void moveHistoryTracked() {
            assertThat(game.getLastMove()).isNull();
            play(new int[][]{{7, 7}, {7, 8}});
            assertThat(game.getMoveCount()).isEqualTo(2);
            int[] last = game.getLastMove();
            assertThat(last).containsExactly(7, 8, GomokuGame.WHITE);
        }
    }

    @Nested
    @DisplayName("悔棋与重开")
    class UndoAndReset {

        @Test
        @DisplayName("不足两步时悔棋失败")
        void undoFailsWithFewerThanTwoMoves() {
            assertThat(game.undo()).isFalse();
            game.place(7, 7, GomokuGame.BLACK);
            assertThat(game.undo()).isFalse();
        }

        @Test
        @DisplayName("悔棋撤回双方各一步，轮到申请方")
        void undoRemovesBothMoves() {
            play(new int[][]{{7, 7}, {7, 8}, {8, 7}, {8, 8}});
            assertThat(game.undo()).isTrue();
            assertThat(game.getMoveCount()).isEqualTo(2);
            assertThat(game.getCurrentPlayer()).isEqualTo(GomokuGame.BLACK);
            int[][] board = new int[GomokuGame.SIZE][GomokuGame.SIZE];
            game.copyBoard(board);
            assertThat(board[8][7]).isZero();
            assertThat(board[8][8]).isZero();
            assertThat(board[7][7]).isEqualTo(GomokuGame.BLACK);
            assertThat(board[7][8]).isEqualTo(GomokuGame.WHITE);
        }

        @Test
        @DisplayName("获胜后悔棋恢复对局")
        void undoAfterWinRestoresGame() {
            play(blackWinningLine(7, 3, 0, 1));
            assertThat(game.isOver()).isTrue();
            assertThat(game.undo()).isTrue();
            assertThat(game.isOver()).isFalse();
            assertThat(game.getWinner()).isZero();
        }

        @Test
        @DisplayName("重开清空棋局")
        void resetClearsEverything() {
            play(new int[][]{{7, 7}, {7, 8}});
            game.reset();
            assertThat(game.getMoveCount()).isZero();
            assertThat(game.getCurrentPlayer()).isEqualTo(GomokuGame.BLACK);
            assertThat(game.isOver()).isFalse();
            int[][] board = new int[GomokuGame.SIZE][GomokuGame.SIZE];
            game.copyBoard(board);
            for (int[] row : board) {
                for (int cell : row) assertThat(cell).isZero();
            }
        }

        @Test
        @DisplayName("copyBoard 是深拷贝，修改目标数组不影响内部棋盘")
        void copyBoardIsDeepCopy() {
            game.place(7, 7, GomokuGame.BLACK);
            int[][] target = new int[GomokuGame.SIZE][GomokuGame.SIZE];
            game.copyBoard(target);
            target[7][7] = GomokuGame.WHITE;
            int[][] again = new int[GomokuGame.SIZE][GomokuGame.SIZE];
            game.copyBoard(again);
            assertThat(again[7][7]).isEqualTo(GomokuGame.BLACK);
        }
    }
}
