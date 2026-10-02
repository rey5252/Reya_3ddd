package com.reya.singularityfusion.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Drawing helpers the renderers share: vertices, quads seen from both sides, bands of light between two points, boxes. */
final class Fx {
    static final float PI = (float) Math.PI;

    static ResourceLocation effect(String name) {
        return new ResourceLocation(SingularityFusion.MODID, "textures/effect/" + name + ".png");
    }

    static ResourceLocation entity(String name) {
        return new ResourceLocation(SingularityFusion.MODID, "textures/entity/" + name + ".png");
    }

    static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float r, float g, float b, float a,
                       int light) {
        vc.vertex(m, x, y, z).color(c(r), c(g), c(b), c(a)).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    /** A colour channel kept within 0 to 1: the vertex format packs it into a byte, and 1.05 would wrap round to nearly nothing. */
    static float c(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    /** A square of half-size s round (x, y) in the local x-y plane at z, seen from both sides, full bright. */
    static void square(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float s, float r, float g, float b, float a) {
        rect(vc, m, n, x - s, y - s, x + s, y + s, z, 0.0F, 0.0F, 1.0F, 1.0F, r, g, b, a);
    }

    /** A rectangle in the local x-y plane at z with a texture's (u1, v1)-(u2, v2) on it (v1 at the top), both sides, full bright. */
    static void rect(VertexConsumer vc, Matrix4f m, Matrix3f n, float x1, float y1, float x2, float y2, float z, float u1, float v1, float u2,
                     float v2, float r, float g, float b, float a) {
        int l = LightTexture.FULL_BRIGHT;
        vertex(vc, m, n, x1, y2, z, u1, v1, r, g, b, a, l);
        vertex(vc, m, n, x1, y1, z, u1, v2, r, g, b, a, l);
        vertex(vc, m, n, x2, y1, z, u2, v2, r, g, b, a, l);
        vertex(vc, m, n, x2, y2, z, u2, v1, r, g, b, a, l);
        vertex(vc, m, n, x2, y2, z, u2, v1, r, g, b, a, l);
        vertex(vc, m, n, x2, y1, z, u2, v2, r, g, b, a, l);
        vertex(vc, m, n, x1, y1, z, u1, v2, r, g, b, a, l);
        vertex(vc, m, n, x1, y2, z, u1, v1, r, g, b, a, l);
    }

    /**
     * A band of light from a to b (local coordinates), `width` across, turned to face the eye at `eye`; the texture's u runs
     * along it (u1 to u2), its v across it. Both sides, full bright; alpha at each end.
     */
    static void band(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, Vec3 eye, float width, float u1, float u2, float r, float g, float bl,
                     float alphaA, float alphaB) {
        Vec3 along = b.subtract(a);
        Vec3 side = along.cross(eye.subtract(a.add(b).scale(0.5D)));
        double len = side.length();
        if (len < 1.0E-6D) return;
        side = side.scale(width * 0.5D / len);
        int l = LightTexture.FULL_BRIGHT;
        Vec3 a1 = a.add(side), a2 = a.subtract(side), b1 = b.add(side), b2 = b.subtract(side);
        float ra = r * alphaA, ga = g * alphaA, ba = bl * alphaA, rb = r * alphaB, gb = g * alphaB, bb = bl * alphaB;
        vertex(vc, m, n, (float) a1.x, (float) a1.y, (float) a1.z, u1, 0.0F, ra, ga, ba, alphaA, l);
        vertex(vc, m, n, (float) a2.x, (float) a2.y, (float) a2.z, u1, 1.0F, ra, ga, ba, alphaA, l);
        vertex(vc, m, n, (float) b2.x, (float) b2.y, (float) b2.z, u2, 1.0F, rb, gb, bb, alphaB, l);
        vertex(vc, m, n, (float) b1.x, (float) b1.y, (float) b1.z, u2, 0.0F, rb, gb, bb, alphaB, l);
        vertex(vc, m, n, (float) b1.x, (float) b1.y, (float) b1.z, u2, 0.0F, rb, gb, bb, alphaB, l);
        vertex(vc, m, n, (float) b2.x, (float) b2.y, (float) b2.z, u2, 1.0F, rb, gb, bb, alphaB, l);
        vertex(vc, m, n, (float) a2.x, (float) a2.y, (float) a2.z, u1, 1.0F, ra, ga, ba, alphaA, l);
        vertex(vc, m, n, (float) a1.x, (float) a1.y, (float) a1.z, u1, 0.0F, ra, ga, ba, alphaA, l);
    }

    /**
     * A box from (x1, y1, z1) to (x2, y2, z2), its sides with the texture's (su1, sv1)-(su2, sv2) and its ends with
     * (eu1, ev1)-(eu2, ev2) (the texture's v down the sides, from the top), lit by `light`, tinted.
     */
    static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, float x1, float y1, float z1, float x2, float y2, float z2, float su1, float sv1,
                    float su2, float sv2, float eu1, float ev1, float eu2, float ev2, float r, float g, float b, float a, int light) {
        // sides: north (-z), south (+z), west (-x), east (+x); each from its top-left, counter-clockwise seen from outside
        face(vc, m, n, x2, y2, z1, x2, y1, z1, x1, y1, z1, x1, y2, z1, su1, sv1, su2, sv2, 0, 0, -1, r, g, b, a, light);
        face(vc, m, n, x1, y2, z2, x1, y1, z2, x2, y1, z2, x2, y2, z2, su1, sv1, su2, sv2, 0, 0, 1, r, g, b, a, light);
        face(vc, m, n, x1, y2, z1, x1, y1, z1, x1, y1, z2, x1, y2, z2, su1, sv1, su2, sv2, -1, 0, 0, r, g, b, a, light);
        face(vc, m, n, x2, y2, z2, x2, y1, z2, x2, y1, z1, x2, y2, z1, su1, sv1, su2, sv2, 1, 0, 0, r, g, b, a, light);
        // ends: top (+y), bottom (-y)
        face(vc, m, n, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, eu1, ev1, eu2, ev2, 0, 1, 0, r, g, b, a, light);
        face(vc, m, n, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, eu1, ev1, eu2, ev2, 0, -1, 0, r, g, b, a, light);
    }

    /** A box as above, with its sides' and ends' texture regions given as {u1, v1, u2, v2}. */
    static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, float x1, float y1, float z1, float x2, float y2, float z2, float[] side, float[] end,
                    float r, float g, float b, float a, int light) {
        box(vc, m, n, x1, y1, z1, x2, y2, z2, side[0], side[1], side[2], side[3], end[0], end[1], end[2], end[3], r, g, b, a, light);
    }

    /**
     * A flat ring round the y axis from y1 to y2, between the radii ri and ro, in `segments` pieces: its top and bottom
     * with the texture region `face` (u round the ring, v from its inside out), its outer wall with `wall` and its inner
     * with `inner` (u round, v down). Regions are {u1, v1, u2, v2}, 0 to 1. Every face turned outward (for culled types).
     */
    static void ring(VertexConsumer vc, Matrix4f m, Matrix3f n, float ri, float ro, float y1, float y2, int segments, float[] face, float[] wall,
                     float[] inner, float r, float g, float b, float a, int light) {
        for (int j = 0; j < segments; j++) {
            float a0 = 2.0F * PI * j / segments, a1 = 2.0F * PI * (j + 1) / segments, am = (a0 + a1) * 0.5F;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1), cm = Mth.cos(am), sm = Mth.sin(am);
            float t0 = j / (float) segments, t1 = (j + 1) / (float) segments;
            float fu0 = Mth.lerp(t0, face[0], face[2]), fu1 = Mth.lerp(t1, face[0], face[2]);
            float wu0 = Mth.lerp(t0, wall[0], wall[2]), wu1 = Mth.lerp(t1, wall[0], wall[2]);
            float iu0 = Mth.lerp(t0, inner[0], inner[2]), iu1 = Mth.lerp(t1, inner[0], inner[2]);
            // top: seen from above, counter-clockwise
            corner(vc, m, n, ri * c0, y2, ri * s0, fu0, face[1], 0.0F, 1.0F, 0.0F, r, g, b, a, light);
            corner(vc, m, n, ri * c1, y2, ri * s1, fu1, face[1], 0.0F, 1.0F, 0.0F, r, g, b, a, light);
            corner(vc, m, n, ro * c1, y2, ro * s1, fu1, face[3], 0.0F, 1.0F, 0.0F, r, g, b, a, light);
            corner(vc, m, n, ro * c0, y2, ro * s0, fu0, face[3], 0.0F, 1.0F, 0.0F, r, g, b, a, light);
            // bottom
            corner(vc, m, n, ri * c0, y1, ri * s0, fu0, face[1], 0.0F, -1.0F, 0.0F, r, g, b, a, light);
            corner(vc, m, n, ro * c0, y1, ro * s0, fu0, face[3], 0.0F, -1.0F, 0.0F, r, g, b, a, light);
            corner(vc, m, n, ro * c1, y1, ro * s1, fu1, face[3], 0.0F, -1.0F, 0.0F, r, g, b, a, light);
            corner(vc, m, n, ri * c1, y1, ri * s1, fu1, face[1], 0.0F, -1.0F, 0.0F, r, g, b, a, light);
            // the outer wall
            corner(vc, m, n, ro * c0, y2, ro * s0, wu0, wall[1], cm, 0.0F, sm, r, g, b, a, light);
            corner(vc, m, n, ro * c1, y2, ro * s1, wu1, wall[1], cm, 0.0F, sm, r, g, b, a, light);
            corner(vc, m, n, ro * c1, y1, ro * s1, wu1, wall[3], cm, 0.0F, sm, r, g, b, a, light);
            corner(vc, m, n, ro * c0, y1, ro * s0, wu0, wall[3], cm, 0.0F, sm, r, g, b, a, light);
            // the inner wall
            corner(vc, m, n, ri * c0, y2, ri * s0, iu0, inner[1], -cm, 0.0F, -sm, r, g, b, a, light);
            corner(vc, m, n, ri * c0, y1, ri * s0, iu0, inner[3], -cm, 0.0F, -sm, r, g, b, a, light);
            corner(vc, m, n, ri * c1, y1, ri * s1, iu1, inner[3], -cm, 0.0F, -sm, r, g, b, a, light);
            corner(vc, m, n, ri * c1, y2, ri * s1, iu1, inner[1], -cm, 0.0F, -sm, r, g, b, a, light);
        }
    }

    /**
     * A crystal: two four-sided pyramids base to base round (0, y, 0), w from its middle to its edges and h to its
     * points; the texture region `uv` ({u1, v1, u2, v2}) on each face, its upper half on the upper faces.
     */
    static void crystal(VertexConsumer vc, Matrix4f m, Matrix3f n, float y, float w, float h, float[] uv, float r, float g, float b, float a,
                        int light) {
        float[][] rim = {{w, 0.0F}, {0.0F, w}, {-w, 0.0F}, {0.0F, -w}};
        float um = (uv[0] + uv[2]) * 0.5F, vm = (uv[1] + uv[3]) * 0.5F;
        for (int k = 0; k < 4; k++) {
            float[] p = rim[k], q = rim[(k + 1) % 4];
            // the face's normal: out from the middle of its edge, tipped up as far as the face leans
            float ox = (p[0] + q[0]) * 0.5F, oz = (p[1] + q[1]) * 0.5F;
            float ol = (float) Math.sqrt(ox * ox + oz * oz), slope = (float) Math.sqrt(ol * ol + h * h);
            float nx = ox / ol * h / slope, nz = oz / ol * h / slope, ny = ol / slope;
            float shade = k % 2 == 0 ? 1.0F : 0.86F;
            // upper: from the next corner to this one, then the point (counter-clockwise from outside)
            corner(vc, m, n, q[0], y, q[1], uv[0], vm, nx, ny, nz, r * shade, g * shade, b * shade, a, light);
            corner(vc, m, n, p[0], y, p[1], uv[2], vm, nx, ny, nz, r * shade, g * shade, b * shade, a, light);
            corner(vc, m, n, 0.0F, y + h, 0.0F, um, uv[1], nx, ny, nz, r * shade, g * shade, b * shade, a, light);
            corner(vc, m, n, 0.0F, y + h, 0.0F, um, uv[1], nx, ny, nz, r * shade, g * shade, b * shade, a, light);
            // lower
            float dark = shade * 0.78F;
            corner(vc, m, n, p[0], y, p[1], uv[0], vm, nx, -ny, nz, r * dark, g * dark, b * dark, a, light);
            corner(vc, m, n, q[0], y, q[1], uv[2], vm, nx, -ny, nz, r * dark, g * dark, b * dark, a, light);
            corner(vc, m, n, 0.0F, y - h, 0.0F, um, uv[3], nx, -ny, nz, r * dark, g * dark, b * dark, a, light);
            corner(vc, m, n, 0.0F, y - h, 0.0F, um, uv[3], nx, -ny, nz, r * dark, g * dark, b * dark, a, light);
        }
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float nx, float ny, float nz,
                               float r, float g, float b, float a, int light) {
        vc.vertex(m, x, y, z).color(c(r), c(g), c(b), c(a)).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz)
                .endVertex();
    }

    /** A region of a texture `size` pixels square, from pixel corners {x1, y1, x2, y2} to {u1, v1, u2, v2}. */
    static float[] uv(int[] px, int size) {
        return new float[] {px[0] / (float) size, px[1] / (float) size, px[2] / (float) size, px[3] / (float) size};
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, float u1, float v1, float u2, float v2, float nx, float ny, float nz, float r, float g,
                             float b, float a, int light) {
        r = c(r);
        g = c(g);
        b = c(b);
        a = c(a);
        vc.vertex(m, ax, ay, az).color(r, g, b, a).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).uv(u1, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).uv(u2, v2).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).uv(u2, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    static float smooth(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** A steady pseudo-random number in [0, 1) for a few integers (the same every frame). */
    static float hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private Fx() {
    }
}
