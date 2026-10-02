package com.reya.singularityfusion.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.reya.singularityfusion.SingularityFusion;
import com.reya.singularityfusion.gui.Layouts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The cosmos the fusion core's screen and JEI's fusion page are drawn with. Sprites from the GUI's sheets are laid out
 * in batches, either painted over what is there (their alpha) or added to it as light (their colour); on that, space
 * with drifting nebulae, twinkling and shooting stars, galaxies, planets lit by the singularity, the singularity itself
 * (the far half of its disk, its shadow, the bent light, the near half, the photon ring, sparks falling in), and
 * streams of matter curving into it the way its disk turns.
 */
public final class Cosmos2D {
    public static final ResourceLocation FRAME = gui("fusion_frame"), SPACE = gui("fusion_space"), WIDGETS = gui("fusion_widgets"),
            PLANETS = Fx.effect("planets");
    private static final ResourceLocation DISK = Fx.effect("disk"), RING = Fx.effect("ring"), GLOW = Fx.effect("glow"), SPARK = Fx.effect("spark"),
            SHOCK = Fx.effect("shock");
    /** The singularity's disk: how flat it looks (the sine of its tilt to the eye), its edges in radii of its shadow. */
    public static final float SQUASH = Mth.sin(0.24F), DISK_IN = 1.55F, DISK_OUT = 3.3F;
    public static final float TWO_PI = 2.0F * Fx.PI;

    /** How a batch is laid on: a texture painted over, a texture added as light, or untextured light or paint. */
    private enum Kind { PAINT, LIGHT, GLOW, SHADE }

    private static Kind kind = Kind.PAINT;
    private static ResourceLocation texture;
    private static float tintR = 1.0F, tintG = 1.0F, tintB = 1.0F, tintA = 1.0F;
    private static Matrix4f matrix = new Matrix4f();
    private static BufferBuilder building;

    private static ResourceLocation gui(String name) {
        return new ResourceLocation(SingularityFusion.MODID, "textures/gui/" + name + ".png");
    }

    // ------------------------------------------------------------------ batches

    /** Starts painting sprites of `tex` over what is there, tinted (r, g, b) and faded to `a`. */
    public static void paint(GuiGraphics g, ResourceLocation tex, float r, float gr, float b, float a) {
        start(g, Kind.PAINT, tex);
        tintR = r;
        tintG = gr;
        tintB = b;
        tintA = a;
    }

