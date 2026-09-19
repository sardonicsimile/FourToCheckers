package com.mrj.switchfourcheckers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CheckersGame {
    public static final int SIZE = 8;
    public static final int EMPTY = 0;
    public static final int RED_MAN = 1;
    public static final int RED_KING = 2;
    public static final int BLACK_MAN = -1;
    public static final int BLACK_KING = -2;

    public static final class Move {
        public final int fromRow, fromCol, toRow, toCol;
        public final boolean capture;
        public Move(int fromRow, int fromCol, int toRow, int toCol, boolean capture) {
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
            this.capture = capture;
        }
    }

    private final int[][] board = new int[SIZE][SIZE];
    private int currentPlayer = RED_MAN;
    private int winner = EMPTY;
    private int forcedRow = -1;
    private int forcedCol = -1;

    public CheckersGame() {
        reset();
    }

    public void reset() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) board[r][c] = EMPTY;
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < SIZE; c++) {
                if ((r + c) % 2 == 1) board[r][c] = BLACK_MAN;
            }
        }
        for (int r = 5; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if ((r + c) % 2 == 1) board[r][c] = RED_MAN;
            }
        }
        currentPlayer = RED_MAN;
        winner = EMPTY;
        forcedRow = forcedCol = -1;
    }

    public int getCell(int row, int col) { return board[row][col]; }
    public int getCurrentPlayer() { return currentPlayer; }
    public int getWinner() { return winner; }
    public boolean isGameOver() { return winner != EMPTY; }
    public int getForcedRow() { return forcedRow; }
    public int getForcedCol() { return forcedCol; }

    public List<Move> getLegalMoves(int row, int col) {
        if (isGameOver() || !inside(row, col)) return Collections.emptyList();
        int piece = board[row][col];
        if (!belongsTo(piece, currentPlayer)) return Collections.emptyList();
        if (forcedRow >= 0 && (row != forcedRow || col != forcedCol)) return Collections.emptyList();

        boolean capturesRequired = forcedRow >= 0 || playerHasCapture(currentPlayer);
        return capturesRequired ? captureMoves(row, col) : simpleMoves(row, col);
    }

    public boolean move(int fromRow, int fromCol, int toRow, int toCol) {
        Move chosen = null;
        for (Move m : getLegalMoves(fromRow, fromCol)) {
            if (m.toRow == toRow && m.toCol == toCol) {
                chosen = m;
                break;
            }
        }
        if (chosen == null) return false;

        int piece = board[fromRow][fromCol];
        boolean wasMan = Math.abs(piece) == 1;
        board[fromRow][fromCol] = EMPTY;
        board[toRow][toCol] = piece;

        if (chosen.capture) {
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;
            board[midRow][midCol] = EMPTY;
        }

        boolean promoted = false;
        if (piece == RED_MAN && toRow == 0) { board[toRow][toCol] = RED_KING; promoted = true; }
        if (piece == BLACK_MAN && toRow == SIZE - 1) { board[toRow][toCol] = BLACK_KING; promoted = true; }

        if (chosen.capture && !(wasMan && promoted) && !captureMoves(toRow, toCol).isEmpty()) {
            forcedRow = toRow;
            forcedCol = toCol;
            return true;
        }

        forcedRow = forcedCol = -1;
        currentPlayer = -currentPlayer;
        updateWinner();
        return true;
    }

    public boolean isSelectable(int row, int col) {
        return !getLegalMoves(row, col).isEmpty();
    }

    private void updateWinner() {
        if (!hasAnyPiece(currentPlayer) || !hasAnyLegalMove(currentPlayer)) {
            winner = -currentPlayer;
        }
    }

    private boolean hasAnyPiece(int player) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (belongsTo(board[r][c], player)) return true;
            }
        }
        return false;
    }

    private boolean hasAnyLegalMove(int player) {
        if (playerHasCapture(player)) return true;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (belongsTo(board[r][c], player) && !simpleMoves(r, c).isEmpty()) return true;
            }
        }
        return false;
    }

    private boolean playerHasCapture(int player) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (belongsTo(board[r][c], player) && !captureMoves(r, c).isEmpty()) return true;
            }
        }
        return false;
    }

    private List<Move> simpleMoves(int row, int col) {
        List<Move> moves = new ArrayList<>();
        int piece = board[row][col];
        for (int[] d : directions(piece)) {
            int nr = row + d[0], nc = col + d[1];
            if (inside(nr, nc) && board[nr][nc] == EMPTY) {
                moves.add(new Move(row, col, nr, nc, false));
            }
        }
        return moves;
    }

    private List<Move> captureMoves(int row, int col) {
        List<Move> moves = new ArrayList<>();
        int piece = board[row][col];
        if (piece == EMPTY) return moves;
        for (int[] d : directions(piece)) {
            int mr = row + d[0], mc = col + d[1];
            int nr = row + 2 * d[0], nc = col + 2 * d[1];
            if (inside(nr, nc) && board[nr][nc] == EMPTY && inside(mr, mc)
                    && board[mr][mc] != EMPTY && !belongsTo(board[mr][mc], piece)) {
                moves.add(new Move(row, col, nr, nc, true));
            }
        }
        return moves;
    }

    private int[][] directions(int piece) {
        if (Math.abs(piece) == 2) return new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}};
        if (piece > 0) return new int[][]{{-1,-1},{-1,1}};
        return new int[][]{{1,-1},{1,1}};
    }

    private static boolean belongsTo(int piece, int player) {
        return piece != EMPTY && Integer.signum(piece) == Integer.signum(player);
    }

    private static boolean inside(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }
}
