package org.mosh4j.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Minimal framebuffer: fixed size grid, simple ANSI handling (cursor move, clear, basic SGR).
 */
public class SimpleFramebuffer implements Framebuffer {

    private final int width;
    private final int height;
    private final Cell[][] cells;
    private int cursorRow;
    private int cursorCol;
    private boolean cursorVisible = true;
    private String title = "";
    private final MinimalAnsiParser ansiParser;

    public SimpleFramebuffer(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.cells = new Cell[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                cells[r][c] = Cell.blank();
            }
        }
        this.cursorRow = 0;
        this.cursorCol = 0;
        this.ansiParser = new MinimalAnsiParser(this);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public Cell getCell(int row, int col) {
        if (row >= 0 && row < height && col >= 0 && col < width) {
            return cells[row][col];
        }
        return Cell.blank();
    }

    void setCell(int row, int col, Cell cell) {
        if (row >= 0 && row < height && col >= 0 && col < width) {
            cells[row][col] = cell;
        }
    }

    @Override
    public int getCursorRow() {
        return cursorRow;
    }

    @Override
    public int getCursorCol() {
        return cursorCol;
    }

    @Override
    public boolean isCursorVisible() {
        return cursorVisible;
    }

    @Override
    public String getTitle() {
        return title;
    }

    void setCursor(int row, int col) {
        this.cursorRow = clamp(row, 0, height - 1);
        this.cursorCol = clamp(col, 0, width - 1);
    }

    void setCursorVisible(boolean visible) {
        this.cursorVisible = visible;
    }

    void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public void feedHostBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        ansiParser.feed(bytes);
    }

    @Override
    public byte[] toStateBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append("W").append(width).append("H").append(height).append("R").append(cursorRow).append("C").append(cursorCol).append("\n");
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                Cell cell = cells[r][c];
                int[] cp = cell.getCodePoints();
                for (int ch : cp) sb.appendCodePoint(ch);
            }
            sb.append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void fromStateBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        String s = new String(bytes, StandardCharsets.UTF_8);
        int i = 0;
        if (s.startsWith("W") && s.contains("H")) {
            int wEnd = s.indexOf('H');
            int hEnd = s.indexOf('R');
            if (wEnd > 0 && hEnd > wEnd) {
                int w = Integer.parseInt(s.substring(1, wEnd));
                int h = Integer.parseInt(s.substring(wEnd + 1, hEnd));
                if (w == width && h == height) {
                    int cEnd = s.indexOf('\n', hEnd);
                    if (cEnd > 0) {
                        cursorRow = Integer.parseInt(s.substring(hEnd + 1, s.indexOf('C', hEnd)));
                        cursorCol = Integer.parseInt(s.substring(s.indexOf('C', hEnd) + 1, cEnd));
                        i = cEnd + 1;
                    }
                }
            }
        }
        int row = 0, col = 0;
        while (i < s.length() && row < height) {
            char ch = s.charAt(i);
            if (ch == '\n') {
                row++;
                col = 0;
                i++;
                continue;
            }
            if (col < width) {
                int codePoint = Character.codePointAt(s, i);
                setCell(row, col, new Cell(new int[] { codePoint }, 1, 0, 0, 0));
                col++;
                i += Character.charCount(codePoint);
            } else {
                i++;
            }
        }
    }
}
