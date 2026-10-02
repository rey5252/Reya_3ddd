package com.reya.singularityfusion.client;

import com.reya.singularityfusion.gui.Layouts;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * JEI's page for a fusion, in the core's screen's style: a window onto space in a gilded frame, the singularity
 * turning behind the catalyst in a ring of gold, the pylons' ingredients in portholes on an orbit round it (light
 * running round the orbit, streams of matter falling from each into the singularity), a shimmering arrow to the result
 * in its porthole, the energy and the time along the bottom. The recipe's items are drawn over it by JEI.
 */
public final class FusionPage {
    private static final int TEX = Layouts.WIDGETS_TEX;
    private static final int ENERGY = 0xFFE2C4FF, TIME = 0xFFB8AED6;

    /** Where the ingredient i of n sits (its item's top left): round the orbit, from the top, clockwise. */
    public static int[] slot(int i, int n) {
        double a = -Math.PI / 2.0D + 2.0D * Math.PI * i / Math.max(1, n);
        return new int[] {(int) Math.round(Layouts.JEI_CENTER[0] + Layouts.JEI_RING_R * Math.cos(a)) - 8,
                (int) Math.round(Layouts.JEI_CENTER[1] + Layouts.JEI_RING_R * Math.sin(a)) - 8};
    }

    /** Draws the page for a fusion of `ingredients` ingredients, its energy and time written along the bottom. */
    public static void draw(GuiGraphics g, int ingredients, Component energy, Component time) {
        float clock = (Util.getMillis() % 3_600_000L) / 1000.0F;
        int w = Layouts.JEI[0], h = Layouts.JEI[1];
        float cx = Layouts.JEI_CENTER[0], cy = Layouts.JEI_CENTER[1];

        // the window (clipped where it is on the screen: the clip doesn't follow JEI's placing of the page)
        Matrix4f m = g.pose().last().pose();
        g.enableScissor(Math.round(m.m30() + m.m00() * 4.0F), Math.round(m.m31() + m.m11() * 4.0F), Math.round(m.m30() + m.m00() * (w - 4.0F)),
                Math.round(m.m31() + m.m11() * (h - 4.0F)));
        // the screen's picture of space, the singularity's place over the catalyst
        float sx = Layouts.HOLE[0] - Layouts.WINDOW[0] - cx, sy = Layouts.HOLE[1] - Layouts.WINDOW[1] - cy;
        Cosmos2D.space(g, 0.0F, 0.0F, w, h, sx, sy, clock, 0.85F);
        Cosmos2D.galaxy(g, 136.0F, 20.0F, 18.0F, clock * 0.03F + 0.8F, 0.5F, 0.5F, 0.62F);
        Cosmos2D.twinkles(g, 0.0F, 0.0F, w, h, 16, 7, clock, 1.0F);
        Cosmos2D.shootingStars(g, 0.0F, 0.0F, w, h, clock + 2.0F, 0.8F, 9);
        orbit(g, cx, cy, clock);
        float charge = 0.78F + 0.04F * Mth.sin(clock * 0.8F), radius = 9.5F;
        Cosmos2D.hole(g, cx, cy, radius, charge, 0.0F, clock * 0.28F % 8.0F, clock * 20.0F, 0.25F, 46.0F);
        for (int i = 0; i < ingredients; i++) {
            int[] at = slot(i, ingredients);
            float ix = at[0] + 8.0F, iy = at[1] + 8.0F, dx = cx - ix, dy = cy - iy, len = Math.max(1.0F, Mth.sqrt(dx * dx + dy * dy));
            Cosmos2D.stream(g, ix + dx / len * 11.0F, iy + dy / len * 11.0F, cx, cy, 0.25F, radius * charge * 1.15F, clock + i * 0.37F, 0.35F, i, 5, 1.0F,
                    0.72F, 0.48F, 1.0F);
        }
        g.disableScissor();
        Cosmos2D.paint(g, Cosmos2D.WIDGETS);
        Cosmos2D.sprite(TEX, Layouts.W_JEI_FRAME, w * 0.5F, h * 0.5F, w, h, 0.0F, 1.0F);
        Cosmos2D.end();

        // the portholes, glowing; the gold ring round the catalyst; the arrow, shimmering
        float ox = Layouts.JEI_OUTPUT[0] + 8.0F, oy = Layouts.JEI_OUTPUT[1] + 8.0F;
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        for (int i = 0; i < ingredients; i++) {
            int[] at = slot(i, ingredients);
            float k = 0.22F + 0.1F * Mth.sin(clock * 2.0F + i);
            Cosmos2D.sprite(TEX, Layouts.W_BLOOM, at[0] + 8.0F, at[1] + 8.0F, 30.0F, 30.0F, 0.0F, 0.55F * k, 0.3F * k, k);
        }
        float pulse = 0.35F + 0.15F * Mth.sin(clock * 3.0F);
        Cosmos2D.sprite(TEX, Layouts.W_BLOOM, ox, oy, 34.0F, 34.0F, 0.0F, pulse, 0.75F * pulse, 0.35F * pulse);
        Cosmos2D.paint(g, Cosmos2D.WIDGETS);
        for (int i = 0; i < ingredients; i++) {
            int[] at = slot(i, ingredients);
            Cosmos2D.sprite(TEX, Layouts.W_PORT_VIOLET, at[0] + 8.0F, at[1] + 8.0F, Layouts.PORTHOLE, Layouts.PORTHOLE, 0.0F, 1.0F);
        }
        Cosmos2D.sprite(TEX, Layouts.W_PORT_GOLD, ox, oy, Layouts.PORTHOLE, Layouts.PORTHOLE, 0.0F, 1.0F);
        Cosmos2D.sprite(TEX, Layouts.W_ORB_RING, cx, cy, 26.0F, 26.0F, clock * 0.5F, 1.0F);
        float ax = Layouts.JEI_ARROW[0], ay = Layouts.JEI_ARROW[1], aw = 22.0F, ah = 15.0F;
        Cosmos2D.sprite(TEX, Layouts.W_ARROW, ax + aw * 0.5F, ay + ah * 0.5F, aw, ah, 0.0F, 1.0F);
        Cosmos2D.light(g, Cosmos2D.WIDGETS);
        float shine = clock / 1.8F % 1.0F * 1.6F - 0.3F;
        for (int j = 0; j < 8; j++) {
            float f1 = j / 8.0F, f2 = (j + 1) / 8.0F, k = (float) Math.exp(-Math.pow(((f1 + f2) * 0.5F - shine) / 0.12F, 2.0D)) * 0.7F;
            if (k > 0.01F) Cosmos2D.part(TEX, Layouts.W_ARROW, f1, 0.0F, f2, 1.0F, ax + aw * f1, ay, ax + aw * f2, ay + ah, k, k, k);
        }
        Cosmos2D.end();

        Font font = Minecraft.getInstance().font;
        g.drawString(font, energy, 7, Layouts.JEI_TEXT_Y, ENERGY, true);
        g.drawString(font, time, w - 7 - font.width(time), Layouts.JEI_TEXT_Y, TIME, true);
    }

