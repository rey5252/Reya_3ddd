package com.reya.alfheimheart.portal.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The little square letters of the name plates (5 pixels high, one pixel apart), in the style of the
 * LoliMagically plates the GUI follows. Each glyph is 7 rows: an accent row, the 5 letter rows and a
 * descender row. Latin, Ukrainian and Russian capitals, digits and a few signs.
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
        put("AА", ".XX.", "X..X", "XXXX", "X..X", "X..X");
        put("BВ", "XXX.", "X..X", "XXX.", "X..X", "XXX.");
        put("CС", ".XXX", "X...", "X...", "X...", ".XXX");
        put("D", "XXX.", "X..X", "X..X", "X..X", "XXX.");
        put("EЕ", "XXXX", "X...", "XXX.", "X...", "XXXX");
        put("F", "XXXX", "X...", "XXX.", "X...", "X...");
        put("G", ".XXX", "X...", "X.XX", "X..X", ".XXX");
        put("HН", "X..X", "X..X", "XXXX", "X..X", "X..X");
        put("IІ", "XXX", ".X.", ".X.", ".X.", "XXX");
        put("J", "...X", "...X", "...X", "X..X", ".XX.");
        put("KК", "X..X", "X.X.", "XX..", "X.X.", "X..X");
        put("L", "X...", "X...", "X...", "X...", "XXXX");
        put("MМ", "X...X", "XX.XX", "X.X.X", "X...X", "X...X");
        put("N", "X..X", "XX.X", "X.XX", "X..X", "X..X");
        put("OО", ".XX.", "X..X", "X..X", "X..X", ".XX.");
        put("PР", "XXX.", "X..X", "XXX.", "X...", "X...");
        put("Q", ".XX.", "X..X", "X..X", "X.X.", ".X.X");
        put("R", "XXX.", "X..X", "XXX.", "X.X.", "X..X");
        put("S", ".XXX", "X...", ".XX.", "...X", "XXX.");
        put("TТ", "XXXXX", "..X..", "..X..", "..X..", "..X..");
        put("U", "X..X", "X..X", "X..X", "X..X", ".XX.");
        put("V", "X...X", "X...X", ".X.X.", ".X.X.", "..X..");
        put("W", "X...X", "X...X", "X.X.X", "X.X.X", ".X.X.");
        put("XХ", "X..X", "X..X", ".XX.", "X..X", "X..X");
        put("Y", "X.X", "X.X", ".X.", ".X.", ".X.");
        put("Z", "XXXX", "...X", ".XX.", "X...", "XXXX");
        put("Б", "XXXX", "X...", "XXX.", "X..X", "XXX.");
        put("Г", "XXXX", "X...", "X...", "X...", "X...");
        put("Ґ", "...X", "XXXX", "X...", "X...", "X...", "X...", "....");
        put("Д", ".....", ".XXX.", ".X.X.", ".X.X.", ".X.X.", "XXXXX", "X...X");
        put("Є", ".XXX", "X...", "XXX.", "X...", ".XXX");
        put("Э", "XXX.", "...X", ".XXX", "...X", "XXX.");
        put("Ж", "X.X.X", "X.X.X", ".XXX.", "X.X.X", "X.X.X");
        put("З", "XXX.", "...X", ".XX.", "...X", "XXX.");
        put("И", "X..X", "X..X", "X.XX", "XX.X", "X..X");
        put("Й", ".XX.", "X..X", "X..X", "X.XX", "XX.X", "X..X", "....");
        put("Ї", "X.X", ".X.", ".X.", ".X.", ".X.", ".X.", "...");
        put("Л", ".XXX", ".X.X", ".X.X", ".X.X", "XX.X");
        put("П", "XXXX", "X..X", "X..X", "X..X", "X..X");
        put("У", "X..X", "X..X", ".XXX", "...X", "XXX.");
        put("Ф", ".XXX.", "X.X.X", "X.X.X", ".XXX.", "..X..");
        put("Ц", ".....", "X..X.", "X..X.", "X..X.", "X..X.", "XXXXX", "....X");
        put("Ч", "X..X", "X..X", ".XXX", "...X", "...X");
        put("Ш", "X.X.X", "X.X.X", "X.X.X", "X.X.X", "XXXXX");
        put("Щ", "......", "X.X.X.", "X.X.X.", "X.X.X.", "X.X.X.", "XXXXXX", ".....X");
        put("Ъ", "XX..", ".X..", ".XX.", ".X.X", ".XX.");
        put("Ы", "X...X", "X...X", "XXX.X", "X..XX", "XXX.X");
        put("Ь", "X...", "X...", "XXX.", "X..X", "XXX.");
        put("Ю", "X.XX.", "X.X.X", "XXX.X", "X.X.X", "X.XX.");
        put("Я", ".XXX", "X..X", ".XXX", ".X.X", "X..X");
        put("0", ".XX.", "X..X", "X..X", "X..X", ".XX.");
        put("1", ".X", "XX", ".X", ".X", ".X");
        put("2", "XXX.", "...X", ".XX.", "X...", "XXXX");
        put("3", "XXX.", "...X", ".XX.", "...X", "XXX.");
        put("4", "X..X", "X..X", "XXXX", "...X", "...X");
        put("5", "XXXX", "X...", "XXX.", "...X", "XXX.");
        put("6", ".XX.", "X...", "XXX.", "X..X", ".XX.");
        put("7", "XXXX", "...X", "..X.", ".X..", ".X..");
        put("8", ".XX.", "X..X", ".XX.", "X..X", ".XX.");
        put("9", ".XX.", "X..X", ".XXX", "...X", ".XX.");
        put("-", "...", "...", "XXX", "...", "...");
        put("'’", "X", "X", ".", ".", ".");
        put(".", ".", ".", ".", ".", "X");
        put("!", "X", "X", "X", ".", "X");
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
