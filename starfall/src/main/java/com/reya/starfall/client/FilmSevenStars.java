package com.reya.starfall.client;

import com.reya.starfall.BigDipper;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

import static com.reya.starfall.client.Cam.clamp01;
import static com.reya.starfall.client.Cam.ramp;
import static com.reya.starfall.client.Cam.smoother;
import static com.reya.starfall.client.Cam.v;

/**
 * SS-04's film: up from the target at night, then the Big Dipper overhead. Its seven stars wake one after
 * another with long cross-shaped flares, the figure's lines join them, sigils turn around every star as the array
 * comes online; then, from the ground, the seven of them fire down in pillars of white light.
 */
final class FilmSevenStars {
    private static final int VIOLET = ClientStrike.VIOLET, HOT = ClientStrike.VIOLET_HOT, BEAM = 0xD8E6FF;
    private static final float FAR = 1000.0F;

    static void draw(Canvas c, float s) {
        float toStars = ramp(s, 2.4F, 2.7F), toGround = ramp(s, 9.15F, 9.45F);
        Film.labelScale = 1.0F - toStars;
        if (s < 2.7F) earth(c, s);
        Film.labelScale = toStars * (1.0F - toGround);
        if (s > 2.4F && s < 9.45F) constellation(c, s, toStars);
        Film.labelScale = toGround;
        if (s > 9.15F) fire(c, s, toGround);
        Film.labelScale = 1.0F;
        if (s > 11.85F) {
            c.mode(false);
            c.rect(0, 0, c.w, c.h, Fx.argb(0xF0F4FF, clamp01((s - 11.85F) / 0.15F)));
        }
        c.flush();
    }

    private static float wake(int i) {
        return 2.9F + i * 0.42F;
    }

    // ------------------------------------------------------------------ 1: up at night

    private static void earth(Canvas c, float s) {
        float[] at = {0.0F, 0.7F, 1.4F, 2.0F, 2.7F};
        float alt = (float) Math.exp(Cam.keyed1(s, at, (float) Math.log(0.15F), (float) Math.log(1.5F), (float) Math.log(14.0F),
                (float) Math.log(90.0F), (float) Math.log(600.0F)));
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.milky = 0.8F;
        sky.nebulaA = 0x2A1850;
        sky.nebulaB = 0x381444;
        sky.bandStrength = 0.7F;
        Scene3D sc = Film.opening(c, alt, smoother(s, 0.6F, 1.7F), true, VIOLET, s, 1.0F, sky, null);
        Film.label(sc, Film.onEarth(0.0F), 7.0F, "target", VIOLET, ramp(alt, 10.0F, 40.0F));
        sc.end();
    }

    // ------------------------------------------------------------------ 2: the Big Dipper wakes

    /** The stars' directions in a frame looking at {@code center} with {@code right} and {@code up}; scale k. */
    private static Vector3f[] stars(Vector3f center, Vector3f right, Vector3f up, float k) {
        double mx = 0, my = 0;
        for (int i = 0; i < 7; i++) {
            mx += BigDipper.X[i] / 7.0D;
            my += BigDipper.Y[i] / 7.0D;
        }
        Vector3f[] out = new Vector3f[7];
        for (int i = 0; i < 7; i++) {
            float x = (float) (-(BigDipper.X[i] - mx) * k), y = (float) ((BigDipper.Y[i] - my) * k);
            out[i] = new Vector3f(center).add(new Vector3f(right).mul(x)).add(new Vector3f(up).mul(y)).normalize();
        }
        return out;
    }

