package com.reya.starfall.client;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A red banner at the top of the screen for anyone standing where a strike is about to land. */
final class Evac {
    static void render(GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || Film.active()) return;
        ClientStrike.Warning w = ClientStrikes.warning(partial);
        if (w == null) return;
        Font font = mc.font;
        float time = (mc.level == null ? 0 : mc.level.getGameTime() % 24000L) + partial;
        boolean on = ((int) (time / 5.0F)) % 2 == 0;
        Component title = Component.translatable("hud.starfall.evac");
        Component line = Component.translatable(w.key(), String.format(Locale.ROOT, "%.1f", w.seconds()));
        int bw = Math.max(font.width(line), font.width(title) * 2) + 60, bh = 34;
        int x = (width - bw) / 2, y = 22;
        g.fill(x, y, x + bw, y + bh, 0xC8140408);
        g.renderOutline(x, y, bw, bh, on ? 0xFFFF3A48 : 0xFF7A1A24);
        // hazard stripes at both ends
        for (int side = 0; side < 2; side++) {
            int sx = side == 0 ? x + 3 : x + bw - 23;
            g.enableScissor(sx, y + 3, sx + 20, y + bh - 3);
            for (int k = -bh; k < 20; k += 6) {
                for (int r = 0; r < bh; r++) {
                    int px = sx + k + r / 2;
                    g.fill(px, y + 3 + r, px + 3, y + 4 + r, ((k / 6) & 1) == 0 ? 0xFFF0C020 : 0xFF141414);
                }
            }
            g.disableScissor();
        }
        g.pose().pushPose();
        g.pose().translate(width / 2.0F - font.width(title), y + 4, 0.0F);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font, title, 0, 0, on ? 0xFFFF4A58 : 0xFFB02A36, false);
        g.pose().popPose();
        g.drawString(font, line, (width - font.width(line)) / 2, y + bh - 11, 0xFFFFD8DC, false);
    }

    private Evac() {
    }
}
