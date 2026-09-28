package com.reya.goldenquarry.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The little square letters of the grey title plate (5 pixels high, one pixel apart), as the
 * reference writes "ВАКУУМНЫЙ СУНДУК": В А К У М Н Ы Й С Д are read off it, the other letters
 * drawn the same way. Each glyph is 7 rows: an accent row, the 5 letter rows, a descender row.
 */
public final class PlateFont {
    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    private static void put(String chars, String... rows) {
        String[] full = rows.length == 7 ? rows : new String[]{blank(rows[0]), rows[0], rows[1], rows[2], rows[3], rows[4], blank(rows[0])};
        for (char c : chars.toCharArray()) GLYPHS.put(c, full);
    }

    private static String blank(String row) {
        return ".".repeat(row.length());
    }

    static {
        put("AА", ".XXX", "X..X", "XXXX", "X..X", "X..X");
        put("BВ", "XXX.", "X..X", "XXX.", "X..X", "XXXX");
        put("CС", "XXXX", "X...", "X...", "X...", "XXXX");
        put("D", "XXX.", "X..X", "X..X", "X..X", "XXX.");
        put("EЕ", "XXXX", "X...", "XXX.", "X...", "XXXX");
        put("F", "XXXX", "X...", "XXX.", "X...", "X...");
        put("G", "XXXX", "X...", "X.XX", "X..X", "XXXX");
        put("HН", "X..X", "X..X", "XXXX", "X..X", "X..X");
        put("IІ", "XXX", ".X.", ".X.", ".X.", "XXX");
        put("J", "...X", "...X", "...X", "X..X", "XXXX");
        put("KК", "X..X", "X..X", "XXX.", "X..X", "X..X");
        put("L", "X...", "X...", "X...", "X...", "XXXX");
        put("MМ", "XX.XX", "X.X.X", "X.X.X", "X...X", "X...X");
        put("N", "X..X", "XX.X", "X.XX", "X..X", "X..X");
        put("OО", "XXXX", "X..X", "X..X", "X..X", "XXXX");
        put("PР", "XXX.", "X..X", "XXX.", "X...", "X...");
        put("Q", "XXXX", "X..X", "X..X", "X.XX", "XXXX");
        put("R", "XXX.", "X..X", "XXX.", "X..X", "X..X");
        put("S", "XXXX", "X...", "XXXX", "...X", "XXXX");
        put("TТ", "XXXXX", "..X..", "..X..", "..X..", "..X..");
        put("U", "X..X", "X..X", "X..X", "X..X", "XXXX");
        put("V", "X...X", "X...X", "X...X", ".X.X.", "..X..");
        put("W", "X...X", "X...X", "X.X.X", "X.X.X", "XX.XX");
        put("XХ", "X..X", "X..X", ".XX.", "X..X", "X..X");
        put("Y", "X.X", "X.X", ".X.", ".X.", ".X.");
        put("Z", "XXXX", "...X", ".XX.", "X...", "XXXX");
        put("У", "X..X", "X..X", ".XXX", "...X", "XXX.");
        put("Ы", "X....X", "X....X", "XXXX.X", "X..X.X", "XXXX.X");
        put("И", "X..X", "X..X", "X.XX", "XX.X", "X..X");
        put("Й", ".XX.", "X..X", "X..X", "X.XX", "XX.X", "X..X", "....");
        put("Д", ".....", ".XXXX", ".X..X", ".X..X", ".X..X", "XXXXX", "X...X");
        put("Я", ".XXX", "X..X", ".XXX", ".X.X", "X..X");
        put("Г", "XXXX", "X...", "X...", "X...", "X...");
        put("Л", ".XXX", ".X.X", ".X.X", ".X.X", "XX.X");
        put("П", "XXXX", "X..X", "X..X", "X..X", "X..X");
        put("Ь", "X...", "X...", "XXX.", "X..X", "XXX.");
        put("Ч", "X..X", "X..X", "XXXX", "...X", "...X");
        put("Ш", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "XXXXX");
        put("Ю", "X.XXX", "X.X.X", "XXX.X", "X.X.X", "X.XXX");
        put("Ж", "X.X.X", "X.X.X", ".XXX.", "X.X.X", "X.X.X");
        put("З", "XXX.", "...X", ".XX.", "...X", "XXX.");
        put("Ф", ".XXX.", "X.X.X", "X.X.X", ".XXX.", "..X..");
        put("Ц", ".....", "X..X.", "X..X.", "X..X.", "X..X.", "XXXXX", "....X");
        put("Щ", ".......", "X.X.X.", "X.X.X.", "X.X.X.", "X.X.X.", "XXXXXX", ".....X");
        put("Б", "XXXX", "X...", "XXX.", "X..X", "XXX.");
        put("Э", "XXX.", "...X", ".XXX", "...X", "XXX.");
        put("Є", ".XXX", "X...", "XXX.", "X...", ".XXX");
        put("Ї", "X.X", ".X.", ".X.", ".X.", ".X.");
        put("Ъ", "XX..", ".X..", ".XX.", ".X.X", ".XX.");
        put("0", "XXXX", "X..X", "X..X", "X..X", "XXXX");
        put("1", ".X", "XX", ".X", ".X", ".X");
        put("2", "XXXX", "...X", "XXXX", "X...", "XXXX");
        put("3", "XXXX", "...X", ".XXX", "...X", "XXXX");
        put("-", "...", "...", "XXX", "...", "...");
    }

    /** Width in pixels (a space is three, letters one pixel apart). */
    public static int width(String text) {
        int w = 0;
        String s = text.toUpperCase(Locale.ROOT);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) w += 1;
            String[] g = GLYPHS.get(s.charAt(i));
            w += g == null ? 3 : g[1].length();
        }
        return w;
    }

    /** Draws the text with its letter rows starting at y (accents one row above, descenders one below). */
    public static void draw(GuiGraphics g, String text, int x, int y, int color) {
        String s = text.toUpperCase(Locale.ROOT);
        for (int i = 0; i < s.length(); i++) {
            String[] glyph = GLYPHS.get(s.charAt(i));
            if (glyph == null) {
                x += 4;
                continue;
            }
            for (int r = 0; r < 7; r++) {
                String row = glyph[r];
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) == 'X') g.fill(x + c, y - 1 + r, x + c + 1, y + r, color);
                }
            }
            x += glyph[1].length() + 1;
        }
    }

    private PlateFont() {
    }
}