    private static void constellation(Canvas c, float s, float fade) {
        float drift = (s - 2.4F) * 0.012F;
        Vector3f center = v((float) Math.sin(drift), 0.18F, -(float) Math.cos(drift)).normalize();
        Vector3f right = v((float) Math.cos(drift), 0.0F, (float) Math.sin(drift)).normalize();
        Vector3f up = new Vector3f(right).cross(center).normalize();
        float zoom = 1.0F + 0.04F * (s - 2.4F);
        Cam cam = new Cam(v(0.0F, 0.0F, 0.0F), new Vector3f(center).mul(FAR), up).lens(52.0F / zoom, 1.0F, 1.0E5F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.milky = 0.5F;
        sky.nebulaA = 0x3A1E70;
        sky.nebulaB = 0x501A5A;
        sky.nebula = 1.3F;
        sky.seed = 17.0F;
        sky.band.set(0.6F, 0.4F, 0.7F);
        sky.bandStrength = 0.5F;
        sc.sky(sky);
        float k = 1.0F / (float) BigDipper.EXTENT * 0.95F;
        Vector3f[] dir = stars(center, right, up, k);
        Vector3f[] at = new Vector3f[7];
        float[] awake = new float[7];
        for (int i = 0; i < 7; i++) {
            at[i] = new Vector3f(dir[i]).mul(FAR);
            awake[i] = clamp01((s - wake(i)) / 0.3F);
        }
        float array = ramp(s, 6.6F, 7.2F);
        Soft g = sc.soft(true);
        // the figure's lines, once both ends are awake, with sparks running along them
        for (int[] line : BigDipper.LINES) {
            int a = line[0], b = line[1];
            float lk = Math.min(awake[a], awake[b]);
            if (lk <= 0.0F) continue;
            float w = 0.0015F + 0.002F * array;
            g.line(at[a], at[b], w, w, Fx.argb(HOT, (0.35F + 0.4F * array) * lk), Fx.argb(HOT, (0.35F + 0.4F * array) * lk));
            g.line(at[a], at[b], w * 5.0F, w * 5.0F, Fx.argb(VIOLET, 0.18F * lk + 0.2F * array), Fx.argb(VIOLET, 0.18F * lk + 0.2F * array));
            for (int p = 0; p < 4; p++) {
                float f = ((s * 0.8F + p * 0.25F + a * 0.13F) % 1.0F);
                Vector3f q = new Vector3f(at[a]).lerp(at[b], f);
                g.glowScreen(q, 0.006F, Fx.argb(0xFFFFFF, 0.8F * lk * (0.4F + 0.6F * array)), true);
            }
        }
        for (int i = 0; i < 7; i++) {
            float size = (float) BigDipper.size(i);
            float pulse = 0.88F + 0.12F * (float) Math.sin(s * 7.0F + i);
            if (awake[i] <= 0.0F) {
                g.glowScreen(at[i], 0.006F, Fx.argb(0xFFFFFF, 0.7F), true);
                continue;
            }
            float w = awake[i];
            g.glowScreen(at[i], (0.05F + 0.03F * size) * w * pulse, Fx.argb(VIOLET, 0.55F * w), false);
            g.glowScreen(at[i], (0.014F + 0.008F * size) * w, Fx.argb(0xFFFFFF, 0.95F * w), true);
            // the long cross of light through each awake star
            g.flare(at[i], (0.30F + 0.12F * size) * w, 0.0016F, 0.0F, Fx.argb(0xE8D8FF, 0.75F * w));
            g.flare(at[i], (0.22F + 0.1F * size) * w, 0.0014F, (float) (Math.PI / 2), Fx.argb(0xE8D8FF, 0.7F * w));
            g.flare(at[i], 0.05F * w, 0.001F, (float) (Math.PI / 4), Fx.argb(0xFFFFFF, 0.4F * w));
            g.flare(at[i], 0.05F * w, 0.001F, (float) (-Math.PI / 4), Fx.argb(0xFFFFFF, 0.4F * w));
            float since = s - wake(i);
            if (since >= 0.0F && since < 0.9F) {
                float q = since / 0.9F;
                Vector3f r = new Vector3f(right), u = new Vector3f(up);
                g.ring(at[i], r, u, FAR * (0.01F + 0.12F * q), FAR * 0.0012F * (1.0F + 2.0F * q), Fx.argb(HOT, 0.8F * (1.0F - q)), 64);
            }
            if (array > 0.0F) sigil(g, at[i], right, up, s, i, size, array);
        }
        g.end();
        for (int i = 0; i < 7; i++) {
            Film.labelText(sc, at[i], 9.0F, BigDipper.NAMES[i], Component.translatable("film.starfall.label.star.sub",
                    String.format(java.util.Locale.ROOT, "%.2f", BigDipper.MAG[i])), HOT, ramp(awake[i], 0.3F, 1.0F) * (1.0F - array));
        }
        if (s > wake(0)) Film.charge = clamp01((s - wake(0)) / (9.0F - wake(0)));
        Film.title("seven_stars", ramp(s, 3.0F, 3.4F) * (1.0F - ramp(s, 4.6F, 5.0F)));
        sc.end();
    }

    /** The turning glyphs around a star of the array: dashed rings, a hexagon and ticks. */
    private static void sigil(Soft g, Vector3f at, Vector3f right, Vector3f up, float s, int i, float size, float k) {
        float r0 = FAR * (0.042F + 0.01F * size);
        float spin = s * (i % 2 == 0 ? 0.9F : -0.9F) + i;
        int dash = 12;
        for (int d = 0; d < dash; d++) {
            float a0 = spin + d * (float) (Math.PI * 2.0D) / dash, a1 = a0 + (float) (Math.PI * 2.0D) / dash * 0.6F;
            g.ring(at, right, up, r0 * 1.35F, FAR * 0.0009F, Fx.argb(HOT, 0.8F * k), 6, a0, a1);
        }
        g.ring(at, right, up, r0, FAR * 0.0007F, Fx.argb(0xFFFFFF, 0.65F * k), 72);
        g.ring(at, right, up, r0 * 0.55F, FAR * 0.0006F, Fx.argb(VIOLET, 0.7F * k), 48);
        Vector3f[] hex = new Vector3f[6];
        for (int e = 0; e < 6; e++) {
            double a = -spin * 0.7F + e * Math.PI / 3.0D;
            hex[e] = new Vector3f(right).mul((float) Math.cos(a) * r0 * 0.9F).add(new Vector3f(up).mul((float) Math.sin(a) * r0 * 0.9F)).add(at);
        }
        for (int e = 0; e < 6; e++) {
            g.line(hex[e], hex[(e + 1) % 6], 0.0011F, 0.0011F, Fx.argb(0xE8D8FF, 0.75F * k), Fx.argb(0xE8D8FF, 0.75F * k));
            g.line(hex[e], hex[(e + 2) % 6], 0.0007F, 0.0007F, Fx.argb(VIOLET, 0.45F * k), Fx.argb(VIOLET, 0.45F * k));
        }
        for (int t = 0; t < 24; t++) {
            double a = spin * 0.5F + t * Math.PI / 12.0D;
            Vector3f d = new Vector3f(right).mul((float) Math.cos(a)).add(new Vector3f(up).mul((float) Math.sin(a)));
            float len = t % 6 == 0 ? 0.25F : 0.1F;
            g.line(new Vector3f(d).mul(r0 * 1.55F).add(at), new Vector3f(d).mul(r0 * (1.55F + len)).add(at), 0.0008F, 0.0008F,
                    Fx.argb(HOT, 0.6F * k), Fx.argb(HOT, 0.6F * k));
        }
    }

    // ------------------------------------------------------------------ 3: from the ground, the seven pillars

    private static void fire(Canvas c, float s, float fade) {
        Vector3f t = Film.onEarth(0.0F);
        Vector3f eye = Film.onEarth(0.05F).sub(new Vector3f(Film.NORTH).mul(0.9F));
        float elev = Cam.lerp(0.42F, 0.2F, smoother(s, 9.15F, 11.0F));
        Vector3f center = new Vector3f(Film.NORTH).mul((float) Math.cos(elev)).add(new Vector3f(Film.TARGET).mul((float) Math.sin(elev))).normalize();
        Vector3f right = new Vector3f(Film.EAST);
        Vector3f up = new Vector3f(right).cross(center).normalize();
        Cam cam = new Cam(eye, new Vector3f(center).mul(10.0F).add(eye), up).lens(62.0F, 0.002F, 1.0E6F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.milky = 0.8F;
        sky.nebulaA = 0x2A1850;
        sky.nebulaB = 0x381444;
        sky.bandStrength = 0.6F;
        sky.stars = 0.9F;
        sc.sky(sky);
        Scene3D.Planet earth = Film.earth(true, VIOLET, 0.0F, s);
        earth.night = 1.0F;
        sc.planet(earth);
        // the stars stay where they are in the sky while the camera tilts down
        Vector3f sky0 = new Vector3f(Film.NORTH).mul((float) Math.cos(0.5D)).add(new Vector3f(Film.TARGET).mul((float) Math.sin(0.5D))).normalize();
        Vector3f up0 = new Vector3f(right).cross(sky0).normalize();
        Vector3f[] dir = stars(sky0, right, up0, 1.0F / (float) BigDipper.EXTENT * 0.8F);
        Soft g = sc.soft(true);
        float dawn = 0.0F;
        for (int i = 0; i < 7; i++) {
            Vector3f star = new Vector3f(dir[i]).mul(3000.0F).add(eye);
            g.glowScreen(star, 0.03F, Fx.argb(VIOLET, 0.6F), false);
            g.glowScreen(star, 0.01F, Fx.argb(0xFFFFFF, 0.95F), true);
            g.flare(star, 0.18F, 0.0014F, 0.0F, Fx.argb(0xE8D8FF, 0.6F));
            g.flare(star, 0.12F, 0.0012F, (float) (Math.PI / 2), Fx.argb(0xE8D8FF, 0.5F));
            float f = clamp01((s - (9.55F + i * 0.17F)) / 0.35F);
            if (f <= 0.0F) continue;
            // each beam comes down to its own spot on the land, spread out in front of us
            Vector3f ground = Film.onEarth(0.0F).add(new Vector3f(Film.EAST).mul((i - 3) * 0.55F)).add(new Vector3f(Film.NORTH).mul(0.9F + (i % 3) * 0.6F));
            Vector3f head = new Vector3f(star).lerp(ground, f);
            g.beam(star, head, 0.9F, 0.05F + 0.04F * f, Fx.argb(BEAM, 0.25F), Fx.argb(BEAM, 0.55F));
            g.beam(star, head, 0.3F, 0.012F + 0.008F * f, Fx.argb(0xFFFFFF, 0.6F), Fx.argb(0xFFFFFF, 0.95F));
            if (f >= 1.0F) {
                float since = s - (9.55F + i * 0.17F + 0.35F);
                float q = clamp01(since / 0.8F);
                g.glow(ground, 0.04F + 0.08F * q, Fx.argb(0xFFFFFF, 0.9F), true);
                g.glow(ground, 0.15F + 0.25F * q, Fx.argb(BEAM, 0.25F), false);
                g.ring(ground, Film.EAST, Film.NORTH, 0.05F + 0.45F * q, 0.006F + 0.012F * q, Fx.argb(HOT, 0.8F * (1.0F - q)), 48);
                dawn += 1.0F / 7.0F;
            }
        }
        // the sky lights up over the horizon as they land
        g.glowScreen(Film.onEarth(0.0F).add(new Vector3f(Film.NORTH).mul(2.5F)), 0.6F, Fx.argb(0x9AB0FF, 0.22F * dawn), false);
        g.end();
        sc.end();
    }

    private FilmSevenStars() {
    }
}
