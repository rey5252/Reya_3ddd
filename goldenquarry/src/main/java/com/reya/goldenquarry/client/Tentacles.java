package com.reya.goldenquarry.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.goldenquarry.GoldenQuarry;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The tentacles round the vacuum chest's GUI and the stars they pull in. The tentacles are drawn
 * frame by frame by tools/gen_tentacles.py (purple glossy back with dark spots, pink underside
 * with round suckers, the tip curled; they wave, the waves running in, and are drawn in and reach
 * out again): one row of 16 frames per tentacle in vacuum_chest_tentacles.png. Their roots are
 * under the frame, which is drawn over them. The stars drift in along them, twinkling, and go in
 * under the frame.
 */
final class Tentacles {
    private static final ResourceLocation SHEET = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/vacuum_chest_tentacles.png");
    private static final ResourceLocation STARS = new ResourceLocation(GoldenQuarry.MODID, "textures/gui/vacuum_chest_stars.png");
    private static final int SIZE = 96, FRAMES = 16, REACH = 40;
    /** Root x, y (GUI pixels), direction out, length: as in tools/gen_tentacles.py, in its order. */
    private static final float[][] TENTACLES;

    static {
        float r = 216 - 1;
        TENTACLES = new float[][]{
                {6, 14, -0.75F, -0.66F, 54}, {34, 13, -0.2F, -0.98F, 40}, {4, 48, -0.98F, -0.2F, 40},
                {4, 104, -0.97F, 0.25F, 46}, {9, 152, -0.8F, 0.6F, 52}, {18, 160, -0.4F, 0.92F, 40},
                {r - 6, 14, 0.75F, -0.66F, 54}, {r - 34, 13, 0.2F, -0.98F, 40}, {r - 4, 48, 0.98F, -0.2F, 40},
                {r - 4, 104, 0.97F, 0.25F, 46}, {r - 9, 152, 0.8F, 0.6F, 52}, {r - 18, 160, 0.4F, 0.92F, 40}};
    }

    void draw(GuiGraphics g, int left, int top) {
        long now = Util.getMillis();
        for (int i = 0; i < TENTACLES.length; i++) {
            float[] tc = TENTACLES[i];
            int frame = (int) ((now / 90L + i * 5L) % FRAMES);
            int x = left + Math.round(tc[0] - (SIZE / 2.0F - REACH * tc[2]));
            int y = top + Math.round(tc[1] - (SIZE / 2.0F - REACH * tc[3]));
            g.blit(SHEET, x, y, frame * SIZE, i * SIZE, SIZE, SIZE, SIZE * FRAMES, SIZE * TENTACLES.length);
        }
        // stars pulled in along the tentacles, speeding up as they go, and fading in out there
        double t = now / 1000.0D;
        for (int k = 0; k < 24; k++) {
            float[] tc = TENTACLES[k % TENTACLES.length];
            double p = (t * 0.45D + k * 0.37D) % 1.0D;
            double side = ((k * 7) % 5 - 2) * 5.0D;
            double sx = tc[0] + tc[2] * tc[4] * 0.95D - tc[3] * side, sy = tc[1] + tc[3] * tc[4] * 0.95D + tc[2] * side;
            double ex = tc[0] - tc[2] * 4.0D, ey = tc[1] - tc[3] * 4.0D;
            double e = Math.pow(p, 1.6D);
            int cx = left + (int) Math.round(sx + (ex - sx) * e), cy = top + (int) Math.round(sy + (ey - sy) * e);
            float alpha = (float) Math.min(1.0D, p / 0.25D);
            int frame = (int) ((now / 110L + k) % 8);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            g.blit(STARS, cx - 4, cy - 4, frame * 9, 0, 9, 9, 72, 9);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
