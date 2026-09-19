package com.mrj.switchfourcheckers;

public final class ConnectFourGame {
    public static final int ROWS = 6;
    public static final int COLS = 7;

    private final int[][] board = new int[ROWS][COLS];
    private int currentPlayer = 1;
    private int winner = 0;
    private boolean draw = false;
    private int moves = 0;

    public int getCell(int row, int col) {
        return board[row][col];
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public int getWinner() {
        return winner;
    }

    public boolean isDraw() {
        return draw;
    }

    public boolean isGameOver() {
        return winner != 0 || draw;
    }

    public boolean canDrop(int col) {
        return !isGameOver() && col >= 0 && col < COLS && board[0][col] == 0;
    }

    public int drop(int col) {
        if (!canDrop(col)) return -1;
        int row = -1;
        for (int r = ROWS - 1; r >= 0; r--) {
            if (board[r][col] == 0) {
                row = r;
                board[r][col] = currentPlayer;
                break;
            }
        }
        moves++;
        if (hasFour(row, col, currentPlayer)) {
            winner = currentPlayer;
        } else if (moves == ROWS * COLS) {
            draw = true;
        } else {
            currentPlayer = 3 - currentPlayer;
        }
        return row;
    }

    public void reset() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) board[r][c] = 0;
        }
        currentPlayer = 1;
        winner = 0;
        draw = false;
        moves = 0;
    }

    private boolean hasFour(int row, int col, int player) {
        int[][] dirs = {{1,0}, {0,1}, {1,1}, {1,-1}};
        for (int[] d : dirs) {
            int count = 1;
            count += countDir(row, col, d[0], d[1], player);
            count += countDir(row, col, -d[0], -d[1], player);
            if (count >= 4) return true;
        }
        return false;
    }

    private int countDir(int row, int col, int dr, int dc, int player) {
        int count = 0;
        int r = row + dr;
        int c = col + dc;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLS && board[r][c] == player) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }
}