    /** The orbit the ingredients sit on: pulses of light running round it, a fainter ring inside it turning the other way. */
    private static void orbit(GuiGraphics g, float cx, float cy, float time) {
        Cosmos2D.glow(g);
        int segments = 96;
        float r = Layouts.JEI_RING_R, inner = r - 9.0F;
        for (int j = 0; j < segments; j++) {
            float a0 = Cosmos2D.TWO_PI * j / segments, a1 = Cosmos2D.TWO_PI * (j + 1) / segments;
            float k = 0.07F + 0.09F * (0.5F + 0.5F * Mth.cos(a0 * 12.0F - time * 1.6F));
            Cosmos2D.line(cx + r * Mth.cos(a0), cy + r * Mth.sin(a0), cx + r * Mth.cos(a1), cy + r * Mth.sin(a1), 1.0F, 0.6F, 0.5F, 1.0F, k, k);
            float q = 0.03F + 0.05F * (0.5F + 0.5F * Mth.cos(a0 * 8.0F + time * 1.1F));
            Cosmos2D.line(cx + inner * Mth.cos(a0), cy + inner * Mth.sin(a0), cx + inner * Mth.cos(a1), cy + inner * Mth.sin(a1), 0.8F, 0.5F, 0.6F, 1.0F,
                    q, q);
        }
        Cosmos2D.end();
    }

    private FusionPage() {
    }
}
