package com.reya.starfall.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Flat drawing for the films: coloured quads in screen space, batched, either blended or glowing. */
final class Canvas {
    final float w, h;
    private final Matrix4f mat;
    private BufferBuilder buf;
    private boolean additive;

    Canvas(Matrix4f mat, float w, float h) {
        this.mat = mat;
        this.w = w;
        this.h = h;
    }

    /** Starts a batch; anything drawn before is flushed first. */
    void mode(boolean glow) {
        flush();
        additive = glow;
        RenderSystem.enableBlend();
        if (glow) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    boolean glowing() {
        return additive;
    }

    void flush() {
        if (buf == null) return;
        BufferUploader.drawWithShader(buf.end());
        buf = null;
    }

    void finish() {
        flush();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    void v(double x, double y, int argb) {
        buf.vertex(mat, (float) x, (float) y, 0.0F)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    void quad(double x0, double y0, int c0, double x1, double y1, int c1, double x2, double y2, int c2, double x3, double y3, int c3) {
        v(x0, y0, c0);
        v(x1, y1, c1);
        v(x2, y2, c2);
        v(x3, y3, c3);
    }

    void rect(double x0, double y0, double x1, double y1, int argb) {
        quad(x0, y0, argb, x0, y1, argb, x1, y1, argb, x1, y0, argb);
    }

    /** Vertical gradient. */
    void gradient(double x0, double y0, double x1, double y1, int top, int bottom) {
        quad(x0, y0, top, x0, y1, bottom, x1, y1, bottom, x1, y0, top);
    }

    /** A straight stroke from a to b, fading from ca to cb. */
    void line(double ax, double ay, double bx, double by, double width, int ca, int cb) {
        double dx = bx - ax, dy = by - ay;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-6D) return;
        double nx = -dy / len * width * 0.5D, ny = dx / len * width * 0.5D;
        quad(ax - nx, ay - ny, ca, ax + nx, ay + ny, ca, bx + nx, by + ny, cb, bx - nx, by - ny, cb);
    }

    /** A tapering stroke: width wa at a, wb at b. */
    void taper(double ax, double ay, double wa, double bx, double by, double wb, int ca, int cb) {
        double dx = bx - ax, dy = by - ay;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-6D) return;
        double ux = -dy / len, uy = dx / len;
        quad(ax - ux * wa * 0.5D, ay - uy * wa * 0.5D, ca, ax + ux * wa * 0.5D, ay + uy * wa * 0.5D, ca,
                bx + ux * wb * 0.5D, by + uy * wb * 0.5D, cb, bx - ux * wb * 0.5D, by - uy * wb * 0.5D, cb);
    }

    /** Filled disc from a centre colour to an edge colour. */
    void disc(double cx, double cy, double r, int center, int edge) {
        int seg = Math.max(12, Math.min(64, (int) (r * 0.6D)));
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2.0D * i / seg, a1 = Math.PI * 2.0D * (i + 1) / seg;
            quad(cx, cy, center, cx, cy, center, cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, edge,
                    cx + Math.cos(a0) * r, cy + Math.sin(a0) * r, edge);
        }
    }

    /** A soft glow: colour in the middle, gone at the edge. */
    void glow(double cx, double cy, double r, int argb) {
        disc(cx, cy, r, argb, argb & 0x00FFFFFF);
    }

    /** An ellipse outline (rx, ry) turned by {@code tilt} radians, from angle a0 to a1. */
    void ellipse(double cx, double cy, double rx, double ry, double tilt, double width, double a0, double a1, int argb) {
        int seg = Math.max(16, (int) (Math.abs(a1 - a0) / (Math.PI * 2.0D) * 96.0D));
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        for (int i = 0; i < seg; i++) {
            double t0 = a0 + (a1 - a0) * i / seg, t1 = a0 + (a1 - a0) * (i + 1) / seg;
            double x0 = Math.cos(t0) * rx, y0 = Math.sin(t0) * ry, x1 = Math.cos(t1) * rx, y1 = Math.sin(t1) * ry;
            line(cx + x0 * ct - y0 * st, cy + x0 * st + y0 * ct, cx + x1 * ct - y1 * st, cy + x1 * st + y1 * ct, width, argb, argb);
        }
    }

    /** A point on that ellipse. */
    static double[] onEllipse(double cx, double cy, double rx, double ry, double tilt, double a) {
        double x = Math.cos(a) * rx, y = Math.sin(a) * ry;
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        return new double[]{cx + x * ct - y * st, cy + x * st + y * ct};
    }

    /** Colour of a sphere's surface at a latitude and longitude (radians), as RGB. */
    interface Surface {
        int color(double lat, double lon, double x, double y, double z);
    }

    /**
     * A lit planet: a polar mesh over the disc, each vertex coloured by the surface under it, shaded by a
     * light from (lx, ly, lz) (screen axes, z towards the viewer), with a thin atmosphere glow at the rim.
     */
    void sphere(double cx, double cy, double r, double spin, double tiltRad, double lx, double ly, double lz,
                Surface surface, int atmosphere) {
        if (r < 0.5D) return;
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll;
        ly /= ll;
        lz /= ll;
        int rings = r < 30 ? 8 : 18, seg = r < 30 ? 20 : 44;
        int[][] col = new int[rings + 1][seg + 1];
        double[][] px = new double[rings + 1][seg + 1], py = new double[rings + 1][seg + 1];
        double ct = Math.cos(tiltRad), st = Math.sin(tiltRad);
        for (int i = 0; i <= rings; i++) {
            // denser near the rim, where the curvature shows
            double rho = Math.sin(Math.PI * 0.5D * i / rings);
            for (int j = 0; j <= seg; j++) {
                double th = Math.PI * 2.0D * j / seg;
                double nx = rho * Math.cos(th), ny = rho * Math.sin(th);
                double nz = Math.sqrt(Math.max(0.0D, 1.0D - rho * rho));
                px[i][j] = cx + nx * r;
                py[i][j] = cy + ny * r;
                // into the planet's own frame: undo the axial tilt, then spin about its axis
                double bx = nx * ct + ny * st, by = -nx * st + ny * ct;
                double lat = Math.asin(Math.max(-1.0D, Math.min(1.0D, -by)));
                double lon = Math.atan2(bx, nz) + spin;
                double sx = Math.cos(lat) * Math.sin(lon), sz = Math.cos(lat) * Math.cos(lon), sy = Math.sin(lat);
                int rgb = surface.color(lat, lon, sx, sy, sz);
                double lambert = Math.max(0.0D, nx * lx + ny * ly + nz * lz);
                double light = 0.06D + 0.94D * lambert;
                double rim = Math.pow(1.0D - nz, 3.0D) * (0.3D + 0.7D * lambert);
                int r8 = (int) Math.min(255, ((rgb >> 16) & 0xFF) * light + ((atmosphere >> 16) & 0xFF) * rim);
                int g8 = (int) Math.min(255, ((rgb >> 8) & 0xFF) * light + ((atmosphere >> 8) & 0xFF) * rim);
                int b8 = (int) Math.min(255, (rgb & 0xFF) * light + (atmosphere & 0xFF) * rim);
                col[i][j] = 0xFF000000 | r8 << 16 | g8 << 8 | b8;
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < seg; j++) {
                quad(px[i][j], py[i][j], col[i][j], px[i + 1][j], py[i + 1][j], col[i + 1][j],
                        px[i + 1][j + 1], py[i + 1][j + 1], col[i + 1][j + 1], px[i][j + 1], py[i][j + 1], col[i][j + 1]);
            }
        }
    }
}
