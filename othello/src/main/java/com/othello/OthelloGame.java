package com.othello;

import java.util.ArrayList;
import java.util.List;

public class OthelloGame {

    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    public enum Difficulty { EASY, NORMAL, HARD }
    public enum Mode { VS_AI, VS_HUMAN }

    private int[][] board;
    private int currentPlayer;
    private boolean gameOver;
    private String winner;
    private Difficulty difficulty;
    private Mode mode;

    private static final int[][] DIRECTIONS = {
        {-1,-1},{-1,0},{-1,1},
        { 0,-1},        { 0,1},
        { 1,-1},{ 1,0},{ 1,1}
    };

    private static final int[][] WEIGHTS = {
        {100,-20, 10,  5,  5, 10,-20,100},
        {-20,-50, -2, -2, -2, -2,-50,-20},
        { 10, -2,  3,  1,  1,  3, -2, 10},
        {  5, -2,  1,  0,  0,  1, -2,  5},
        {  5, -2,  1,  0,  0,  1, -2,  5},
        { 10, -2,  3,  1,  1,  3, -2, 10},
        {-20,-50, -2, -2, -2, -2,-50,-20},
        {100,-20, 10,  5,  5, 10,-20,100}
    };

    public OthelloGame(Difficulty difficulty, Mode mode) {
        this.difficulty = difficulty;
        this.mode = mode;
        board = new int[8][8];
        board[3][3] = WHITE; board[3][4] = BLACK;
        board[4][3] = BLACK; board[4][4] = WHITE;
        currentPlayer = BLACK;
        gameOver = false;
        winner = null;
    }

    public int[][] getBoard() {
        int[][] copy = new int[8][8];
        for (int i = 0; i < 8; i++) System.arraycopy(board[i], 0, copy[i], 0, 8);
        return copy;
    }

    public int getCurrentPlayer() { return currentPlayer; }
    public boolean isGameOver() { return gameOver; }
    public String getWinner() { return winner; }
    public Difficulty getDifficulty() { return difficulty; }
    public Mode getMode() { return mode; }

    public int getScore(int player) {
        int count = 0;
        for (int[] row : board) for (int cell : row) if (cell == player) count++;
        return count;
    }

    public List<int[]> getValidMoves(int player) {
        return getValidMovesOnBoard(player, board);
    }

    private boolean isValidMove(int[][] b, int row, int col, int player) {
        if (b[row][col] != EMPTY) return false;
        int opponent = (player == BLACK) ? WHITE : BLACK;
        for (int[] dir : DIRECTIONS) {
            int r = row + dir[0], c = col + dir[1];
            if (r < 0 || r >= 8 || c < 0 || c >= 8 || b[r][c] != opponent) continue;
            r += dir[0]; c += dir[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8) {
                if (b[r][c] == EMPTY) break;
                if (b[r][c] == player) return true;
                r += dir[0]; c += dir[1];
            }
        }
        return false;
    }

    public boolean playerMove(int row, int col) {
        if (gameOver) return false;
        if (!isValidMove(board, row, col, currentPlayer)) return false;
        applyMove(board, row, col, currentPlayer);
        advanceTurn();
        return true;
    }

    public int[] aiMove() {
        if (gameOver || mode == Mode.VS_HUMAN || currentPlayer != WHITE) return null;
        List<int[]> moves = getValidMoves(WHITE);
        if (moves.isEmpty()) { advanceTurn(); return null; }

        int depth = switch (difficulty) {
            case EASY   -> 1;
            case NORMAL -> 3;
            case HARD   -> 6;
        };

        int[] best;
        if (difficulty == Difficulty.EASY) {
            // Random move for easy
            best = new int[]{0, moves.get((int)(Math.random() * moves.size()))[0],
                                moves.get((int)(Math.random() * moves.size()))[1]};
            // pick one random move properly
            int[] picked = moves.get((int)(Math.random() * moves.size()));
            best = new int[]{0, picked[0], picked[1]};
        } else {
            best = minimax(board, depth, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        }

        int row = best[1], col = best[2];
        applyMove(board, row, col, WHITE);
        advanceTurn();
        return new int[]{row, col};
    }

    private void applyMove(int[][] b, int row, int col, int player) {
        b[row][col] = player;
        int opponent = (player == BLACK) ? WHITE : BLACK;
        for (int[] dir : DIRECTIONS) {
            List<int[]> toFlip = new ArrayList<>();
            int r = row + dir[0], c = col + dir[1];
            while (r >= 0 && r < 8 && c >= 0 && c < 8 && b[r][c] == opponent) {
                toFlip.add(new int[]{r, c});
                r += dir[0]; c += dir[1];
            }
            if (r >= 0 && r < 8 && c >= 0 && c < 8 && b[r][c] == player) {
                for (int[] pos : toFlip) b[pos[0]][pos[1]] = player;
            }
        }
    }

    private void advanceTurn() {
        int next = (currentPlayer == BLACK) ? WHITE : BLACK;
        if (!getValidMovesOnBoard(next, board).isEmpty()) {
            currentPlayer = next;
        } else if (!getValidMovesOnBoard(currentPlayer, board).isEmpty()) {
            // pass
        } else {
            gameOver = true;
            int black = getScore(BLACK), white = getScore(WHITE);
            if (black > white) winner = "black";
            else if (white > black) winner = "white";
            else winner = "draw";
        }
    }

    private List<int[]> getValidMovesOnBoard(int player, int[][] b) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (isValidMove(b, r, c, player)) moves.add(new int[]{r, c});
        return moves;
    }

    private int[] minimax(int[][] b, int depth, int alpha, int beta, boolean maximizing) {
        int player = maximizing ? WHITE : BLACK;
        List<int[]> moves = getValidMovesOnBoard(player, b);
        if (depth == 0 || moves.isEmpty()) return new int[]{evaluate(b), -1, -1};

        int bestScore = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int bestRow = moves.get(0)[0], bestCol = moves.get(0)[1];

        for (int[] move : moves) {
            int[][] copy = copyBoard(b);
            applyMove(copy, move[0], move[1], player);
            int score = minimax(copy, depth - 1, alpha, beta, !maximizing)[0];
            if (maximizing && score > bestScore) {
                bestScore = score; bestRow = move[0]; bestCol = move[1];
                alpha = Math.max(alpha, score);
            } else if (!maximizing && score < bestScore) {
                bestScore = score; bestRow = move[0]; bestCol = move[1];
                beta = Math.min(beta, score);
            }
            if (beta <= alpha) break;
        }
        return new int[]{bestScore, bestRow, bestCol};
    }

    private int evaluate(int[][] b) {
        int score = 0;
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                if (b[r][c] == WHITE) score += WEIGHTS[r][c];
                else if (b[r][c] == BLACK) score -= WEIGHTS[r][c];
            }
        return score;
    }

    private int[][] copyBoard(int[][] b) {
        int[][] copy = new int[8][8];
        for (int i = 0; i < 8; i++) System.arraycopy(b[i], 0, copy[i], 0, 8);
        return copy;
    }
}
