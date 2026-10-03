package com.reya.starfall.client;

import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.reya.starfall.client.Cam.clamp01;
import static com.reya.starfall.client.Cam.ramp;
import static com.reya.starfall.client.Cam.smoother;
import static com.reya.starfall.client.Cam.v;

/**
 * SS-03's film: up from the target, out to Jupiter, where an accelerator ring circles the planet. The black
 * needle runs seven laps around Jupiter, faster each time, the ring's stations flaring as it passes; it is flung
 * off the ring at the launch gate and falls on the Earth in silence.
 */
final class FilmGungnir {
    private static final int EMBER = ClientStrike.EMBER, HOT = ClientStrike.EMBER_HOT, CYAN = 0x5FD8EC;
    private static final float RING = 170.0F;
    private static final Matrix4f RING_TILT = new Matrix4f().rotateX(0.2F);
    private static final float LAPS_FROM = 3.3F, RELEASE = 8.0F;

    static void draw(Canvas c, float s) {
        float toJupiter = ramp(s, 2.2F, 2.5F), toEarth = ramp(s, 8.45F, 8.75F);
        Film.labelScale = 1.0F - toJupiter;
        if (s < 2.5F) earth(c, s);
        Film.labelScale = toJupiter * (1.0F - toEarth);
        if (s > 2.2F && s < 8.75F) jupiter(c, s, toJupiter);
        Film.labelScale = toEarth;
        if (s > 8.45F) descent(c, s, toEarth);
        Film.labelScale = 1.0F;
        float flash = FilmRailgun.bump(s, 8.0F, 0.12F) * 0.5F;
        if (flash > 0.0F) {
            c.mode(false);
            c.rect(0, 0, c.w, c.h, Fx.argb(0xFFE8D0, flash));
        }
        if (s > 11.85F) {
            c.mode(false);
            c.rect(0, 0, c.w, c.h, Fx.argb(0xFFF0E0, clamp01((s - 11.85F) / 0.15F)));
        }
        c.flush();
    }

    // ------------------------------------------------------------------ 1: up from the ember mark

    private static void earth(Canvas c, float s) {
        float[] at = {0.0F, 0.6F, 1.2F, 1.8F, 2.5F};
        float alt = (float) Math.exp(Cam.keyed1(s, at, (float) Math.log(0.15F), (float) Math.log(1.2F), (float) Math.log(12.0F),
                (float) Math.log(130.0F), (float) Math.log(1500.0F)));
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.nebulaA = 0x30180C;
        sky.nebulaB = 0x101830;
        Scene3D sc = Film.opening(c, alt, smoother(s, 0.5F, 1.5F), false, EMBER, s, 1.0F, sky, null);
        Film.label(sc, Film.onEarth(0.0F), 7.0F, "target", EMBER, ramp(alt, 20.0F, 60.0F));
        sc.end();
    }

    // ------------------------------------------------------------------ 2: the ring around Jupiter

    /** Laps run by s seconds: seven, quicker and quicker. */
    private static float laps(float s) {
        float q = clamp01((s - LAPS_FROM) / (RELEASE - LAPS_FROM));
        return 7.0F * (float) Math.pow(q, 1.7D);
    }

    /** A point on the ring's track at angle a, in the world. */
    private static Vector3f track(double a, float lift) {
        return RING_TILT.transformPosition(new Vector3f((float) Math.cos(a) * RING, lift, (float) Math.sin(a) * RING));
    }

