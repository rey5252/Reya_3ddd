package com.reya.starfall.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector3f;

/**
 * A batch of soft light in a {@link Scene3D}: glows that face the camera, beams and streaks with a soft
 * cross-section, rings. They are hidden behind solid things but don't hide anything. Additive batches add light;
 * the others lay soft colour over what is there.
 */
final class Soft {
    private final Scene3D s;
    private final boolean additive, shaded;
    private final BufferBuilder buf;

    Soft(Scene3D scene, boolean additive) {
        this.s = scene;
        this.additive = additive;
        ShaderInstance sh = FilmGfx.soft;
        this.shaded = sh != null;
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        else RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        if (shaded) RenderSystem.setShader(() -> sh);
        else RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, shaded ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
    }

    void end() {
        RenderSystem.setShaderColor(s.fade, s.fade, s.fade, s.fade);
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    private void v(float x, float y, float z, float u, float w, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (shaded) {
            buf.vertex(x, y, z).uv(u, w).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, a).endVertex();
        } else {
            // without the shader there's no soft edge: keep it faint and premultiplied like the shader would
            float k = (shaded ? 1.0F : 0.35F) * a / 255.0F;
            int r = Math.round(((argb >> 16) & 0xFF) * k), g = Math.round(((argb >> 8) & 0xFF) * k), b = Math.round((argb & 0xFF) * k);
            buf.vertex(x, y, z).color(r, g, b, additive ? 255 : Math.round(a * 0.35F)).endVertex();
        }
    }

    // ------------------------------------------------------------------ glows

    /** A glow facing the camera at a world point, {@code radius} in world units. {@code core} adds a hot centre. */
    void glow(Vector3f world, float radius, int argb, boolean core) {
        glowView(s.toView(world), radius, argb, core);
    }

    /** The same, sized as a fraction of the screen's height whatever the distance. */
    void glowScreen(Vector3f world, float frac, int argb, boolean core) {
        Vector3f c = s.toView(world);
        if (c.z > -s.near) return;
        glowView(c, s.viewSize(frac, c.z), argb, core);
    }

    void glowView(Vector3f c, float r, int argb, boolean core) {
        if (c.z > -s.near || ((argb >>> 24) & 0xFF) == 0) return;
        float g = core ? 4.0F : 0.0F;
        v(c.x - r, c.y - r, c.z, g - 1.0F, -1.0F, argb);
        v(c.x + r, c.y - r, c.z, g + 1.0F, -1.0F, argb);
        v(c.x + r, c.y + r, c.z, g + 1.0F, 1.0F, argb);
        v(c.x - r, c.y + r, c.z, g - 1.0F, 1.0F, argb);
    }

    /** A plain soft disc (used where a planet can't be drawn properly). */
    void discView(Vector3f c, float r, int center, int edge) {
        glowView(c, r * 1.15F, center, false);
    }

    /** A flare: a thin streak through a world point across the screen, sizes as fractions of the screen height. */
    void flare(Vector3f world, float length, float width, float angle, int argb) {
        Vector3f c = s.toView(world);
        if (c.z > -s.near) return;
        float l = s.viewSize(length, c.z), w = s.viewSize(width, c.z);
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float nx = -dy * w, ny = dx * w;
        // the profile runs across; along it the streak fades through the vertex colour at its ends
        int edge = argb & 0x00FFFFFF;
        v(c.x - dx * l - nx, c.y - dy * l - ny, c.z, 0.0F, -1.0F, edge);
        v(c.x - nx, c.y - ny, c.z, 0.0F, -1.0F, argb);
        v(c.x + nx, c.y + ny, c.z, 0.0F, 1.0F, argb);
        v(c.x - dx * l + nx, c.y - dy * l + ny, c.z, 0.0F, 1.0F, edge);
        v(c.x - nx, c.y - ny, c.z, 0.0F, -1.0F, argb);
        v(c.x + dx * l - nx, c.y + dy * l - ny, c.z, 0.0F, -1.0F, edge);
        v(c.x + dx * l + nx, c.y + dy * l + ny, c.z, 0.0F, 1.0F, edge);
        v(c.x + nx, c.y + ny, c.z, 0.0F, 1.0F, argb);
    }

    // ------------------------------------------------------------------ beams and streaks

    /** A beam from a to b in the world, facing the camera, widths in world units, fading from ca to cb. */
    void beam(Vector3f a, Vector3f b, float wa, float wb, int ca, int cb) {
        beamView(s.toView(a), s.toView(b), wa, wb, ca, cb, false);
    }

    /** The same, with widths as fractions of the screen's height (a line that stays thin however close). */
    void line(Vector3f a, Vector3f b, float fa, float fb, int ca, int cb) {
        beamView(s.toView(a), s.toView(b), fa, fb, ca, cb, true);
    }

