package com.reya.alfheimheart.machine.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Smooth light over the machines' pixel-art panels, drawn straight with the GUI's pose: soft round glows, rings
 * and arcs whose colour runs along them, soft beams, gradients, a shine swept over a mask. Additive (the light
 * adds to what is under it, as light does) or ordinarily blended. Colours are ARGB; coordinates the screen's.
 */
public final class Gfx {
    public static final float TAU = (float) (Math.PI * 2.0D);

    /** A colour along a line or round a ring: t runs from 0 to 1 along it. */
    @FunctionalInterface
    public interface Shade {
        int at(float t);
    }

    private static BufferBuilder begin(GuiGraphics g, boolean additive) {
        g.flush();
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    private static void end() {
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0.0F).color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24).endVertex();
    }

    /** The colour with its alpha times k. */
    public static int fade(int argb, float k) {
        int a = Mth.clamp(Math.round((argb >>> 24) * k), 0, 255);
        return a << 24 | argb & 0xFFFFFF;
    }

    public static int argb(int rgb, float alpha) {
        return Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
    }

    /** Between two colours (alpha too). */
    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
            r |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return r;
    }

    /** A colour round the colour wheel (hue in turns), opaque. */
    public static int hue(float hue, float saturation, float value) {
        return 0xFF000000 | Mth.hsvToRgb(((hue % 1.0F) + 1.0F) % 1.0F, saturation, value);
    }

    /** A soft round glow: `inner` at its middle fading to `outer` at radius r. */
    public static void radial(GuiGraphics g, float cx, float cy, float r, int inner, int outer, boolean additive) {
        if (r <= 0.0F) return;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        int n = Mth.clamp((int) (r * 1.6F), 16, 96);
        for (int i = 0; i < n; i++) {
            float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            v(b, m, cx, cy, inner);
            v(b, m, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, outer);
            v(b, m, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, outer);
        }
        end();
    }

    /**
     * A band round (cx, cy) from radius r1 to r2, from angle a0 to a1 (radians, clockwise from the right), its
     * colour along it from `shade`; soft: its edges fade out from the band's middle, else it is solid across.
     */
    public static void arc(GuiGraphics g, float cx, float cy, float r1, float r2, float a0, float a1, Shade shade, boolean soft,
                           boolean additive) {
        if (a1 <= a0 || r2 <= r1) return;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        float mid = (r1 + r2) / 2.0F;
        int n = Mth.clamp((int) ((a1 - a0) * r2 / 2.0F) + 4, 4, 256);
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n;
            float b0 = a0 + (a1 - a0) * t0, b1 = a0 + (a1 - a0) * t1;
            int c0 = shade.at(t0), c1 = shade.at(t1);
            float x0 = Mth.cos(b0), y0 = Mth.sin(b0), x1 = Mth.cos(b1), y1 = Mth.sin(b1);
            if (soft) {
                band(b, m, cx, cy, x0, y0, x1, y1, r1, mid, fade(c0, 0.0F), c0, fade(c1, 0.0F), c1);
                band(b, m, cx, cy, x0, y0, x1, y1, mid, r2, c0, fade(c0, 0.0F), c1, fade(c1, 0.0F));
            } else {
                band(b, m, cx, cy, x0, y0, x1, y1, r1, r2, c0, c0, c1, c1);
            }
        }
        end();
    }

    /** One piece of a ring: inner and outer colours at each of its two angles. */
    private static void band(BufferBuilder b, Matrix4f m, float cx, float cy, float x0, float y0, float x1, float y1, float ri, float ro,
                             int in0, int out0, int in1, int out1) {
        v(b, m, cx + x0 * ri, cy + y0 * ri, in0);
        v(b, m, cx + x0 * ro, cy + y0 * ro, out0);
        v(b, m, cx + x1 * ro, cy + y1 * ro, out1);
        v(b, m, cx + x0 * ri, cy + y0 * ri, in0);
        v(b, m, cx + x1 * ro, cy + y1 * ro, out1);
        v(b, m, cx + x1 * ri, cy + y1 * ri, in1);
    }

    /** A soft line of light w wide from (x1, y1) to (x2, y2), from colour c1 to c2, its sides fading out. */
    public static void beam(GuiGraphics g, float x1, float y1, float x2, float y2, float w, int c1, int c2, boolean additive) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        float nx = -dy / len * w / 2.0F, ny = dx / len * w / 2.0F;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        quad(b, m, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2, y2, x1, y1, fade(c1, 0.0F), fade(c2, 0.0F), c2, c1);
        quad(b, m, x1, y1, x2, y2, x2 - nx, y2 - ny, x1 - nx, y1 - ny, c1, c2, fade(c2, 0.0F), fade(c1, 0.0F));
        end();
    }

    /**
     * A ribbon of light hanging from a wavering line: the points (xs[i], ys[i]) its lower edge, `colours[i]` its
     * colour there, fading out to nothing `height` above it (an aurora's curtain).
     */
    public static void ribbon(GuiGraphics g, float[] xs, float[] ys, float height, int[] colours, boolean additive) {
        if (xs.length < 2) return;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        for (int i = 0; i + 1 < xs.length; i++) {
            quad(b, m, xs[i], ys[i] - height, xs[i + 1], ys[i + 1] - height, xs[i + 1], ys[i + 1], xs[i], ys[i],
                    fade(colours[i], 0.0F), fade(colours[i + 1], 0.0F), colours[i + 1], colours[i]);
        }
        end();
    }

    /** A rectangle whose four corners have their own colours (top-left, top-right, bottom-right, bottom-left). */
    public static void gradient(GuiGraphics g, float x1, float y1, float x2, float y2, int tl, int tr, int br, int bl, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = begin(g, additive);
        quad(b, m, x1, y1, x2, y1, x2, y2, x1, y2, tl, tr, br, bl);
        end();
    }

    private static void quad(BufferBuilder b, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
                             int ca, int cb, int cc, int cd) {
        v(b, m, ax, ay, ca);
        v(b, m, bx, by, cb);
        v(b, m, cx, cy, cc);
        v(b, m, ax, ay, ca);
        v(b, m, cx, cy, cc);
        v(b, m, dx, dy, cd);
    }

    /**
     * A shine sweeping over a mask texture drawn whole at (x, y), w x h: a soft band slanting across it (along
     * u + v / 2), its middle at `pos` (in pixels along that), `half` wide on each side, `strength` at its middle.
     */
    public static void sheen(GuiGraphics g, ResourceLocation mask, float x, float y, float w, float h, float pos, float half, float strength) {
        if (strength <= 0.0F) return;
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, mask);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[][] rect = {{0, 0}, {w, 0}, {w, h}, {0, h}};
        for (int side = 0; side < 2; side++) {
            float lo = side == 0 ? pos - half : pos, hi = side == 0 ? pos : pos + half;
            float[][] poly = clip(clip(rect, lo, true), hi, false);
            if (poly.length < 3) continue;
            for (int i = 1; i + 1 < poly.length; i++) {
                for (float[] p : new float[][]{poly[0], poly[i], poly[i + 1]}) {
                    float along = p[0] + p[1] * 0.5F;
                    float k = side == 0 ? (along - lo) / half : 1.0F - (along - pos) / half;
                    int a = Mth.clamp(Math.round(255.0F * strength * Mth.clamp(k, 0.0F, 1.0F)), 0, 255);
                    b.vertex(m, x + p[0], y + p[1], 0.0F).uv(p[0] / w, p[1] / h).color(255, 255, 255, a).endVertex();
                }
            }
        }
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    /** The convex polygon cut to where u + v / 2 is above `c` (keepAbove) or below it. */
    private static float[][] clip(float[][] poly, float c, boolean keepAbove) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < poly.length; i++) {
            float[] p = poly[i], q = poly[(i + 1) % poly.length];
            float dp = (p[0] + p[1] * 0.5F - c) * (keepAbove ? 1 : -1), dq = (q[0] + q[1] * 0.5F - c) * (keepAbove ? 1 : -1);
            if (dp >= 0) out.add(p);
            if (dp >= 0 != dq >= 0) {
                float t = dp / (dp - dq);
                out.add(new float[]{p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
            }
        }
        return out.toArray(new float[0][]);
    }

    private Gfx() {
    }
}
