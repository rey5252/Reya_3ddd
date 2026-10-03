package com.reya.starfall.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Immediate-mode drawing of glowing shapes in the world, relative to the camera. Anything further away than
 * the far plane is pulled in along its line of sight, so a star a thousand blocks off still shows where it is.
 */
final class Fx {
    private final BufferBuilder buf;
    private final Matrix4f mat;
    private final Vec3 cam;
    private final double far;
    final Vector3f left, up;

    private Fx(Matrix4f mat, Camera camera, double far) {
        this.buf = Tesselator.getInstance().getBuilder();
        this.mat = mat;
        this.cam = camera.getPosition();
        this.far = far;
        this.left = new Vector3f(camera.getLeftVector());
        this.up = new Vector3f(camera.getUpVector());
    }

    /**
     * Starts a batch. Additive batches glow (black draws nothing); the others are ordinary translucent colour.
     * With {@code xray} the shapes show through the land.
     */
    static Fx begin(Matrix4f mat, Camera camera, double far, boolean additive, boolean xray) {
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (xray) RenderSystem.disableDepthTest();
        else RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Fx fx = new Fx(mat, camera, far);
        fx.buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return fx;
    }

    void end() {
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    void vertex(double x, double y, double z, int argb) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 > far * far) {
            double s = far / Math.sqrt(d2);
            dx *= s;
            dy *= s;
            dz *= s;
        }
        buf.vertex(mat, (float) dx, (float) dy, (float) dz)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    /** An upright tube around (x, z) from y0 to y1; the colour can fade from bottom to top. */
    void cylinder(double x, double z, double y0, double y1, double r, int segments, int bottom, int top) {
        if (r <= 0.0D) return;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments, a1 = Math.PI * 2.0D * (i + 1) / segments;
            double x0 = x + Math.cos(a0) * r, z0 = z + Math.sin(a0) * r;
            double x1 = x + Math.cos(a1) * r, z1 = z + Math.sin(a1) * r;
            vertex(x0, y0, z0, bottom);
            vertex(x1, y0, z1, bottom);
            vertex(x1, y1, z1, top);
            vertex(x0, y1, z0, top);
        }
    }

    /** A flat ring on the ground between radii r0 and r1, with its own colour at each edge. */
    void ring(double x, double y, double z, double r0, double r1, int segments, int inner, int outer) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments, a1 = Math.PI * 2.0D * (i + 1) / segments;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            vertex(x + c0 * r0, y, z + s0 * r0, inner);
            vertex(x + c1 * r0, y, z + s1 * r0, inner);
            vertex(x + c1 * r1, y, z + s1 * r1, outer);
            vertex(x + c0 * r1, y, z + s0 * r1, outer);
        }
    }

    /** A ring whose brightness comes and goes around it, like a dashed line that turns. */
    void dashedRing(double x, double y, double z, double r, double width, int segments, int argb, double phase, int dashes) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments, a1 = Math.PI * 2.0D * (i + 1) / segments;
            double k = 0.5D + 0.5D * Math.sin((a0 + a1) * 0.5D * dashes + phase);
            int c = scaleAlpha(argb, (float) (0.15D + 0.85D * k * k));
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            double ri = r - width * 0.5D, ro = r + width * 0.5D;
            vertex(x + c0 * ri, y, z + s0 * ri, c);
            vertex(x + c1 * ri, y, z + s1 * ri, c);
            vertex(x + c1 * ro, y, z + s1 * ro, c);
            vertex(x + c0 * ro, y, z + s0 * ro, c);
        }
    }

    /** A band facing the camera from a to b, of the given half-width, fading from ca to cb. */
    void ribbon(double ax, double ay, double az, double bx, double by, double bz, double half, int ca, int cb) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) * 0.5D - cam.x, my = (ay + by) * 0.5D - cam.y, mz = (az + bz) * 0.5D - cam.z;
        // side = direction x (to camera)
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0E-9D) return;
        sx *= half / len;
        sy *= half / len;
        sz *= half / len;
        vertex(ax - sx, ay - sy, az - sz, ca);
        vertex(ax + sx, ay + sy, az + sz, ca);
        vertex(bx + sx, by + sy, bz + sz, cb);
        vertex(bx - sx, by - sy, bz - sz, cb);
    }

    /** A flat band lying on the ground from a to b. */
    void groundBand(double ax, double ay, double az, double bx, double by, double bz, double half, int ca, int cb) {
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-9D) return;
        double sx = -dz / len * half, sz = dx / len * half;
        vertex(ax - sx, ay, az - sz, ca);
        vertex(ax + sx, ay, az + sz, ca);
        vertex(bx + sx, by, bz + sz, cb);
        vertex(bx - sx, by, bz - sz, cb);
    }

    /** A square that always faces the camera. */
    void billboard(double x, double y, double z, double size, int argb) {
        double lx = left.x() * size, ly = left.y() * size, lz = left.z() * size;
        double ux = up.x() * size, uy = up.y() * size, uz = up.z() * size;
        vertex(x - lx - ux, y - ly - uy, z - lz - uz, argb);
        vertex(x + lx - ux, y + ly - uy, z + lz - uz, argb);
        vertex(x + lx + ux, y + ly + uy, z + lz + uz, argb);
        vertex(x - lx + ux, y - ly + uy, z - lz + uz, argb);
    }

    /** A soft round glow facing the camera: bright in the middle, gone at the edge. */
    void glow(double x, double y, double z, double size, int argb) {
        int edge = argb & 0x00FFFFFF;
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments, a1 = Math.PI * 2.0D * (i + 1) / segments;
            double c0 = Math.cos(a0) * size, s0 = Math.sin(a0) * size, c1 = Math.cos(a1) * size, s1 = Math.sin(a1) * size;
            vertex(x, y, z, argb);
            vertex(x, y, z, argb);
            vertex(x + left.x() * c0 + up.x() * s0, y + left.y() * c0 + up.y() * s0, z + left.z() * c0 + up.z() * s0, edge);
            vertex(x + left.x() * c1 + up.x() * s1, y + left.y() * c1 + up.y() * s1, z + left.z() * c1 + up.z() * s1, edge);
        }
    }

    /** A square prism standing from y0 to y1, for solid things like Gungnir's needle. */
    void prism(double x, double z, double y0, double y1, double half, int side, int lit) {
        double[][] c = {{x - half, z - half}, {x + half, z - half}, {x + half, z + half}, {x - half, z + half}};
        for (int i = 0; i < 4; i++) {
            double[] a = c[i], b = c[(i + 1) % 4];
            int col = (i & 1) == 0 ? side : lit;
            vertex(a[0], y0, a[1], col);
            vertex(b[0], y0, b[1], col);
            vertex(b[0], y1, b[1], col);
            vertex(a[0], y1, a[1], col);
        }
    }

    /** A box around the camera, used to wash the world out behind the skill menu. */
    void veil(int argb) {
        double s = 1.0D;
        double x = cam.x, y = cam.y, z = cam.z;
        double[][] faces = {
                {-s, -s, -s, s, -s, -s, s, s, -s, -s, s, -s},
                {-s, -s, s, -s, s, s, s, s, s, s, -s, s},
                {-s, -s, -s, -s, s, -s, -s, s, s, -s, -s, s},
                {s, -s, -s, s, -s, s, s, s, s, s, s, -s},
                {-s, s, -s, s, s, -s, s, s, s, -s, s, s},
                {-s, -s, -s, -s, -s, s, s, -s, s, s, -s, -s}};
        for (double[] f : faces) {
            for (int i = 0; i < 4; i++) vertex(x + f[i * 3], y + f[i * 3 + 1], z + f[i * 3 + 2], argb);
        }
    }

    static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return a << 24 | rgb & 0xFFFFFF;
    }

    static int scaleAlpha(int argb, float k) {
        int a = (argb >>> 24) & 0xFF;
        return Math.max(0, Math.min(255, Math.round(a * k))) << 24 | argb & 0xFFFFFF;
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        int al = Math.round(((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        return al << 24 | r << 16 | g << 8 | bl;
    }
}