    public static void paint(GuiGraphics g, ResourceLocation tex) {
        paint(g, tex, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Starts adding sprites of `tex` as light, each in its own colour. */
    public static void light(GuiGraphics g, ResourceLocation tex) {
        start(g, Kind.LIGHT, tex);
    }

    /** Starts adding untextured light: lines, rectangles. */
    public static void glow(GuiGraphics g) {
        start(g, Kind.GLOW, null);
    }

    /** Starts painting untextured colour over what is there, with its alpha. */
    public static void shade(GuiGraphics g) {
        start(g, Kind.SHADE, null);
    }

    private static void start(GuiGraphics g, Kind k, ResourceLocation tex) {
        end();
        g.flush();
        kind = k;
        texture = tex;
        matrix = new Matrix4f(g.pose().last().pose());
    }

    private static BufferBuilder builder() {
        if (building == null) {
            building = Tesselator.getInstance().getBuilder();
            building.begin(VertexFormat.Mode.QUADS, switch (kind) {
                case PAINT -> DefaultVertexFormat.POSITION_TEX;
                case LIGHT -> DefaultVertexFormat.POSITION_TEX_COLOR;
                default -> DefaultVertexFormat.POSITION_COLOR;
            });
        }
        return building;
    }

    /** Draws what has been laid out since the batch started (nothing if nothing was), and leaves blending as it found it. */
    public static void end() {
        if (building == null) return;
        BufferBuilder b = building;
        building = null;
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        switch (kind) {
            case PAINT -> {
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderTexture(0, texture);
                RenderSystem.setShaderColor(Fx.c(tintR), Fx.c(tintG), Fx.c(tintB), Fx.c(tintA));
            }
            case LIGHT -> {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, texture);
            }
            case GLOW -> {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
            }
            case SHADE -> {
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------ vertices and sprites

    /** A vertex: its texture's (u, v) and, in a batch of light, its colour (kept within 0 to 1). */
    public static void vertex(float x, float y, float u, float v, float r, float g, float b) {
        BufferBuilder bb = builder();
        switch (kind) {
            case PAINT -> bb.vertex(matrix, x, y, 0.0F).uv(u, v).endVertex();
            case LIGHT -> bb.vertex(matrix, x, y, 0.0F).uv(u, v).color(Fx.c(r), Fx.c(g), Fx.c(b), 1.0F).endVertex();
            default -> bb.vertex(matrix, x, y, 0.0F).color(Fx.c(r), Fx.c(g), Fx.c(b), 1.0F).endVertex();
        }
    }

    /** An untextured vertex with an alpha (a batch of paint). */
    public static void point(float x, float y, float r, float g, float b, float a) {
        builder().vertex(matrix, x, y, 0.0F).color(Fx.c(r), Fx.c(g), Fx.c(b), Fx.c(a)).endVertex();
    }

    /** A rectangle turned by `angle` round its middle (cx, cy), half its size (hw, hh), with the texture's (u1, v1)-(u2, v2) on it. */
    public static void quad(float cx, float cy, float hw, float hh, float angle, float u1, float v1, float u2, float v2, float r, float g, float b) {
        float c = Mth.cos(angle), s = Mth.sin(angle);
        float ax = -hw * c + hh * s, ay = -hw * s - hh * c;     // the top left corner from the middle
        float bx = -hw * c - hh * s, by = -hw * s + hh * c;     // the bottom left
        vertex(cx + ax, cy + ay, u1, v1, r, g, b);
        vertex(cx + bx, cy + by, u1, v2, r, g, b);
        vertex(cx - ax, cy - ay, u2, v2, r, g, b);
        vertex(cx - bx, cy - by, u2, v1, r, g, b);
    }

    /**
     * A sprite: the pixels (x, y, w, h) of a sheet `size` pixels square, centred on (cx, cy), w by h across, turned by
     * `angle`; in a batch of light, in the colour (r, g, b). Its outermost half pixels are left off, so that the
     * sheet's smoothing never draws in its neighbours.
     */
    public static void sprite(int size, int[] px, float cx, float cy, float w, float h, float angle, float r, float g, float b) {
        quad(cx, cy, w * 0.5F, h * 0.5F, angle, (px[0] + 0.5F) / size, (px[1] + 0.5F) / size, (px[0] + px[2] - 0.5F) / size,
                (px[1] + px[3] - 0.5F) / size, r, g, b);
    }

    public static void sprite(int size, int[] px, float cx, float cy, float w, float h, float angle, float k) {
        sprite(size, px, cx, cy, w, h, angle, k, k, k);
    }

    /** Part of a sheet's region, from (fu1, fv1) to (fu2, fv2) of it (fractions), onto the rectangle (x1, y1)-(x2, y2). */
    public static void part(int size, int[] px, float fu1, float fv1, float fu2, float fv2, float x1, float y1, float x2, float y2, float r,
                            float g, float b) {
        float u1 = (px[0] + 0.5F + (px[2] - 1.0F) * fu1) / size, u2 = (px[0] + 0.5F + (px[2] - 1.0F) * fu2) / size;
        float v1 = (px[1] + 0.5F + (px[3] - 1.0F) * fv1) / size, v2 = (px[1] + 0.5F + (px[3] - 1.0F) * fv2) / size;
        vertex(x1, y1, u1, v1, r, g, b);
        vertex(x1, y2, u1, v2, r, g, b);
        vertex(x2, y2, u2, v2, r, g, b);
        vertex(x2, y1, u2, v1, r, g, b);
    }

    /** An untextured rectangle: light (a batch of glow) or paint with an alpha (a batch of shade). */
    public static void rect(float x1, float y1, float x2, float y2, float r, float g, float b, float a) {
        if (kind == Kind.SHADE) {
            point(x1, y1, r, g, b, a);
            point(x1, y2, r, g, b, a);
            point(x2, y2, r, g, b, a);
            point(x2, y1, r, g, b, a);
        } else {
            vertex(x1, y1, 0.0F, 0.0F, r * a, g * a, b * a);
            vertex(x1, y2, 0.0F, 0.0F, r * a, g * a, b * a);
            vertex(x2, y2, 0.0F, 0.0F, r * a, g * a, b * a);
            vertex(x2, y1, 0.0F, 0.0F, r * a, g * a, b * a);
        }
    }

    /** A line of light from (x1, y1) to (x2, y2), `width` across, its colour fading from k1 to k2 along it (a batch of glow). */
    public static void line(float x1, float y1, float x2, float y2, float width, float r, float g, float b, float k1, float k2) {
        float dx = x2 - x1, dy = y2 - y1, len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        float nx = -dy / len * width * 0.5F, ny = dx / len * width * 0.5F;
        vertex(x1 + nx, y1 + ny, 0.0F, 0.0F, r * k1, g * k1, b * k1);
        vertex(x1 - nx, y1 - ny, 0.0F, 0.0F, r * k1, g * k1, b * k1);
        vertex(x2 - nx, y2 - ny, 0.0F, 0.0F, r * k2, g * k2, b * k2);
        vertex(x2 + nx, y2 + ny, 0.0F, 0.0F, r * k2, g * k2, b * k2);
    }

    /**
     * A band of soft light across the middle row of a glow sprite (bright along its middle, fading to its sides):
     * from (x1, y1) to (x2, y2), `width` across (a batch of light on the widgets).
     */
    public static void beam(float x1, float y1, float x2, float y2, float width, float r, float g, float b) {
        int[] bloom = Layouts.W_BLOOM;
        int size = Layouts.WIDGETS_TEX;
        float dx = x2 - x1, dy = y2 - y1, len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        float nx = -dy / len * width * 0.5F, ny = dx / len * width * 0.5F;
        float v = (bloom[1] + bloom[3] * 0.5F) / size, u1 = (bloom[0] + 1.0F) / size, u2 = (bloom[0] + bloom[2] - 1.0F) / size;
        vertex(x1 + nx, y1 + ny, u1, v, r, g, b);
        vertex(x1 - nx, y1 - ny, u2, v, r, g, b);
        vertex(x2 - nx, y2 - ny, u2, v, r, g, b);
        vertex(x2 + nx, y2 + ny, u1, v, r, g, b);
    }

    // ------------------------------------------------------------------ space

    /**
     * Space through a window from (x1, y1) to (x2, y2): the part of the space picture from (sx, sy) on (the picture is
     * the core's screen's window at twice size, so (sx, sy) are in the screen's pixels from the window's corner), and
     * wisps of nebula drifting over it, `lift` how bright.
     */
    public static void space(GuiGraphics g, float x1, float y1, float x2, float y2, float sx, float sy, float time, float lift) {
        int size = Layouts.SPACE_TEX;
        int[] base = Layouts.SPACE_BASE;
        paint(g, SPACE);
        float u1 = (base[0] + 2.0F * sx) / size, v1 = (base[1] + 2.0F * sy) / size;
        float u2 = u1 + 2.0F * (x2 - x1) / size, v2 = v1 + 2.0F * (y2 - y1) / size;
        vertex(x1, y1, u1, v1, 1.0F, 1.0F, 1.0F);
        vertex(x1, y2, u1, v2, 1.0F, 1.0F, 1.0F);
        vertex(x2, y2, u2, v2, 1.0F, 1.0F, 1.0F);
        vertex(x2, y1, u2, v1, 1.0F, 1.0F, 1.0F);
        light(g, SPACE);
        wisps(x1, y1, x2, y2, time * 2.4F + sx, 1.0F, false, 0.34F * lift, 0.3F * lift, 0.4F * lift);
        wisps(x1, y1, x2, y2, -time * 1.5F + 97.0F + sx, 1.5F, true, 0.2F * lift, 0.13F * lift, 0.3F * lift);
        end();
    }

    /** A layer of the wisps across a window, its picture `scale` times its size, `offset` pixels along (it repeats round). */
    private static void wisps(float x1, float y1, float x2, float y2, float offset, float scale, boolean flip, float r, float g, float b) {
        int size = Layouts.SPACE_TEX;
        int[] w = Layouts.SPACE_WISPS;
        float period = w[2] * 0.5F * scale;
        float t1 = offset / period % 1.0F, t2 = t1 + (x2 - x1) / period;
        // the wisps span the sheet's whole width, so the sheet repeating carries them round
        float u1 = (w[0] + t1 * w[2]) / size, u2 = (w[0] + t2 * w[2]) / size;
        float v1 = (w[1] + 1.0F) / size, v2 = (w[1] + w[3] - 1.0F) / size;
        if (flip) {
            float t = v1;
            v1 = v2;
            v2 = t;
        }
        vertex(x1, y1, u1, v1, r, g, b);
        vertex(x1, y2, u1, v2, r, g, b);
        vertex(x2, y2, u2, v2, r, g, b);
        vertex(x2, y1, u2, v1, r, g, b);
    }

    /** Stars twinkling in a window: `count` of them, placed by `seed`, each in its own time; `k` how bright. */
    public static void twinkles(GuiGraphics g, float x1, float y1, float x2, float y2, int count, int seed, float time, float k) {
        light(g, PLANETS);
        int[] glint = Layouts.P_GLINT;
        for (int i = 0; i < count; i++) {
            float x = Mth.lerp(Fx.hash(i, seed, 1), x1 + 3.0F, x2 - 3.0F), y = Mth.lerp(Fx.hash(i, seed, 2), y1 + 3.0F, y2 - 3.0F);
            float s = Mth.sin(time * (0.5F + 1.3F * Fx.hash(i, seed, 3)) + TWO_PI * Fx.hash(i, seed, 4));
            float flash = s > 0.0F ? s * s * s : 0.0F;
            float b = k * (0.12F + 0.88F * flash);
            float size = (3.0F + 5.0F * Fx.hash(i, seed, 5)) * (0.65F + 0.35F * flash);
            int tint = (int) (Fx.hash(i, seed, 6) * 4.0F);
            float r = tint == 2 ? 1.0F : tint == 1 ? 0.78F : tint == 3 ? 0.9F : 1.0F;
            float gr = tint == 2 ? 0.88F : tint == 1 ? 0.86F : tint == 3 ? 0.8F : 1.0F;
            float bl = tint == 2 ? 0.72F : 1.0F;
            sprite(Layouts.PLANETS_TEX, glint, x, y, size, size, 0.0F, r * b, gr * b, bl * b);
        }
        end();
    }

    /** Now and then a shooting star across a window, from its upper half down. */
    public static void shootingStars(GuiGraphics g, float x1, float y1, float x2, float y2, float time, float k, int seed) {
        light(g, PLANETS);
        int[] streak = Layouts.P_STREAK;
        for (int slot = 0; slot < 2; slot++) {
            float period = slot == 0 ? 6.3F : 9.7F;
            float clock = time + slot * 3.1F;
            int n = Mth.floor(clock / period);
            float t = (clock - n * period) / 1.1F;
            if (t >= 1.0F) continue;
            float sx = Mth.lerp(Fx.hash(n, slot, seed), x1 + 24.0F, x2 - 24.0F), sy = Mth.lerp(Fx.hash(n, slot, seed + 1), y1 + 6.0F, (y1 + y2) * 0.5F);
            float angle = 0.3F + 0.4F * Fx.hash(n, slot, seed + 3);
            float dx = (Fx.hash(n, slot, seed + 2) < 0.5F ? 1.0F : -1.0F) * Mth.cos(angle), dy = Mth.sin(angle);
            float travel = (55.0F + 45.0F * Fx.hash(n, slot, seed + 4)) * t;
            float fade = Mth.sin(t * Fx.PI) * k, len = 22.0F + 14.0F * t;
            float hx = sx + dx * travel, hy = sy + dy * travel;
            // the sprite's head is at its right: its middle half a length behind the head, turned along the flight
            sprite(Layouts.PLANETS_TEX, streak, hx - dx * len * 0.5F, hy - dy * len * 0.5F, len, 3.0F, (float) Math.atan2(dy, dx), 0.92F * fade,
                    0.88F * fade, fade);
        }
        end();
    }

    // ------------------------------------------------------------------ planets

    /**
     * A planet `size` across at (cx, cy), lit from (lx, ly) (the singularity) as brightly as `lit`: the glow of its air
     * in (ar, ag, ab), its body (darkened to `dim` when it is far), the night on its far side and the light on its rim
     * turned toward the light; faded to `alpha`.
     */
    public static void planet(GuiGraphics g, int[] body, float cx, float cy, float size, float lx, float ly, float lit, float alpha, float dim,
                              float ar, float ag, float ab) {
        if (alpha <= 0.004F) return;
        float turn = (float) Math.atan2(ly - cy, lx - cx) - Fx.PI;
        float air = alpha * dim * (0.3F + 0.7F * lit);
        light(g, PLANETS);
        sprite(Layouts.PLANETS_TEX, Layouts.P_ATMOS, cx, cy, size * 1.45F, size * 1.45F, turn, ar * air, ag * air, ab * air);
        paint(g, PLANETS, dim, dim, dim, alpha);
        sprite(Layouts.PLANETS_TEX, body, cx, cy, size, size, 0.0F, 1.0F);
        sprite(Layouts.PLANETS_TEX, Layouts.P_SHADE, cx, cy, size, size, turn, 1.0F);
        light(g, PLANETS);
        float rim = alpha * dim * (0.2F + 0.8F * lit);
        sprite(Layouts.PLANETS_TEX, Layouts.P_RIM, cx, cy, size, size, turn, rim, 0.9F * rim, 0.8F * rim);
        end();
    }

    /** A ringed giant: the half of its ring behind it, the planet, the half in front; the ring turned by `tilt`. */
    public static void ringed(GuiGraphics g, float cx, float cy, float size, float tilt, float lx, float ly, float lit, float alpha) {
        if (alpha <= 0.004F) return;
        int[] back = Layouts.P_RING_BACK, front = Layouts.P_RING_FRONT;
        float w = size * back[2] / (float) Layouts.P_GIANT[2], h = w * back[3] / back[2];
        float ox = -Mth.sin(tilt) * h * 0.5F, oy = Mth.cos(tilt) * h * 0.5F;   // half a half's height, down the ring's tilt
        float k = 0.45F + 0.55F * lit;
        paint(g, PLANETS, k, k, k, alpha);
        sprite(Layouts.PLANETS_TEX, back, cx - ox, cy - oy, w, h, tilt, 1.0F);
        end();
        planet(g, Layouts.P_GIANT, cx, cy, size, lx, ly, lit, alpha, 1.0F, 1.0F, 0.75F, 0.5F);
        paint(g, PLANETS, k, k, k, alpha);
        sprite(Layouts.PLANETS_TEX, front, cx + ox, cy + oy, w, h, tilt, 1.0F);
        end();
    }

    /** A spiral galaxy, its light (r, g, b), `size` across, turned by `angle`. */
    public static void galaxy(GuiGraphics g, float cx, float cy, float size, float angle, float r, float gr, float b) {
        light(g, PLANETS);
        sprite(Layouts.PLANETS_TEX, Layouts.P_GALAXY, cx, cy, size, size * 0.62F, angle, r, gr, b);
        end();
    }

    // ------------------------------------------------------------------ the singularity

    /**
     * The singularity at (cx, cy), its shadow `full` across when the core is full and as big as its `charge`, shrunk
     * as it `collapse`s: glows warm round the disk and violet round it all (no wider than `maxGlow`), the far half of
     * its disk, the shadow over it, the far side's light bent over and under the shadow, the disk's near half across
     * it, the photon ring, and sparks spiralling in. Its disk has turned `spin` (from 0 to 8, round); `ticks` is the
     * time for the sparks, `sparks` more of them (a fusion).
     */
    public static void hole(GuiGraphics g, float cx, float cy, float full, float charge, float collapse, float spin, float ticks, float sparks,
                            float maxGlow) {
        float radius = full * charge * (1.0F - 0.4F * collapse);
        if (radius < 0.05F) return;
        float bright = Fx.smooth(0.0F, 0.2F, charge) * (0.75F + 0.25F * charge) * (1.0F + 0.7F * collapse);

        light(g, GLOW);
        float aura = Fx.smooth(0.12F, 0.5F, charge) * 0.55F, k = bright * 0.42F;
        quad(cx, cy, Math.min(maxGlow * 0.8F, 3.4F * radius + 3.0F), Math.min(maxGlow * 0.8F, 3.4F * radius + 3.0F) * 0.62F, 0.0F, 0.0F, 0.0F, 1.0F,
                1.0F, 0.45F * k, 0.29F * k, 0.15F * k);
        float s = Math.min(maxGlow, 4.6F * radius + 9.0F);
        quad(cx, cy, s, s, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.3F * aura, 0.09F * aura, 0.52F * aura);

        light(g, DISK);
        disk(cx, cy, radius, bright, spin, 0.0F);

        shade(g);
        int segments = 40;
        for (int j = 0; j < segments; j++) {
            float a0 = TWO_PI * j / segments, a1 = TWO_PI * (j + 1) / segments;
            point(cx, cy, 0.0F, 0.0F, 0.0F, 1.0F);
            point(cx + radius * Mth.cos(a1), cy + radius * Mth.sin(a1), 0.0F, 0.0F, 0.0F, 1.0F);
            point(cx + radius * Mth.cos(a0), cy + radius * Mth.sin(a0), 0.0F, 0.0F, 0.0F, 1.0F);
            point(cx + radius * Mth.cos(a0), cy + radius * Mth.sin(a0), 0.0F, 0.0F, 0.0F, 1.0F);
        }

        light(g, DISK);
        // the far side's light bent over the shadow's top and, fainter, under it; then the disk's near half
        annulus(cx, cy, 1.04F * radius, 1.5F * radius, 0.0F, Fx.PI, 24, 1.5F, spin, 0.0F, 0.62F, bright * 0.95F, false);
        annulus(cx, cy, 1.03F * radius, 1.24F * radius, Fx.PI, TWO_PI, 24, 1.5F, spin, 0.0F, 0.4F, bright * 0.5F, true);
        disk(cx, cy, radius, bright, spin, Fx.PI);
        light(g, RING);
        annulus(cx, cy, 0.985F * radius, 1.12F * radius, 0.0F, TWO_PI, 48, 4.0F, spin * 0.5F, 0.0F, 1.0F, bright * 1.05F, false);

        float more = Fx.smooth(0.25F, 0.7F, charge) + sparks;
        int count = Math.min(30, (int) (22.0F * more));
        if (count > 0) {
            light(g, SPARK);
            for (int i = 0; i < count; i++) {
                float period = 70.0F + 60.0F * Fx.hash(i, 1, 9);
                float clock = ticks + period * Fx.hash(i, 2, 9);
                int cycle = (int) (clock / period);
                float life = clock / period - cycle;
                float r0 = radius * (2.1F + 1.4F * Fx.hash(i, cycle, 3));
                float at = r0 + (radius * 1.02F - r0) * (float) Math.pow(life, 1.6D);
                float angle = Fx.hash(i, cycle, 4) * TWO_PI - life * (2.5F + 2.0F * Fx.hash(i, cycle, 5)) * Fx.PI;
                float px = cx + at * Mth.cos(angle), py = cy - at * Mth.sin(angle) * SQUASH;
                if (Mth.sin(angle) > 0.0F && Math.abs(px - cx) < radius) continue;     // behind the shadow
                float fade = Fx.smooth(0.0F, 0.15F, life) * (1.0F - Fx.smooth(0.85F, 1.0F, life)) * Math.min(1.0F, bright * 1.4F);
                boolean hot = Fx.hash(i, cycle, 8) < 0.6F;
                float size = 1.2F + 0.8F * Fx.hash(i, cycle, 7);
                quad(px, py, size, size, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, (hot ? 1.0F : 0.68F) * fade, (hot ? 0.74F : 0.38F) * fade,
                        (hot ? 0.42F : 1.0F) * fade);
            }
        }
        end();
    }

    /** Half the disk: the far half (from 0) or the near (from pi), in three bands turning at their speeds (a batch of light on the disk). */
    private static void disk(float cx, float cy, float radius, float bright, float spin, float from) {
        float[] speeds = {1.0F, 0.625F, 0.375F};
        for (int band = 0; band < 3; band++) {
            float v0 = band / 3.0F, v1 = (band + 1) / 3.0F;
            float r0 = radius * Mth.lerp(v0, DISK_IN, DISK_OUT), r1 = radius * Mth.lerp(v1, DISK_IN, DISK_OUT);
            int segments = 24;
            float phase = spin * speeds[band] % 1.0F;
            for (int j = 0; j < segments; j++) {
                float a0 = from + Fx.PI * j / segments, a1 = from + Fx.PI * (j + 1) / segments;
                float u0 = a0 / TWO_PI * 3.0F + phase, u1 = a1 / TWO_PI * 3.0F + phase;
                // brighter on the side coming toward the eye, and on the near side
                float k0 = bright * (1.0F + 0.3F * Mth.cos(a0)) * (1.0F + 0.25F * Math.max(0.0F, -Mth.sin(a0)));
                float k1 = bright * (1.0F + 0.3F * Mth.cos(a1)) * (1.0F + 0.25F * Math.max(0.0F, -Mth.sin(a1)));
                hot(cx + r0 * Mth.cos(a0), cy - r0 * Mth.sin(a0) * SQUASH, u0, v0, k0);
                hot(cx + r0 * Mth.cos(a1), cy - r0 * Mth.sin(a1) * SQUASH, u1, v0, k1);
                hot(cx + r1 * Mth.cos(a1), cy - r1 * Mth.sin(a1) * SQUASH, u1, v1, k1);
                hot(cx + r1 * Mth.cos(a0), cy - r1 * Mth.sin(a0) * SQUASH, u0, v1, k0);
            }
        }
    }

    /** A ring (or part of one) round (cx, cy), up being angle pi/2: u round it (`repeat` times a turn), v from in to out. */
    private static void annulus(float cx, float cy, float in, float out, float from, float to, int segments, float repeat, float phase, float vIn,
                                float vOut, float k, boolean backwards) {
        for (int j = 0; j < segments; j++) {
            float a0 = Mth.lerp(j / (float) segments, from, to), a1 = Mth.lerp((j + 1) / (float) segments, from, to);
            float t0 = (a0 - from) / TWO_PI * repeat, t1 = (a1 - from) / TWO_PI * repeat;
            float u0 = (backwards ? -t0 : t0) + phase % 1.0F, u1 = (backwards ? -t1 : t1) + phase % 1.0F;
            hot(cx + in * Mth.cos(a0), cy - in * Mth.sin(a0), u0, vIn, k);
            hot(cx + in * Mth.cos(a1), cy - in * Mth.sin(a1), u1, vIn, k);
            hot(cx + out * Mth.cos(a1), cy - out * Mth.sin(a1), u1, vOut, k);
            hot(cx + out * Mth.cos(a0), cy - out * Mth.sin(a0), u0, vOut, k);
        }
    }

    /** A vertex of the disk's light: white-hot, the texture giving its colour. */
    private static void hot(float x, float y, float u, float v, float k) {
        vertex(x, y, u, v, k, 0.94F * k, 0.88F * k);
    }

    /** The flash as a fusion ends, `t` from 0 to 1 through it: light flooding out of the singularity. */
    public static void flash(GuiGraphics g, float cx, float cy, float t, float reach) {
        if (t < 0.0F || t >= 1.0F) return;
        float k = (1.0F - t) * (1.0F - t);
        light(g, GLOW);
        float s = 12.0F + reach * (float) Math.sqrt(t);
        quad(cx, cy, s, s, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.6F * k, 1.45F * k, 1.3F * k);
        quad(cx, cy, s * 0.4F, s * 0.4F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 2.0F * k, 2.0F * k, 2.0F * k);
        end();
    }

    /** The shock wave as a fusion ends, `t` from 0 to 1 through it: a ring of light racing out, flattened like the disk. */
    public static void shock(GuiGraphics g, float cx, float cy, float t, float reach, float flat) {
        if (t < 0.0F || t >= 1.0F) return;
        float out = 6.0F + reach * (1.0F - (float) Math.pow(1.0F - t, 2.2D));
        float in = Math.max(0.0F, out - 3.0F - 9.0F * t), k = (float) Math.pow(1.0F - t, 1.6D);
        light(g, SHOCK);
        int segments = 64;
        for (int j = 0; j < segments; j++) {
            float a0 = TWO_PI * j / segments, a1 = TWO_PI * (j + 1) / segments;
            float u0 = j / (float) segments * 6.0F, u1 = (j + 1) / (float) segments * 6.0F;
            vertex(cx + out * Mth.cos(a0), cy + out * Mth.sin(a0) * flat, u0, 0.0F, k, k, k);
            vertex(cx + in * Mth.cos(a0), cy + in * Mth.sin(a0) * flat, u0, 1.0F, k, k, k);
            vertex(cx + in * Mth.cos(a1), cy + in * Mth.sin(a1) * flat, u1, 1.0F, k, k, k);
            vertex(cx + out * Mth.cos(a1), cy + out * Mth.sin(a1) * flat, u1, 0.0F, k, k, k);
        }
        end();
    }

    // ------------------------------------------------------------------ matter falling in

    /**
     * Where a stream from (sx, sy) into the singularity at (hx, hy) is, `t` (0 to 1) along it: it curves round the
     * singularity the way its disk turns (clockwise), `bend` how much.
     */
    public static float[] along(float sx, float sy, float hx, float hy, float bend, float t) {
        float dx = hx - sx, dy = hy - sy;
        float mx = (sx + hx) * 0.5F + dy * bend, my = (sy + hy) * 0.5F - dx * bend;
        float a = (1.0F - t) * (1.0F - t), b = 2.0F * (1.0F - t) * t, c = t * t;
        return new float[] {a * sx + b * mx + c * hx, a * sy + b * my + c * hy};
    }

    /**
     * A stream of matter from (sx, sy) into the singularity at (hx, hy), ending `stop` from its middle: a faint thread
     * of light along it and `count` motes running down it, faster as they fall, in the colour (r, g, b) as bright as
     * `k` (a batch is started on the widgets' sheet and ended).
     */
    public static void stream(GuiGraphics g, float sx, float sy, float hx, float hy, float bend, float stop, float time, float speed, int seed, int count,
                              float k, float r, float gr, float b) {
        if (k <= 0.004F) return;
        float total = Mth.sqrt((hx - sx) * (hx - sx) + (hy - sy) * (hy - sy));
        float end = Mth.clamp(1.0F - stop / Math.max(1.0F, total), 0.05F, 1.0F);
        glow(g);
        int pieces = 14;
        float[] last = along(sx, sy, hx, hy, bend, 0.0F);
        for (int j = 1; j <= pieces; j++) {
            float t = end * j / pieces;
            float[] at = along(sx, sy, hx, hy, bend, t);
            float k0 = 0.28F * k * Fx.smooth(0.0F, 0.15F, end * (j - 1) / pieces), k1 = 0.28F * k * Fx.smooth(0.0F, 0.15F, t);
            line(last[0], last[1], at[0], at[1], 1.0F, r, gr, b, k0, k1);
            last = at;
        }
        light(g, WIDGETS);
        int[] sparkle = Layouts.W_SPARKLE;
        for (int j = 0; j < count; j++) {
            float life = (time * speed + j / (float) count + Fx.hash(seed, j, 11)) % 1.0F;
            float t = end * life * life * (0.6F + 0.4F * life);
            float[] at = along(sx, sy, hx, hy, bend, t);
            float fade = k * Fx.smooth(0.0F, 0.12F, life) * (1.0F - Fx.smooth(0.82F, 1.0F, life));
            float size = 4.2F - 2.0F * life;
            float hot = 0.4F + 0.6F * life;
            sprite(Layouts.WIDGETS_TEX, sparkle, at[0], at[1], size, size, 0.0F, Mth.lerp(hot, r, 1.0F) * fade, Mth.lerp(hot, gr, 0.95F) * fade,
                    Mth.lerp(hot, b, 0.9F) * fade);
        }
        end();
    }

    private Cosmos2D() {
    }
}
