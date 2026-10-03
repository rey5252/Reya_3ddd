package com.reya.starfall.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * SS-01's railgun as a 3D structure for the film: a long armoured body inside a truss, two rails running out
 * to a muzzle ring, coils that light one by one as it charges, neon panel frames, radiator wings and lit masts.
 * The barrel points along +Z; the muzzle is at z = {@link #MUZZLE}.
 */
final class Railgun3D {
    static final float MUZZLE = 101.0F;
    private static final int HULL = 0x2E3138, HULL_LIGHT = 0x40444E, FRAME = 0x24262C, RAIL = 0x1C1E24, WING = 0x283040;
    private static final int RED = 0xFF2A2A, RED_SOFT = 0xFF6A6A;
    private static final Matrix4f I = new Matrix4f();

    /**
     * Draws the structure. {@code charge} is 0..1 (coils light in turn), {@code fire} 0..1 is the shot itself,
     * {@code time} in seconds animates the blinking lights.
     */
    static void draw(Scene3D s, float charge, float fire, float time) {
        s.solid();
        // the hull, with a raised spine
        s.box(I, -5.0F, -4.0F, 0.0F, 5.0F, 4.0F, 70.0F, HULL, true);
        s.box(I, -3.5F, 4.0F, 2.0F, 3.5F, 5.2F, 68.0F, HULL_LIGHT, true);
        s.box(I, -3.5F, -5.2F, 2.0F, 3.5F, -4.0F, 68.0F, HULL_LIGHT, true);
        for (int i = 0; i < 6; i++) {
            float z = 6.0F + i * 10.5F;
            s.box(I, -5.6F, -4.6F, z, 5.6F, 4.6F, z + 1.2F, HULL_LIGHT, true);
        }
        // the truss around it
        float tx = 7.5F, ty = 6.5F;
        float[][] corners = {{tx, ty}, {-tx, ty}, {-tx, -ty}, {tx, -ty}};
        for (float[] c : corners) s.rod(v(c[0], c[1], 0.0F), v(c[0], c[1], 70.0F), 0.35F, FRAME, true);
        for (int i = 0; i <= 10; i++) {
            float z = i * 7.0F;
            for (int k = 0; k < 4; k++) {
                float[] a = corners[k], b = corners[(k + 1) % 4];
                s.rod(v(a[0], a[1], z), v(b[0], b[1], z), 0.25F, FRAME, true);
                if (i < 10) s.rod(v(a[0], a[1], z), v(b[0], b[1], z + 7.0F), 0.18F, FRAME, true);
            }
        }
        // the two rails out to the muzzle
        s.box(I, 1.2F, -1.6F, 70.0F, 3.2F, 1.6F, MUZZLE, RAIL, true);
        s.box(I, -3.2F, -1.6F, 70.0F, -1.2F, 1.6F, MUZZLE, RAIL, true);
        s.box(I, -3.6F, -2.2F, 66.0F, 3.6F, -1.6F, MUZZLE - 2.0F, HULL, true);
        // coils along the rails
        for (int i = 0; i < 8; i++) {
            float z = 72.0F + i * 3.6F, h = 4.4F;
            square(s, z, h, 0.45F, HULL_LIGHT, true);
        }
        // the muzzle ring
        int seg = 18;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            s.rod(v((float) Math.cos(a0) * 5.0F, (float) Math.sin(a0) * 5.0F, MUZZLE), v((float) Math.cos(a1) * 5.0F, (float) Math.sin(a1) * 5.0F, MUZZLE), 0.6F, HULL_LIGHT, true);
        }
        // radiator wings and the masts
        Matrix4f wing = new Matrix4f().translate(5.0F, 0.0F, 22.0F).rotateZ(-0.18F);
        s.box(wing, 0.0F, -0.15F, 0.0F, 24.0F, 0.15F, 16.0F, WING, true);
        Matrix4f wing2 = new Matrix4f().translate(-5.0F, 0.0F, 22.0F).rotateZ(0.18F);
        s.box(wing2, -24.0F, -0.15F, 0.0F, 0.0F, 0.15F, 16.0F, WING, true);
        Vector3f mast1 = v(-26.0F, 26.0F, 4.0F), mast2 = v(24.0F, -24.0F, 30.0F);
        s.rod(v(-2.0F, 4.5F, 34.0F), mast1, 0.3F, FRAME, true);
        s.rod(v(2.0F, -4.5F, 40.0F), mast2, 0.3F, FRAME, true);

        s.glow();
        // neon frames on the hull's sides
        float pulse = 0.75F + 0.25F * (float) Math.sin(time * 3.0F);
        int neon = Fx.argb(RED, 0.9F * pulse);
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 5.15F;
            frame(s, x, -3.0F, 9.0F, 3.0F, 61.0F, 0.12F, neon);
            frame(s, x, -1.8F, 14.0F, 1.8F, 56.0F, 0.08F, Fx.argb(RED_SOFT, 0.6F * pulse));
        }
        // lamps along the spine, and the mast tips
        for (int i = 0; i < 10; i++) {
            boolean on = ((int) (time * 4.0F) + i) % 5 != 0;
            if (on) s.sprite(v(0.0F, 5.4F, 6.0F + i * 6.5F), 0.5F, Fx.argb(0xFFF0E0, 0.9F));
        }
        s.sprite(mast1, 1.4F, Fx.argb(0xFFFFFF, 0.95F));
        s.sprite(mast2, 1.4F, Fx.argb(0xFFD0D0, 0.95F));
        // wing seams
        for (int k = 1; k < 4; k++) {
            float u = k * 6.0F;
            Vector3f a = wing.transformPosition(v(u, 0.2F, 0.0F)), b = wing.transformPosition(v(u, 0.2F, 16.0F));
            s.rod(a, b, 0.06F, 0xC08030, false);
            Vector3f c = wing2.transformPosition(v(-u, 0.2F, 0.0F)), d = wing2.transformPosition(v(-u, 0.2F, 16.0F));
            s.rod(c, d, 0.06F, 0xC08030, false);
        }
        // the coils light up one after another as it charges
        for (int i = 0; i < 8; i++) {
            float lit = Math.max(0.0F, Math.min(1.0F, (charge - i / 8.0F) * 6.0F));
            if (lit <= 0.0F) continue;
            float flicker = 0.8F + 0.2F * (float) Math.sin(time * 40.0F + i);
            square(s, 72.0F + i * 3.6F, 4.6F, 0.25F, dim(RED, 0.9F * lit * flicker), false);
        }
        // charge building in the channel between the rails and at the muzzle
        if (charge > 0.0F) {
            Matrix4f axis = new Matrix4f();
            s.tube(axis, 0.8F, 70.0F, MUZZLE, Fx.argb(RED, 0.25F * charge), Fx.argb(RED_SOFT, 0.6F * charge), 10);
            s.sprite(v(0.0F, 0.0F, MUZZLE), 2.0F + 9.0F * charge * charge, Fx.argb(0xFFD0D0, 0.85F * charge));
            s.sprite(v(0.0F, 0.0F, MUZZLE), 6.0F + 20.0F * charge * charge, Fx.argb(RED, 0.35F * charge));
        }
        if (fire > 0.0F) {
            // the shot: a beam out of the muzzle, the ring white-hot, light flaring
            Matrix4f axis = new Matrix4f();
            float k = Math.min(1.0F, fire * 4.0F);
            s.tube(axis, 1.6F * k, MUZZLE, MUZZLE + 3000.0F, Fx.argb(0xFFFFFF, 0.95F), Fx.argb(0xFFFFFF, 0.6F), 16);
            s.tube(axis, 4.0F * k, MUZZLE, MUZZLE + 3000.0F, Fx.argb(RED_SOFT, 0.5F), Fx.argb(RED, 0.3F), 16);
            s.tube(axis, 9.0F * k, MUZZLE, MUZZLE + 3000.0F, Fx.argb(RED, 0.25F), Fx.argb(RED, 0.1F), 16);
            s.ring(axis, 4.4F, 6.4F, MUZZLE + 0.5F, Fx.argb(0xFFFFFF, 0.9F), Fx.argb(RED, 0.0F), 32);
            Vector3f muzzle = v(0.0F, 0.0F, MUZZLE + 1.0F);
            s.sprite(muzzle, 14.0F, Fx.argb(0xFFFFFF, 0.9F));
            s.sprite(muzzle, 40.0F, Fx.argb(0xFF8080, 0.45F));
            s.streak(muzzle, 70.0F, 0.6F, 0.0F, Fx.argb(0xFFFFFF, 0.8F));
            s.streak(muzzle, 40.0F, 0.4F, 0.9F, Fx.argb(0xFFE0E0, 0.6F));
            s.streak(muzzle, 40.0F, 0.4F, -0.9F, Fx.argb(0xFFE0E0, 0.6F));
        }
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    /** A square frame of rods around the barrel axis at z. */
    private static void square(Scene3D s, float z, float h, float r, int rgb, boolean lit) {
        s.rod(v(-h, -h, z), v(h, -h, z), r, rgb, lit);
        s.rod(v(h, -h, z), v(h, h, z), r, rgb, lit);
        s.rod(v(h, h, z), v(-h, h, z), r, rgb, lit);
        s.rod(v(-h, h, z), v(-h, -h, z), r, rgb, lit);
    }

    /** A colour scaled towards black: in the glow pass that is how bright it adds. */
    private static int dim(int rgb, float k) {
        return Fx.mix(0xFF000000, 0xFF000000 | rgb, k) & 0xFFFFFF;
    }

    /** A rectangle of thin glowing rods on the plane x = const. */
    private static void frame(Scene3D s, float x, float y0, float z0, float y1, float z1, float r, int argb) {
        int c = dim(argb & 0xFFFFFF, ((argb >>> 24) & 0xFF) / 255.0F);
        s.rod(v(x, y0, z0), v(x, y0, z1), r, c, false);
        s.rod(v(x, y1, z0), v(x, y1, z1), r, c, false);
        s.rod(v(x, y0, z0), v(x, y1, z0), r, c, false);
        s.rod(v(x, y0, z1), v(x, y1, z1), r, c, false);
    }

    private Railgun3D() {
    }
}