    void beamView(Vector3f a, Vector3f b, float wa, float wb, int ca, int cb, boolean screen) {
        float lim = -s.near * 1.5F;
        if (a.z > lim && b.z > lim) return;
        // clip the part behind the camera
        if (a.z > lim || b.z > lim) {
            float t = (lim - a.z) / (b.z - a.z);
            Vector3f m = new Vector3f(a).lerp(b, t);
            int cm = Fx.mix(ca, cb, t);
            if (a.z > lim) {
                a = m;
                ca = cm;
            } else {
                b = m;
                cb = cm;
            }
        }
        if (screen) {
            wa = s.viewSize(wa, a.z);
            wb = s.viewSize(wb, b.z);
        }
        Vector3f d = new Vector3f(b).sub(a);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5F);
        Vector3f side = d.cross(mid, new Vector3f());
        if (side.lengthSquared() < 1.0E-12F) return;
        side.normalize();
        v(a.x - side.x * wa, a.y - side.y * wa, a.z - side.z * wa, 0.0F, -1.0F, ca);
        v(b.x - side.x * wb, b.y - side.y * wb, b.z - side.z * wb, 0.0F, -1.0F, cb);
        v(b.x + side.x * wb, b.y + side.y * wb, b.z + side.z * wb, 0.0F, 1.0F, cb);
        v(a.x + side.x * wa, a.y + side.y * wa, a.z + side.z * wa, 0.0F, 1.0F, ca);
    }

    /** A ring in the plane through {@code center} with the two axes, as a soft band of {@code width}. */
    void ring(Vector3f center, Vector3f u, Vector3f w, float radius, float width, int argb, int segments) {
        ring(center, u, w, radius, width, argb, segments, 0.0F, (float) (Math.PI * 2.0D));
    }

    void ring(Vector3f center, Vector3f u, Vector3f w, float radius, float width, int argb, int segments, float a0, float a1) {
        Vector3f eu = new Vector3f(u).normalize(), ew = new Vector3f(w).normalize();
        for (int i = 0; i < segments; i++) {
            float t0 = a0 + (a1 - a0) * i / segments, t1 = a0 + (a1 - a0) * (i + 1) / segments;
            Vector3f d0 = new Vector3f(eu).mul((float) Math.cos(t0)).add(new Vector3f(ew).mul((float) Math.sin(t0)));
            Vector3f d1 = new Vector3f(eu).mul((float) Math.cos(t1)).add(new Vector3f(ew).mul((float) Math.sin(t1)));
            Vector3f i0 = s.toView(new Vector3f(d0).mul(radius - width).add(center));
            Vector3f o0 = s.toView(new Vector3f(d0).mul(radius + width).add(center));
            Vector3f i1 = s.toView(new Vector3f(d1).mul(radius - width).add(center));
            Vector3f o1 = s.toView(new Vector3f(d1).mul(radius + width).add(center));
            if (i0.z > -s.near || o0.z > -s.near || i1.z > -s.near || o1.z > -s.near) continue;
            v(i0.x, i0.y, i0.z, 0.0F, -1.0F, argb);
            v(i1.x, i1.y, i1.z, 0.0F, -1.0F, argb);
            v(o1.x, o1.y, o1.z, 0.0F, 1.0F, argb);
            v(o0.x, o0.y, o0.z, 0.0F, 1.0F, argb);
        }
    }

    /** A jagged bolt of lightning from a to b (world), redrawn each frame from the seed. */
    void bolt(Vector3f a, Vector3f b, float width, int argb, long seed, float jag, int depth) {
        java.util.Random r = new java.util.Random(seed);
        bolt(r, a, b, width, argb, jag, depth);
    }

    private void bolt(java.util.Random r, Vector3f a, Vector3f b, float width, int argb, float jag, int depth) {
        float len = a.distance(b);
        if (depth <= 0 || len < width * 4.0F) {
            beam(a, b, width, width, argb, argb);
            return;
        }
        Vector3f mid = new Vector3f(a).add(b).mul(0.5F);
        mid.add((r.nextFloat() - 0.5F) * len * jag, (r.nextFloat() - 0.5F) * len * jag, (r.nextFloat() - 0.5F) * len * jag);
        bolt(r, a, mid, width, argb, jag, depth - 1);
        bolt(r, mid, b, width, argb, jag, depth - 1);
        if (depth >= 3 && r.nextFloat() < 0.3F) {
            // a branch off to the side, thinner and dimmer
            Vector3f tip = new Vector3f(mid).add((r.nextFloat() - 0.5F) * len * 0.7F, (r.nextFloat() - 0.5F) * len * 0.7F,
                    (r.nextFloat() - 0.5F) * len * 0.7F);
            bolt(r, mid, tip, width * 0.6F, Fx.scaleAlpha(argb, 0.6F), jag, depth - 2);
        }
    }
}
