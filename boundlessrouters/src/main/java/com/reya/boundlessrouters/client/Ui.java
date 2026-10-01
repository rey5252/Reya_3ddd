package com.reya.boundlessrouters.client;

import com.reya.boundlessrouters.BoundlessRouters;
import com.reya.boundlessrouters.gui.Layouts.Sheet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/** What both screens draw alike: pieces of the widget sheet, small text, clicks. */
final class Ui {
    static final ResourceLocation WIDGETS = tex("widgets");
    static final int TEXT = 0xFFE8EEF4, TEXT_DIM = 0xFF8E98A6, TEXT_LCD = 0xFF6FF5E0, TEXT_TITLE = 0xFFF2D4B0;
    static final int TEAL = 0x39E6D0;

    static ResourceLocation tex(String name) {
        return new ResourceLocation(BoundlessRouters.MODID, "textures/gui/" + name + ".png");
    }

    static void blit(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        g.blit(WIDGETS, x, y, u, v, w, h, Sheet.W, Sheet.H);
    }

    /** A 16 square button: 0 normal, 1 hovered, 2 selected, 3 disabled. */
    static void button(GuiGraphics g, int x, int y, int state) {
        blit(g, x, y, Sheet.BUTTON[0] + 16 * state, Sheet.BUTTON[1], 16, 16);
    }

    /** A 14 square toggle. */
    static void toggle(GuiGraphics g, int x, int y, boolean on, boolean hover) {
        blit(g, x, y, Sheet.TOGGLE[0] + 14 * ((on ? 2 : 0) + (hover ? 1 : 0)), Sheet.TOGGLE[1], 14, 14);
    }

    /** A 10 square button for + and -: 0 normal, 1 hovered, 2 pressed. */
    static void small(GuiGraphics g, int x, int y, int state) {
        blit(g, x, y, Sheet.SMALL[0] + 10 * state, Sheet.SMALL[1], 10, 10);
    }

    /** A 10 square glyph from one of the sheet's rows. */
    static void glyph(GuiGraphics g, int[] row, int index, int x, int y) {
        blit(g, x, y, row[0] + 10 * index, row[1], 10, 10);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Text at a smaller scale (3/4 by default), its top left at (x, y). */
    static void small(GuiGraphics g, Font font, Component text, float x, float y, int colour, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Text at a scale, made smaller still if it would be wider than maxWidth (a long translation), its top left at (x, y). */
    static void fit(GuiGraphics g, Font font, Component text, float x, float y, float maxWidth, int colour, float scale) {
        float width = font.width(text) * scale;
        float s = width > maxWidth ? Math.max(0.5F, scale * maxWidth / width) : scale;
        small(g, font, text, x, y + (scale - s) * 6.0F, colour, s);
    }

    static void centred(GuiGraphics g, Font font, Component text, int cx, int y, int colour, boolean shadow) {
        g.drawString(font, text, cx - font.width(text) / 2, y, colour, shadow);
    }

    /** Centred text, made smaller if it would be wider than maxWidth. */
    static void centredFit(GuiGraphics g, Font font, Component text, int cx, int y, int maxWidth, int colour) {
        int width = font.width(text);
        if (width <= maxWidth) {
            centred(g, font, text, cx, y, colour, false);
            return;
        }
        float s = Math.max(0.5F, maxWidth / (float) width);
        small(g, font, text, cx - width * s / 2.0F, y + (1.0F - s) * 4.0F, colour, s);
    }

    /**
     * A number with its noun in the form the language wants: key.one, key.few or key.many (Ukrainian and Russian
     * have three: 1 блок, 2 блоки, 5 блоків; English two, where few is many).
     */
    static Component count(String key, int n) {
        return Component.translatable(key + "." + plural(n), n);
    }

    static String plural(int n) {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        int a = Math.abs(n);
        if (lang.startsWith("uk") || lang.startsWith("ru")) {
            int last = a % 10, lastTwo = a % 100;
            if (last == 1 && lastTwo != 11) return "one";
            if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) return "few";
            return "many";
        }
        return a == 1 ? "one" : "many";
    }

    private Ui() {
    }
}
