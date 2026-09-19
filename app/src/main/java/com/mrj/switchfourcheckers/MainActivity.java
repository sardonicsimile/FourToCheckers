package com.mrj.switchfourcheckers;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.List;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(15, 18, 28));
        getWindow().setNavigationBarColor(Color.rgb(15, 18, 28));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        setContentView(new GameView());
    }

    private final class GameView extends View {
        private static final int MODE_CONNECT4 = 0;
        private static final int MODE_CHECKERS = 1;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ConnectFourGame connect4 = new ConnectFourGame();
        private final CheckersGame checkers = new CheckersGame();
        private final RectF boardRect = new RectF();
        private final RectF resetRect = new RectF();
        private final RectF switchRect = new RectF();
        private int mode = MODE_CONNECT4;
        private int selectedRow = -1;
        private int selectedCol = -1;
        private List<CheckersGame.Move> selectedMoves = java.util.Collections.emptyList();
        private int transitionGeneration = 0;

        private final int bg = Color.rgb(15, 18, 28);
        private final int panel = Color.rgb(27, 32, 47);
        private final int text = Color.rgb(242, 244, 248);
        private final int muted = Color.rgb(166, 174, 190);
        private final int accent = Color.rgb(94, 129, 244);
        private final int red = Color.rgb(240, 74, 86);
        private final int yellow = Color.rgb(255, 203, 62);
        private final int boardBlue = Color.rgb(48, 92, 220);
        private final int darkSquare = Color.rgb(86, 58, 45);
        private final int lightSquare = Color.rgb(222, 190, 155);
        private final int blackPiece = Color.rgb(39, 42, 51);

        GameView() {
            super(MainActivity.this);
            p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            setBackgroundColor(bg);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawHeader(canvas);
            if (mode == MODE_CONNECT4) drawConnectFour(canvas); else drawCheckers(canvas);
            drawControls(canvas);
        }

        private void drawHeader(Canvas c) {
            float w = getWidth();
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(text);
            p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
            p.setTextSize(dp(28));
            c.drawText(mode == MODE_CONNECT4 ? "CONNECT FOUR" : "CHECKERS", dp(20), dp(42), p);

            p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            p.setTextSize(dp(14));
            p.setColor(muted);
            String status;
            if (mode == MODE_CONNECT4) {
                if (connect4.getWinner() != 0) status = playerName(connect4.getWinner()) + " wins — switching to Checkers…";
                else if (connect4.isDraw()) status = "Draw — switching to Checkers…";
                else status = playerName(connect4.getCurrentPlayer()) + " to drop";
            } else {
                if (checkers.getWinner() != 0) status = checkerName(checkers.getWinner()) + " wins";
                else if (checkers.getForcedRow() >= 0) status = checkerName(checkers.getCurrentPlayer()) + " must continue jumping";
                else status = checkerName(checkers.getCurrentPlayer()) + " to move";
            }
            c.drawText(status, dp(20), dp(66), p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(panel);
            c.drawRoundRect(w - dp(112), dp(20), w - dp(20), dp(52), dp(16), dp(16), p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(dp(12));
            p.setColor(text);
            p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
            c.drawText(mode == MODE_CONNECT4 ? "GAME 1 / 2" : "GAME 2 / 2", w - dp(66), dp(41), p);
        }

        private void layoutBoard(float desiredRatio) {
            float w = getWidth();
            float top = dp(92);
            float bottomControls = dp(128);
            float availableH = getHeight() - top - bottomControls;
            float maxW = w - dp(32);
            float bw = maxW;
            float bh = bw / desiredRatio;
            if (bh > availableH) {
                bh = availableH;
                bw = bh * desiredRatio;
            }
            float left = (w - bw) / 2f;
            boardRect.set(left, top + (availableH - bh) / 2f, left + bw, top + (availableH - bh) / 2f + bh);
        }

        private void drawConnectFour(Canvas c) {
            layoutBoard(7f / 6f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(boardBlue);
            c.drawRoundRect(boardRect, dp(16), dp(16), p);
            float cellW = boardRect.width() / ConnectFourGame.COLS;
            float cellH = boardRect.height() / ConnectFourGame.ROWS;
            float radius = Math.min(cellW, cellH) * 0.37f;
            for (int r = 0; r < ConnectFourGame.ROWS; r++) {
                for (int col = 0; col < ConnectFourGame.COLS; col++) {
                    float cx = boardRect.left + (col + 0.5f) * cellW;
                    float cy = boardRect.top + (r + 0.5f) * cellH;
                    int v = connect4.getCell(r, col);
                    p.setColor(v == 1 ? red : v == 2 ? yellow : bg);
                    c.drawCircle(cx, cy, radius, p);
                    if (v != 0) {
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(dp(2));
                        p.setColor(Color.argb(45, 255, 255, 255));
                        c.drawCircle(cx, cy, radius - dp(2), p);
                        p.setStyle(Paint.Style.FILL);
                    }
                }
            }
        }

        private void drawCheckers(Canvas c) {
            layoutBoard(1f);
            float cell = boardRect.width() / CheckersGame.SIZE;
            for (int r = 0; r < CheckersGame.SIZE; r++) {
                for (int col = 0; col < CheckersGame.SIZE; col++) {
                    float l = boardRect.left + col * cell;
                    float t = boardRect.top + r * cell;
                    p.setColor((r + col) % 2 == 0 ? lightSquare : darkSquare);
                    c.drawRect(l, t, l + cell, t + cell, p);
                }
            }

            if (selectedRow >= 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(4));
                p.setColor(accent);
                float l = boardRect.left + selectedCol * cell;
                float t = boardRect.top + selectedRow * cell;
                c.drawRect(l + dp(2), t + dp(2), l + cell - dp(2), t + cell - dp(2), p);
                p.setStyle(Paint.Style.FILL);
                for (CheckersGame.Move m : selectedMoves) {
                    float cx = boardRect.left + (m.toCol + .5f) * cell;
                    float cy = boardRect.top + (m.toRow + .5f) * cell;
                    p.setColor(Color.argb(190, 94, 129, 244));
                    c.drawCircle(cx, cy, cell * .14f, p);
                }
            }

            for (int r = 0; r < CheckersGame.SIZE; r++) {
                for (int col = 0; col < CheckersGame.SIZE; col++) {
                    int piece = checkers.getCell(r, col);
                    if (piece == CheckersGame.EMPTY) continue;
                    float cx = boardRect.left + (col + .5f) * cell;
                    float cy = boardRect.top + (r + .5f) * cell;
                    float radius = cell * .36f;
                    p.setColor(piece > 0 ? red : blackPiece);
                    c.drawCircle(cx, cy, radius, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(dp(2));
                    p.setColor(piece > 0 ? Color.rgb(255, 148, 154) : Color.rgb(125, 130, 142));
                    c.drawCircle(cx, cy, radius - dp(2), p);
                    p.setStyle(Paint.Style.FILL);
                    if (Math.abs(piece) == 2) {
                        p.setColor(yellow);
                        p.setTextAlign(Paint.Align.CENTER);
                        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
                        p.setTextSize(cell * .36f);
                        c.drawText("K", cx, cy + cell * .13f, p);
                    }
                }
            }
        }

        private void drawControls(Canvas c) {
            float y = getHeight() - dp(88);
            float gap = dp(12);
            float margin = dp(20);
            float buttonW = (getWidth() - margin * 2 - gap) / 2f;
            resetRect.set(margin, y, margin + buttonW, y + dp(52));
            switchRect.set(resetRect.right + gap, y, getWidth() - margin, y + dp(52));
            drawButton(c, resetRect, "RESET", panel);
            drawButton(c, switchRect, mode == MODE_CONNECT4 ? "CHECKERS →" : "← CONNECT FOUR", accent);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            p.setTextSize(dp(11));
            p.setColor(muted);
            c.drawText("Local 2-player • no ads • offline", getWidth() / 2f, getHeight() - dp(18), p);
        }

        private void drawButton(Canvas c, RectF rect, String label, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRoundRect(rect, dp(14), dp(14), p);
            p.setColor(text);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
            p.setTextSize(dp(14));
            float baseline = rect.centerY() - (p.ascent() + p.descent()) / 2f;
            c.drawText(label, rect.centerX(), baseline, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX(), y = e.getY();
            if (resetRect.contains(x, y)) {
                transitionGeneration++;
                if (mode == MODE_CONNECT4) connect4.reset(); else checkers.reset();
                clearSelection();
                invalidate();
                return true;
            }
            if (switchRect.contains(x, y)) {
                transitionGeneration++;
                switchMode();
                return true;
            }
            if (!boardRect.contains(x, y)) return true;
            if (mode == MODE_CONNECT4) handleConnectFourTouch(x); else handleCheckersTouch(x, y);
            return true;
        }

        private void handleConnectFourTouch(float x) {
            if (connect4.isGameOver()) return;
            int col = (int) ((x - boardRect.left) / (boardRect.width() / ConnectFourGame.COLS));
            if (col < 0 || col >= ConnectFourGame.COLS) return;
            if (connect4.drop(col) >= 0) {
                invalidate();
                if (connect4.isGameOver()) scheduleCheckersSwitch();
            }
        }

        private void scheduleCheckersSwitch() {
            final int generation = ++transitionGeneration;
            postDelayed(() -> {
                if (generation == transitionGeneration && mode == MODE_CONNECT4 && connect4.isGameOver()) {
                    mode = MODE_CHECKERS;
                    checkers.reset();
                    clearSelection();
                    invalidate();
                }
            }, 1600);
        }

        private void handleCheckersTouch(float x, float y) {
            if (checkers.isGameOver()) return;
            float cell = boardRect.width() / CheckersGame.SIZE;
            int col = (int) ((x - boardRect.left) / cell);
            int row = (int) ((y - boardRect.top) / cell);
            if (row < 0 || row >= CheckersGame.SIZE || col < 0 || col >= CheckersGame.SIZE) return;

            if (selectedRow >= 0) {
                for (CheckersGame.Move m : selectedMoves) {
                    if (m.toRow == row && m.toCol == col) {
                        checkers.move(selectedRow, selectedCol, row, col);
                        if (checkers.getForcedRow() >= 0) {
                            select(checkers.getForcedRow(), checkers.getForcedCol());
                        } else {
                            clearSelection();
                        }
                        invalidate();
                        return;
                    }
                }
            }
            if (checkers.isSelectable(row, col)) select(row, col); else clearSelection();
            invalidate();
        }

        private void select(int row, int col) {
            selectedRow = row;
            selectedCol = col;
            selectedMoves = checkers.getLegalMoves(row, col);
        }

        private void clearSelection() {
            selectedRow = selectedCol = -1;
            selectedMoves = java.util.Collections.emptyList();
        }

        private void switchMode() {
            mode = mode == MODE_CONNECT4 ? MODE_CHECKERS : MODE_CONNECT4;
            clearSelection();
            invalidate();
        }

        private String playerName(int player) {
            return player == 1 ? "Red" : "Yellow";
        }

        private String checkerName(int player) {
            return player > 0 ? "Red" : "Black";
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }
}
