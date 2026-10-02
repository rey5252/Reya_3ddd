package com.reya.singularityfusion.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small things the screen draws: centred text that shrinks to fit, hit tests. */
final class Ui {
    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static void centred(GuiGraphics g, Font font, Component text, int cx, int y, int colour) {
        g.drawString(font, text, cx - font.width(text) / 2, y, colour, false);
    }

    /** Centred text, made smaller if it would be wider than maxWidth (a long translation). */
    static void centredFit(GuiGraphics g, Font font, Component text, int cx, int y, int maxWidth, int colour) {
        int width = font.width(text);
        if (width <= maxWidth) {
            centred(g, font, text, cx, y, colour);
            return;
        }
        float s = Math.max(0.5F, maxWidth / (float) width);
        g.pose().pushPose();
        g.pose().translate(cx - width * s / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private Ui() {
    }
}