    private static Vector3f gateDir() {
        return RING_TILT.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f()).normalize();
    }

    /** Where the needle is: on the track until it's released, then flung off along the gate. */
    private static Vector3f needle(float s) {
        if (s < RELEASE) return track(laps(s) * Math.PI * 2.0D, 0.0F);
        float t = s - RELEASE;
        return new Vector3f(gateDir()).mul(500.0F * t + 2600.0F * t * t).add(track(0.0D, 0.0F));
    }

    private static void jupiter(Canvas c, float s, float fade) {
        Vector3f eye;
        if (s < 3.4F) {
            eye = Cam.lerp(v(180.0F, 300.0F, 1800.0F), v(190.0F, 105.0F, 470.0F), smoother(s, 2.2F, 3.4F));
        } else {
            float az = Cam.lerp(0.38F, -0.36F, smoother(s, 3.4F, 8.3F));
            float d = Cam.lerp(508.0F, 450.0F, ramp(s, 3.4F, 8.0F));
            eye = v((float) Math.sin(az) * d, 105.0F - 20.0F * ramp(s, 3.4F, 8.0F), (float) Math.cos(az) * d);
        }
        Vector3f look = v(0.0F, -10.0F, 0.0F);
        if (s > RELEASE) look = Cam.lerp(look, needle(Math.min(s, RELEASE + 0.5F)), smoother(s, RELEASE, RELEASE + 0.45F) * 0.85F);
        Cam cam = new Cam(eye, look).lens(46.0F, 0.5F, 1.0E6F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.nebulaA = 0x2A1A10;
        sky.nebulaB = 0x10182E;
        sky.seed = 7.0F;
        sky.bandStrength = 0.5F;
        sc.sky(sky);
        Scene3D.Planet p = new Scene3D.Planet();
        p.kind = Scene3D.Planet.JUPITER;
        p.radius = 100.0F;
        p.tilt = 0.05F;
        p.spin = -1.0F + s * 0.03F;
        p.sun.set(-0.85F, 0.22F, 0.48F).normalize();
        p.atmosphere = 0xFFC890;
        p.atmosphereHeight = 0.015F;
        p.time = s;
        sc.planet(p);
        // the ring itself: track, ties and stations
        Scene3D.Light light = new Scene3D.Light();
        light.sun.set(p.sun);
        light.sunColor = 0xFFF0E0;
        light.sunStrength = 1.25F;
        light.fill = 0x3A2A20;
        light.glow.set(needle(s));
        light.glowColor = 0xFF8A3A;
        light.glowStrength = 2.0F + 6.0F * clamp01((s - LAPS_FROM) / 4.0F);
        light.glowRange = 30.0F;
        light.time = s;
        sc.mesh(Mesh.ACCELERATOR, RING_TILT, light);

        float laps = laps(s);
        double a = laps * Math.PI * 2.0D;
        float speed = 7.0F * 1.7F * (float) Math.pow(Math.max(clamp01((s - LAPS_FROM) / (RELEASE - LAPS_FROM)), 1.0E-3D), 0.7D);
        Soft glow = sc.soft(true);
        // the track hums with the field holding the needle
        Vector3f ru = RING_TILT.transformDirection(v(1.0F, 0.0F, 0.0F), new Vector3f()), rw = RING_TILT.transformDirection(v(0.0F, 0.0F, 1.0F), new Vector3f());
        float hum = 0.12F + 0.25F * clamp01((s - LAPS_FROM) / (RELEASE - LAPS_FROM));
        glow.ring(new Vector3f(), ru, rw, RING, 1.6F, Fx.argb(EMBER, hum), 240);
        glow.ring(new Vector3f(), ru, rw, RING, 0.4F, Fx.argb(HOT, hum * 1.5F), 240);
        // the stations flare as the needle runs past them
        for (int k = 0; k < 24; k++) {
            double ak = Math.PI * 2.0D * k / 24.0D;
            double behind = (a - ak) % (Math.PI * 2.0D);
            if (behind < 0) behind += Math.PI * 2.0D;
            float since = s < LAPS_FROM ? 99.0F : (float) behind;
            float flare = since < 0.9F ? 1.0F - since / 0.9F : 0.0F;
            Vector3f at = track(ak, 0.0F);
            glow.glow(at, 4.0F + 10.0F * flare, Fx.argb(HOT, 0.35F + 0.65F * flare), true);
            if (flare > 0.2F) glow.glowScreen(at, 0.03F * flare, Fx.argb(0xFFE0B0, 0.6F * flare), false);
        }
        if (s < RELEASE + 0.1F) {
            // the trail of the needle spinning up: a burning arc behind it along the track
            float trail = Math.min(5.0F, 0.12F + speed * 0.3F);
            int steps = 48;
            for (int k = 0; k < steps; k++) {
                double a0 = a - trail * k / steps, a1 = a - trail * (k + 1) / steps;
                float f = 1.0F - (float) k / steps;
                float w = (1.5F + 4.5F * f) * (0.5F + 0.5F * clamp01(speed / 6.0F));
                glow.beam(track(a0, 0.0F), track(a1, 0.0F), w, w, Fx.argb(EMBER, 0.9F * f * f), Fx.argb(EMBER, 0.9F * f * f));
                glow.beam(track(a0, 0.0F), track(a1, 0.0F), w * 0.3F, w * 0.3F, Fx.argb(HOT, f * f * f), Fx.argb(HOT, f * f * f));
            }
        }
        Vector3f n = needle(s);
        glow.glow(n, 8.0F + speed * 2.0F, Fx.argb(EMBER, 0.7F), true);
        glow.glowScreen(n, 0.02F, Fx.argb(0xFFF0D8, 0.95F), true);
        glow.flare(n, 0.08F, 0.0014F, 0.0F, Fx.argb(HOT, 0.6F));
        if (s > RELEASE) {
            Vector3f back = new Vector3f(gateDir()).mul(-Math.min(800.0F, 120.0F + (s - RELEASE) * 1800.0F)).add(n);
            glow.beam(back, n, 1.0F, 6.0F, Fx.argb(EMBER, 0.0F), Fx.argb(HOT, 0.9F));
            Stars.warp(sc, glow, new Vector3f(gateDir()).mul(-600.0F * ramp(s, RELEASE, RELEASE + 0.3F)), 500.0F, 0.04F,
                    ramp(s, RELEASE, RELEASE + 0.3F), 0xFFE0C8);
        }
        glow.end();
        // the needle itself: a black splinter
        Soft dark = sc.soft(false);
        Vector3f dir = s < RELEASE ? track(a + 0.01D, 0.0F).sub(track(a, 0.0F)).normalize() : gateDir();
        dark.beam(new Vector3f(dir).mul(-7.0F).add(n), new Vector3f(dir).mul(5.0F).add(n), 0.9F, 0.3F, 0xF0060608, 0xF0101014);
        dark.end();

        if (s >= LAPS_FROM && s < RELEASE) {
            Film.lapLabel = Math.min(7, (int) Math.floor(laps) + 1);
            Film.charge = clamp01((s - LAPS_FROM) / (RELEASE - LAPS_FROM));
        }
        Film.label(sc, p.center, sc.screenRadius(p.center, p.radius) * 0.75F, "jupiter", CYAN, ramp(s, 2.7F, 3.0F) * (1.0F - ramp(s, 4.6F, 5.0F)));
        Film.label(sc, track(2.4D, 0.0F), 8.0F, "ring", HOT, ramp(s, 3.4F, 3.8F) * (1.0F - ramp(s, 5.6F, 6.0F)));
        Film.title("gungnir", ramp(s, 3.5F, 3.9F) * (1.0F - ramp(s, 5.0F, 5.4F)));
        sc.end();
    }

    // ------------------------------------------------------------------ 3: down onto the target

    private static void descent(Canvas c, float s, float fade) {
        float u = clamp01((s - 8.45F) / 3.55F);
        float na = 6000.0F * (float) Math.pow(1.0F - u, 2.4D);
        Vector3f t = Film.onEarth(0.0F);
        Vector3f needle = Film.onEarth(na);
        Vector3f eye = Film.onEarth(na * 1.5F + 0.7F).add(new Vector3f(Film.EAST).mul(na * 0.22F + 0.5F)).add(new Vector3f(Film.NORTH).mul(na * 0.12F + 0.45F));
        Cam cam = new Cam(eye, Cam.lerp(new Vector3f(), t, ramp(na, 400.0F, 20.0F)), Film.NORTH).lens(48.0F, Math.max(0.01F, na * 0.01F), 1.0E6F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.stars = 0.9F;
        sky.nebulaA = 0x30180C;
        sc.sky(sky);
        sc.planet(Film.earth(false, EMBER, 1.0F, s));
        Soft g = sc.soft(true);
        // the ember mark on the ground: rings and a column
        Vector3f e = Film.EAST, nn = Film.NORTH;
        for (int i = 0; i < 3; i++) {
            float pulse = 0.7F + 0.3F * (float) Math.sin(s * 8.0F + i);
            g.ring(t, e, nn, 0.35F + i * 0.4F, 0.03F + 0.02F * i, Fx.argb(HOT, 0.8F * pulse), 64);
        }
        Film.laser(g, t, Film.TARGET, EMBER, 0.8F, 0.04F + na * 0.01F);
        // the needle, burning through the air when it gets there
        float air = ramp(na, 6.0F, 1.0F);
        Vector3f tail = Film.onEarth(na + 30.0F + na * 0.4F);
        g.beam(tail, needle, 0.2F + na * 0.002F, 0.6F + na * 0.004F + air * 0.8F, Fx.argb(EMBER, 0.0F), Fx.argb(HOT, 0.6F + 0.4F * air));
        g.glowScreen(needle, 0.012F + 0.03F * air, Fx.argb(HOT, 0.6F + 0.4F * air), true);
        if (air > 0.0F) g.glowScreen(needle, 0.045F * air, Fx.argb(EMBER, 0.3F * air), false);
        Random r = new Random((long) (s * 30.0F));
        for (int i = 0; i < 24 * air; i++) {
            Vector3f d = v((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).mul(0.4F);
            Vector3f p0 = new Vector3f(needle).add(d), p1 = new Vector3f(p0).add(new Vector3f(Film.TARGET).mul(1.5F + r.nextFloat() * 2.0F));
            g.line(p1, p0, 0.0008F, 0.002F, Fx.argb(HOT, 0.0F), Fx.argb(0xFFF0D0, 0.8F * air));
        }
        g.end();
        Soft dark = sc.soft(false);
        dark.beam(Film.onEarth(na + 1.2F), needle, 0.05F, 0.025F, 0xF0060608, 0xF0101014);
        dark.end();
        Film.label(sc, new Vector3f(), Math.max(8.0F, sc.screenRadius(new Vector3f(), Film.EARTH) + 4.0F), "earth", CYAN,
                ramp(na, 3000.0F, 2000.0F) * (1.0F - ramp(na, 300.0F, 150.0F)));
        Film.label(sc, t, 8.0F, "target", EMBER, ramp(na, 200.0F, 60.0F));
        sc.end();
    }

    private FilmGungnir() {
    }
}
