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
import net.minecraft.util.RandomSource;
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

    /** Where the camera is. */
    Vec3 camera() {
        return cam;
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

    /**
     * An upright band around (x, z) that follows the land: at each of the {@code ground.length} points spaced
     * around it, the band runs from {@code ground + y0} to {@code ground + y1}, fading from c0 to c1.
     */
    void skirt(double x, double z, double r, double[] ground, double y0, double y1, int c0, int c1) {
        int n = ground.length;
        for (int i = 0; i < n; i++) {
            int j = i + 1 == n ? 0 : i + 1;
            double a0 = Math.PI * 2.0D * i / n, a1 = Math.PI * 2.0D * (i + 1) / n;
            double x0 = x + Math.cos(a0) * r, z0 = z + Math.sin(a0) * r;
            double x1 = x + Math.cos(a1) * r, z1 = z + Math.sin(a1) * r;
            vertex(x0, ground[i] + y0, z0, c0);
            vertex(x1, ground[j] + y0, z1, c0);
            vertex(x1, ground[j] + y1, z1, c1);
            vertex(x0, ground[i] + y1, z0, c1);
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

    /** A regular polygon outline lying on the ground, turned by {@code rotation} radians. */
    void polygon(double x, double y, double z, double r, int sides, double rotation, double width, int argb) {
        for (int i = 0; i < sides; i++) {
            double a0 = rotation + Math.PI * 2.0D * i / sides, a1 = rotation + Math.PI * 2.0D * (i + 1) / sides;
            groundBand(x + Math.cos(a0) * r, y, z + Math.sin(a0) * r, x + Math.cos(a1) * r, y, z + Math.sin(a1) * r,
                    width * 0.5D, argb, argb);
        }
    }

    /** A sigil laid on the ground: rings, a hexagram and tick marks, slowly turning. */
    void sigil(double x, double y, double z, double r, double turn, int argb, int soft) {
        ring(x, y, z, 0.0D, r, 48, Fx.scaleAlpha(soft, 0.35F), soft);
        dashedRing(x, y, z, r, Math.max(0.5D, r * 0.03D), 96, argb, turn * 2.0D, 18);
        ring(x, y, z, r * 0.9D, r * 0.92D, 64, argb, argb);
        dashedRing(x, y, z, r * 0.62D, Math.max(0.35D, r * 0.02D), 64, argb, -turn * 3.0D, 6);
        polygon(x, y, z, r * 0.6D, 3, turn, Math.max(0.35D, r * 0.018D), argb);
        polygon(x, y, z, r * 0.6D, 3, turn + Math.PI, Math.max(0.35D, r * 0.018D), argb);
        for (int k = 0; k < 24; k++) {
            double a = -turn + Math.PI * 2.0D * k / 24.0D;
            double r0 = r * (k % 3 == 0 ? 0.74D : 0.8D), r1 = r * 0.88D;
            groundBand(x + Math.cos(a) * r0, y, z + Math.sin(a) * r0, x + Math.cos(a) * r1, y, z + Math.sin(a) * r1,
                    Math.max(0.2D, r * 0.008D), argb, argb);
        }
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

    /** A lens flare facing the camera: a small glow crossed by long thin spikes, the level ones longest. */
    void flare(double x, double y, double z, double size, int argb) {
        int edge = argb & 0x00FFFFFF;
        glow(x, y, z, size * 0.06D, argb);
        glow(x, y, z, size * 0.18D, scaleAlpha(argb, 0.35F));
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2.0D * k / 8.0D;
            double len = size * (k % 4 == 0 ? 1.0D : k % 2 == 0 ? 0.55D : 0.3D);
            double c = Math.cos(a) * len, s = Math.sin(a) * len;
            ribbon(x, y, z, x + left.x() * c + up.x() * s, y + left.y() * c + up.y() * s, z + left.z() * c + up.z() * s,
                    size * (k % 2 == 0 ? 0.006D : 0.004D), argb, edge);
        }
    }

    /**
     * A bolt of lightning from a to b: the line is broken up by midpoint displacement, wandering at most
     * {@code wander} blocks off it, and forks into thinner branches. The same seed draws the same bolt, so a new
     * seed every few frames makes it crackle. A white core is drawn inside a wider coloured halo.
     */
    void bolt(double ax, double ay, double az, double bx, double by, double bz, double half, double wander,
              long seed, int core, int halo) {
        boltPart(RandomSource.create(seed), ax, ay, az, bx, by, bz, half, wander, core, halo, 0);
    }

    private void boltPart(RandomSource r, double ax, double ay, double az, double bx, double by, double bz,
                          double half, double wander, int core, int halo, int depth) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-3D) return;
        dx /= len;
        dy /= len;
        dz /= len;
        // about one kink every two blocks, as a power of two so the halving below comes out even
        int n = 8;
        while (n < 128 && len / n > 2.0D) n <<= 1;
        if (depth > 0) n = Math.max(4, n >> 1);
        double[] px = new double[n + 1], py = new double[n + 1], pz = new double[n + 1];
        px[0] = ax;
        py[0] = ay;
        pz[0] = az;
        px[n] = bx;
        py[n] = by;
        pz[n] = bz;
        double amp = Math.min(len * 0.17D, wander);
        for (int step = n; step > 1; step >>= 1) {
            for (int i = 0; i < n; i += step) {
                int m = i + step / 2;
                // a random push sideways, never along the bolt
                double ox = r.nextDouble() * 2.0D - 1.0D, oy = r.nextDouble() * 2.0D - 1.0D, oz = r.nextDouble() * 2.0D - 1.0D;
                double along = ox * dx + oy * dy + oz * dz;
                ox -= along * dx;
                oy -= along * dy;
                oz -= along * dz;
                px[m] = (px[i] + px[i + step]) * 0.5D + ox * amp;
                py[m] = (py[i] + py[i + step]) * 0.5D + oy * amp;
                pz[m] = (pz[i] + pz[i + step]) * 0.5D + oz * amp;
            }
            amp *= 0.5D;
        }
        for (int i = 0; i < n; i++) {
            ribbon(px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], half * 4.0D, halo, halo);
            ribbon(px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], half, core, core);
        }
        if (depth >= 2 || n < 8) return;
        int forks = depth == 0 ? 2 + r.nextInt(3) : r.nextInt(2);
        for (int f = 0; f < forks; f++) {
            int i = 2 + r.nextInt(n - 4);
            // a fork carries on roughly the way the bolt was going there, swung off to one side
            double tx = px[i + 1] - px[i - 1], ty = py[i + 1] - py[i - 1], tz = pz[i + 1] - pz[i - 1];
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1.0E-6D) continue;
            tx = tx / tl + (r.nextDouble() * 2.0D - 1.0D) * 0.9D;
            ty = ty / tl + (r.nextDouble() * 2.0D - 1.0D) * 0.9D;
            tz = tz / tl + (r.nextDouble() * 2.0D - 1.0D) * 0.9D;
            double nl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            double fl = len * (0.22D + 0.25D * r.nextDouble()) / nl;
            boltPart(r, px[i], py[i], pz[i], px[i] + tx * fl, py[i] + ty * fl, pz[i] + tz * fl, half * 0.55D,
                    wander * 0.5D, scaleAlpha(core, 0.75F), scaleAlpha(halo, 0.7F), depth + 1);
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
