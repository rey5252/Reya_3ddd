package com.reya.starfall.client;

import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.reya.starfall.client.Cam.clamp01;
import static com.reya.starfall.client.Cam.ramp;
import static com.reya.starfall.client.Cam.smoother;
import static com.reya.starfall.client.Cam.v;

/**
 * SS-01's film, twelve seconds: up the laser from the target, out past the Moon and Saturn, the solar system
 * shrinking to a point, the Milky Way from above, then down the laser to the railgun beyond the galaxy's rim. The
 * camera flies along the railgun while it charges and stays with it as it fires, then rides the beam home.
 */
final class FilmRailgun {
    private static final int RED = ClientStrike.RED, CYAN = 0x5FD8EC;

    static void draw(Canvas c, float s) {
        float toSaturn = ramp(s, 2.45F, 2.75F), toSun = ramp(s, 4.05F, 4.35F), toGalaxy = ramp(s, 5.55F, 5.85F);
        float toGun = ramp(s, 7.3F, 7.6F), toHome = ramp(s, 11.7F, 11.85F);
        Film.labelScale = 1.0F - toSaturn;
        if (s < 2.75F) earth(c, s);
        Film.labelScale = toSaturn * (1.0F - toSun);
        if (s > 2.45F && s < 4.35F) saturn(c, s, toSaturn);
        Film.labelScale = toSun * (1.0F - toGalaxy);
        if (s > 4.05F && s < 5.85F) solarSystem(c, s, toSun);
        Film.labelScale = toGalaxy * (1.0F - toGun);
        if (s > 5.55F && s < 7.6F) galaxy(c, s, toGalaxy);
        Film.labelScale = toGun * (1.0F - toHome);
        if (s > 7.3F) railgun(c, s, toGun);
        Film.labelScale = toHome;
        if (s > 11.7F) home(c, s, toHome);
        Film.labelScale = 1.0F;
        // the hand-overs: a breath of light while one shot gives way to the next
        float flash = bump(s, 7.45F, 0.18F) * 0.55F + bump(s, 11.05F, 0.12F) * 0.85F;
        if (flash > 0.0F) {
            c.mode(false);
            c.rect(0, 0, c.w, c.h, Fx.argb(0xFFE4E0, Math.min(1.0F, flash)));
        }
        if (s > 11.88F) {
            c.mode(false);
            c.rect(0, 0, c.w, c.h, Fx.argb(0xFFFFFF, clamp01((s - 11.88F) / 0.12F)));
        }
        c.flush();
    }

    /** 1 at {@code at}, falling to 0 a {@code half} either side. */
    static float bump(float s, float at, float half) {
        float d = Math.abs(s - at) / half;
        return d >= 1.0F ? 0.0F : 1.0F - d * d * (3.0F - 2.0F * d);
    }

    // ------------------------------------------------------------------ 1: up from the target, out past the Moon

    private static void earth(Canvas c, float s) {
        float[] at = {0.0F, 0.46F, 0.92F, 1.38F, 1.83F, 2.29F, 2.75F};
        float alt = (float) Math.exp(Cam.keyed1(s, at, ln(0.15F), ln(0.7F), ln(4.0F), ln(32.0F), ln(230.0F), ln(1500.0F), ln(7000.0F)));
        float look = smoother(s, 0.5F, 1.5F);
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.milky = 0.7F;
        sky.nebula = 0.7F;
        sky.bandStrength = 0.55F;
        Scene3D.Planet moon = moon();
        Scene3D sc = Film.opening(c, alt, look, false, RED, s, 1.0F, sky, moon);
        float er = sc.screenRadius(new Vector3f(), Film.EARTH);
        Film.label(sc, Film.onEarth(0.0F), 7.0F, "target", RED, ramp(alt, 20.0F, 60.0F) * (1.0F - ramp(alt, 600.0F, 1200.0F)));
        Film.label(sc, new Vector3f(), Math.max(8.0F, er + 4.0F), "earth", CYAN, ramp(alt, 300.0F, 700.0F));
        Film.label(sc, moon.center, Math.max(6.0F, sc.screenRadius(moon.center, moon.radius) + 3.0F), "moon", CYAN, ramp(alt, 2600.0F, 4200.0F));
        // the sun, low in the frame's corner once we're out
        Soft light = sc.soft(true);
        Vector3f sun = new Vector3f(Film.SUN_DAY).mul(60000.0F);
        light.glowScreen(sun, 0.05F, Fx.argb(0xFFF4E0, 0.9F * ramp(alt, 8.0F, 40.0F)), true);
        light.flare(sun, 0.35F, 0.0016F, 0.0F, Fx.argb(0xFFF0E0, 0.5F * ramp(alt, 8.0F, 40.0F)));
        light.end();
        sc.end();
    }

    static Scene3D.Planet moon() {
        Scene3D.Planet p = new Scene3D.Planet();
        p.kind = Scene3D.Planet.MOON;
        p.radius = 27.0F;
        p.center.set(new Vector3f(Film.EAST).mul(-1400.0F).add(new Vector3f(Film.NORTH).mul(1700.0F)).add(new Vector3f(Film.TARGET).mul(600.0F)));
        p.sun.set(Film.SUN_DAY);
        p.atmosphereHeight = 0.0F;
        return p;
    }

    private static float ln(float x) {
        return (float) Math.log(x);
    }

    // ------------------------------------------------------------------ 2: past Saturn

    private static void saturn(Canvas c, float s, float fade) {
        float q = clamp01((s - 2.45F) / 1.9F);
        float m = q * 0.85F + smoother(q, 0.0F, 1.0F) * 0.15F;
        Vector3f eye = v(-700.0F + 1300.0F * m, 80.0F - 35.0F * m, 720.0F - 140.0F * m);
        Vector3f look = v(-160.0F + 320.0F * m, 0.0F, 0.0F);
        Cam cam = new Cam(eye, look).lens(46.0F, 1.0F, 1.0E6F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.milky = 0.9F;
        sky.nebulaA = 0x1A2448;
        sky.nebulaB = 0x2A1430;
        sky.seed = 5.0F;
        sky.bandStrength = 0.6F;
        sc.sky(sky);
        Scene3D.Planet p = new Scene3D.Planet();
        p.kind = Scene3D.Planet.SATURN;
        p.radius = 100.0F;
        p.tilt = 0.47F;
        p.spin = 0.3F + s * 0.05F;
        p.sun.set(0.6F, 0.75F, -0.3F).normalize();
        p.atmosphere = 0xFFD9A0;
        p.atmosphereHeight = 0.012F;
        p.ring = true;
        p.time = s;
        sc.planet(p);
        Soft light = sc.soft(true);
        // the laser runs past it towards the railgun
        Vector3f from = v(-1.0E5F, -9000.0F, 330.0F + 5000.0F), dir = v(1.0F, 0.09F, -0.05F);
        Film.laser(light, from, dir, RED, 1.0F, 1.6F);
        Stars.warp(sc, light, v(680.0F, -18.0F, -74.0F), 700.0F, 0.05F, 0.8F, 0xD8E4FF);
        light.end();
        Film.label(sc, p.center, sc.screenRadius(p.center, p.radius) * 0.9F, "saturn", CYAN, ramp(q, 0.15F, 0.35F));
        sc.end();
    }

    // ------------------------------------------------------------------ 3: the solar system falling away

    private static final float[] ORBITS = {3.9F, 7.2F, 10.0F, 15.2F, 52.0F, 95.4F, 192.0F, 301.0F};
    private static final float[] PHASE = {0.4F, 2.2F, 0.9F, 4.1F, 5.3F, 1.7F, 3.3F, 2.6F};

    private static void solarSystem(Canvas c, float s, float fade) {
        float q = clamp01((s - 4.05F) / 1.8F);
        float dist = Cam.expLerp(140.0F, 4.0E5F, smoother(q, 0.0F, 1.0F) * 0.7F + q * 0.3F);
        float az = 0.5F + q * 0.6F, el = 0.62F - q * 0.15F;
        Vector3f eye = v((float) (Math.cos(az) * Math.cos(el)) * dist, (float) Math.sin(el) * dist, (float) (Math.sin(az) * Math.cos(el)) * dist);
        Cam cam = new Cam(eye, v(0.0F, 0.0F, 0.0F)).lens(50.0F, Math.max(0.05F, dist * 0.002F), dist * 40.0F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.milky = 0.8F;
        sky.seed = 9.0F;
        sky.band.set(0.2F, 0.3F, 1.0F);
        sky.bandStrength = 0.9F;
        sky.nebula = 0.5F;
        sc.sky(sky);
        Soft light = sc.soft(true);
        Vector3f x = v(1.0F, 0.0F, 0.0F), z = v(0.0F, 0.0F, 1.0F);
        Vector3f earth = null;
        for (int i = 0; i < ORBITS.length; i++) {
            float r = ORBITS[i];
            float onScreen = sc.screenRadius(new Vector3f(), r);
            float k = ramp(onScreen, 6.0F, 30.0F);
            if (k <= 0.0F) continue;
            light.ring(new Vector3f(), x, z, r, r * 0.004F + dist * 0.0009F, Fx.argb(CYAN, 0.55F * k), 160);
            Vector3f pos = v((float) Math.cos(PHASE[i]) * r, 0.0F, (float) Math.sin(PHASE[i]) * r);
            light.glowScreen(pos, 0.006F, Fx.argb(i == 2 ? 0x9FC8FF : 0xFFE8C8, 0.9F * k), true);
            if (i == 2) earth = pos;
            String key = i == 2 ? "earth_orbit" : i == 4 ? "jupiter_orbit" : i == 5 ? "saturn_orbit" : i == 7 ? "neptune_orbit" : null;
            if (key != null) Film.label(sc, pos, 5.0F, key, CYAN, k * (1.0F - ramp(onScreen, 900.0F, 1600.0F)));
        }
        // the sun: a star with a hot core and spikes
        light.glowScreen(new Vector3f(), 0.05F, Fx.argb(0xFFF0D0, 0.95F), true);
        light.glowScreen(new Vector3f(), 0.012F, Fx.argb(0xFFFFFF, 1.0F), true);
        light.flare(new Vector3f(), 0.3F, 0.0014F, 0.0F, Fx.argb(0xFFE8C8, 0.6F));
        light.flare(new Vector3f(), 0.12F, 0.0012F, (float) (Math.PI / 2), Fx.argb(0xFFE8C8, 0.5F));
        // the Oort cloud: a shell of ice far out, thousands of specks with a haze where they crowd at its edge
        float oort = ramp(dist, 2.0E4F, 9.0E4F);
        if (oort > 0.0F) {
            Random r = new Random(31L);
            for (int i = 0; i < 1800; i++) {
                Vector3f d = v((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
                d.mul(8.0E4F * (1.0F + 0.07F * (float) r.nextGaussian()));
                float b = 0.35F + 0.65F * r.nextFloat();
                light.glowScreen(d, 0.0032F + 0.002F * b, Fx.argb(i % 7 == 0 ? 0xFFFFFF : 0xB8D8FF, 0.85F * b * oort), i % 7 == 0);
            }
            float shell = (float) Math.atan2(8.0E4F, dist) / (float) Math.toRadians(25.0D);
            light.glowScreen(new Vector3f(), Math.min(1.2F, shell * 0.55F), Fx.argb(0x6A90C8, 0.07F * oort), false);
        }
        // the laser leaving Earth for the railgun
        if (earth != null) Film.laser(light, earth, v(-0.3F, 0.55F, -0.78F), RED, 1.0F, dist * 0.002F);
        light.end();
        Film.label(sc, new Vector3f(), 6.0F, "sol", 0xFFE8B0, 1.0F - ramp(dist, 3.0E4F, 1.0E5F));
        Film.label(sc, v(0.0F, 0.0F, 0.0F), 26.0F, "oort", CYAN, oort * ramp(q, 0.7F, 0.85F));
        sc.end();
    }

    // ------------------------------------------------------------------ 4: the Milky Way, and the laser out to the rim

    static final Vector3f SOL = v((float) Math.cos(2.2D) * 520.0F, 0.0F, (float) Math.sin(2.2D) * 520.0F);
    static final Vector3f OUT = v(-0.25F, 0.55F, 0.8F).normalize();
    static final Vector3f GUN = new Vector3f(OUT).mul(1160.0F).add(SOL);

    private static void galaxy(Canvas c, float s, float fade) {
        Vector3f eye, look;
        if (s < 6.9F) {
            float q = smoother(s, 5.55F, 6.9F);
            eye = Cam.path(q, new Vector3f(SOL).add(160.0F, 110.0F, 260.0F), v(400.0F, 700.0F, 1400.0F), v(700.0F, 1250.0F, 1800.0F));
            look = Cam.lerp(v(-60.0F, -40.0F, 0.0F), v(-200.0F, 120.0F, 300.0F), q);
        } else {
            // turn to the red light at the rim and rush down the laser to it
            Vector3f far = v(700.0F, 1250.0F, 1800.0F);
            float turn = smoother(s, 6.9F, 7.15F);
            float rush = clamp01((s - 7.05F) / 0.55F);
            float d = Cam.expLerp(GUN.distance(far), 2.0F, rush * rush * (3.0F - 2.0F * rush));
            Vector3f toward = new Vector3f(far).sub(GUN).normalize();
            eye = rush > 0.0F ? new Vector3f(toward).mul(d).add(GUN) : far;
            look = Cam.lerp(v(-200.0F, 120.0F, 300.0F), GUN, turn);
        }
        Cam cam = new Cam(eye, look).lens(50.0F, 0.5F, 1.0E6F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.seed = 13.0F;
        sky.bandStrength = 0.15F;
        sky.nebula = 0.35F;
        sky.stars = 0.8F;
        sc.sky(sky);
        sc.galaxy(new Vector3f(), v(1.0F, 0.0F, 0.0F), v(0.0F, 0.0F, 1.0F), 1000.0F, 0.3F + s * 0.02F, 1.0F);
        Soft light = sc.soft(true);
        Film.laser(light, SOL, OUT, RED, 1.0F, 3.0F);
        light.glowScreen(SOL, 0.01F, Fx.argb(0xFFE0C0, 0.9F), true);
        // the railgun out past the rim: a red star with long spikes
        float gk = 0.6F + 0.4F * (float) Math.sin(s * 9.0F);
        light.glowScreen(GUN, 0.03F, Fx.argb(RED, 0.9F), true);
        light.glowScreen(GUN, 0.008F, Fx.argb(0xFFFFFF, 0.9F), true);
        light.flare(GUN, 0.22F, 0.0018F, 0.0F, Fx.argb(RED, 0.6F * gk));
        light.flare(GUN, 0.12F, 0.0015F, (float) (Math.PI / 2), Fx.argb(RED, 0.5F * gk));
        if (s > 7.05F) {
            Vector3f vel = new Vector3f(GUN).sub(eye).normalize().mul(-3000.0F);
            Stars.warp(sc, light, vel.negate(), 60.0F, 0.02F, ramp(s, 7.05F, 7.2F), 0xFFD8D8);
        }
        light.end();
        Film.label(sc, v(0.0F, 0.0F, 0.0F), 30.0F, "milky_way", CYAN, ramp(s, 6.0F, 6.3F) * (1.0F - ramp(s, 6.9F, 7.05F)));
        Film.label(sc, SOL, 5.0F, "sol_core", RED, ramp(s, 5.75F, 6.0F) * (1.0F - ramp(s, 6.9F, 7.0F)));
        Film.label(sc, GUN, 6.0F, "railgun", RED, ramp(s, 6.3F, 6.6F) * (1.0F - ramp(s, 7.4F, 7.55F)));
        sc.end();
    }

    // ------------------------------------------------------------------ 5: the railgun, its charge and the shot

    private static final float[] T_CAM = {7.3F, 8.0F, 8.8F, 9.7F, 10.5F, 11.0F, 11.45F};
    private static final Vector3f[] EYE = {
            v(-210.0F, 80.0F, -480.0F), v(-90.0F, 36.0F, -150.0F), v(-26.0F, 13.0F, -12.0F), v(-18.0F, 9.0F, 66.0F),
            v(-14.0F, 6.0F, 106.0F), v(-13.0F, 5.0F, 116.0F), v(-9.0F, 3.6F, 121.0F)};
    private static final Vector3f[] LOOK = {
            v(0.0F, 0.0F, 30.0F), v(0.0F, 2.0F, 40.0F), v(0.0F, 3.0F, 60.0F), v(0.0F, 1.0F, 104.0F),
            v(0.0F, 0.0F, 124.0F), v(0.0F, 0.0F, 132.0F), v(0.0F, 0.0F, 170.0F)};
    static final Vector3f MUZZLE = v(0.0F, 0.0F, 127.0F);

    private static void railgun(Canvas c, float s, float fade) {
        float charge = clamp01((s - 9.0F) / 2.0F);
        float fire = clamp01((s - 11.0F) / 0.45F);
        if (s >= 9.0F && s < 11.0F) Film.charge = charge;
        Vector3f eye = Cam.keyed(s, T_CAM, EYE), look = Cam.keyed(s, T_CAM, LOOK);
        if (s > 11.45F) {
            // after the shot the camera swings in behind the muzzle and races down the beam
            float q = clamp01((s - 11.45F) / 0.55F);
            float k = q * q * (3.0F - 2.0F * q);
            eye = v(Cam.lerp(-9.0F, -1.2F, k), Cam.lerp(3.6F, 1.0F, k), 121.0F + 40.0F * q + 2400.0F * q * q * q);
            look = v(0.0F, 0.0F, eye.z + Cam.lerp(50.0F, 3000.0F, k));
        }
        // the shot kicks the camera
        if (s > 11.0F && s < 11.6F) {
            float k = (1.0F - clamp01((s - 11.0F) / 0.6F)) * 0.6F;
            eye.add((float) Math.sin(s * 91.0F) * k, (float) Math.cos(s * 77.0F) * k, 0.0F);
        }
        Cam cam = new Cam(eye, look).lens(s < 8.6F ? 40.0F : 48.0F, 0.1F, 1.0E6F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.seed = 21.0F;
        sky.nebulaA = 0x241A3A;
        sky.nebulaB = 0x3A1218;
        sky.bandStrength = 0.25F;
        sky.nebula = 0.6F;
        sc.sky(sky);
        // the galaxy hangs huge ahead, where the shot is going
        sc.galaxy(v(40000.0F, -52000.0F, 190000.0F), v(1.0F, 0.0F, 0.0F), v(0.0F, 0.42F, 1.0F), 120000.0F, 0.6F, 1.0F);
        Scene3D.Light light = new Scene3D.Light();
        light.sun.set(-0.5F, 0.6F, 0.6F).normalize();
        light.sunColor = 0xFFF1E0;
        light.sunStrength = 1.35F;
        light.fill = 0x262C46;
        light.glow.set(MUZZLE);
        light.glowColor = 0xFF3A2C;
        light.glowStrength = 3.0F * charge * charge + 9.0F * fire * (1.0F - fire * 0.5F);
        light.glowRange = 22.0F + 30.0F * fire;
        light.charge = charge;
        light.time = s;
        sc.mesh(Mesh.RAILGUN, new Matrix4f(), light);
        Soft glow = sc.soft(true);
        lights(glow, s);
        if (charge > 0.0F) charging(glow, s, charge, fire);
        if (fire > 0.0F) firing(glow, fire);
        if (s > 11.45F) {
            float k = ramp(s, 11.45F, 11.7F);
            Stars.warp(sc, glow, v(0.0F, 0.0F, 3000.0F * k), 50.0F, 0.02F, k, 0xFFC0C0);
        }
        glow.end();
        Film.label(sc, v(0.0F, 2.0F, 40.0F), 18.0F, "railgun_close", RED, ramp(s, 7.5F, 7.8F) * (1.0F - ramp(s, 8.3F, 8.6F)));
        Film.title("railgun", ramp(s, 7.7F, 8.1F) * (1.0F - ramp(s, 9.0F, 9.4F)));
        sc.end();
    }

    /** Running lights along the hull, the mast's tip and the radiators' corners, blinking. */
    private static void lights(Soft g, float s) {
        for (int i = 0; i < 12; i++) {
            boolean on = ((int) (s * 3.0F) + i) % 4 != 0;
            if (on) g.glow(v(0.0F, 7.5F, 6.0F + i * 6.5F), 0.35F, Fx.argb(0xFFF0E0, 0.9F), true);
        }
        float blink = ((int) (s * 2.0F)) % 2 == 0 ? 1.0F : 0.15F;
        g.glow(v(0.0F, 24.2F, -7.0F), 0.8F, Fx.argb(0xFF4030, 0.95F * blink), true);
        g.glow(v(46.0F, 0.0F, -7.0F), 0.6F, Fx.argb(0x60FF80, 0.9F * (1.15F - blink)), true);
        g.glow(v(-46.0F, 0.0F, -7.0F), 0.6F, Fx.argb(0xFF4030, 0.9F * (1.15F - blink)), true);
        g.glow(v(0.0F, 0.0F, -15.8F), 3.0F, Fx.argb(0xFF8A60, 0.35F), true);
    }

    /** Energy gathering: the coils burning in turn, arcs jumping between the rails, sparks pulled into the muzzle. */
    private static void charging(Soft g, float s, float charge, float fire) {
        float k = charge * (1.0F - fire);
        for (int i = 0; i < 7; i++) {
            float lit = clamp01((charge - (0.1F + i * 0.12F)) / 0.06F);
            if (lit <= 0.0F) continue;
            float flick = 0.75F + 0.25F * (float) Math.sin(s * 40.0F + i * 1.7F);
            g.ring(v(0.0F, 0.0F, 96.0F + i * 4.6F), v(1.0F, 0.0F, 0.0F), v(0.0F, 1.0F, 0.0F), 3.95F, 0.55F,
                    Fx.argb(0xFF4A3A, 0.55F * lit * flick), 40);
        }
        g.beam(v(0.0F, 0.0F, 93.0F), MUZZLE, 0.5F + charge, 0.8F + 1.6F * charge, Fx.argb(0xFF3020, 0.25F * k), Fx.argb(0xFF6A50, 0.6F * k));
        g.glow(MUZZLE, 1.0F + 5.0F * charge * charge, Fx.argb(0xFFD0C8, 0.9F * charge), true);
        g.glow(MUZZLE, 6.0F + 26.0F * charge * charge, Fx.argb(0xFF2A20, 0.4F * charge), false);
        // arcs between the rails, a new shape every few frames
        long frame = (long) Math.floor(s * 18.0F);
        Random r = new Random(frame * 31L + 7L);
        int arcs = 2 + Math.round(charge * 5.0F);
        for (int i = 0; i < arcs; i++) {
            float z0 = 96.0F + r.nextFloat() * 30.0F * charge, z1 = z0 + (r.nextFloat() - 0.5F) * 8.0F;
            double a0 = Math.PI / 6 + r.nextInt(6) * Math.PI / 3, a1 = a0 + (r.nextBoolean() ? 1 : -1) * Math.PI / 3 * (1 + r.nextInt(2));
            Vector3f p0 = v((float) Math.cos(a0) * 2.6F, (float) Math.sin(a0) * 2.6F, z0);
            Vector3f p1 = v((float) Math.cos(a1) * 2.6F, (float) Math.sin(a1) * 2.6F, z1);
            g.bolt(p0, p1, 0.06F, Fx.argb(0xFFE0E0, 0.9F * k), frame * 13L + i, 0.45F, 5);
            g.bolt(p0, p1, 0.22F, Fx.argb(0xFF3020, 0.5F * k), frame * 13L + i, 0.45F, 5);
        }
        // sparks spiralling into the muzzle
        for (int i = 0; i < 48; i++) {
            float ph = (s * 0.9F + i / 48.0F) % 1.0F;
            float rr = 14.0F * (1.0F - ph) * (0.5F + 0.5F * charge);
            float a = i * 2.399F + ph * 6.0F;
            Vector3f p = v((float) Math.cos(a) * rr, (float) Math.sin(a) * rr, 127.0F + (1.0F - ph) * 10.0F);
            g.glow(p, 0.12F + 0.1F * ph, Fx.argb(0xFFB0A0, 0.8F * ph * k), true);
        }
    }

    /** The shot: the beam out of the muzzle, shock rings, the flash and a spray of sparks. */
    private static void firing(Soft g, float fire) {
        float open = clamp01(fire * 5.0F);
        Vector3f far = v(0.0F, 0.0F, 1.0E5F);
        g.beam(MUZZLE, far, 22.0F * open, 22.0F * open, Fx.argb(0xFF2A1E, 0.18F), Fx.argb(0xFF2A1E, 0.05F));
        g.beam(MUZZLE, far, 7.0F * open, 7.0F * open, Fx.argb(0xFF4A3A, 0.55F), Fx.argb(0xFF4A3A, 0.25F));
        g.beam(MUZZLE, far, 2.6F * open, 2.6F * open, Fx.argb(0xFFB0A8, 0.9F), Fx.argb(0xFFB0A8, 0.5F));
        g.beam(MUZZLE, far, 0.9F * open, 0.9F * open, Fx.argb(0xFFFFFF, 1.0F), Fx.argb(0xFFFFFF, 0.8F));
        for (int i = 0; i < 3; i++) {
            float q = clamp01(fire * 1.6F - i * 0.22F);
            if (q <= 0.0F || q >= 1.0F) continue;
            g.ring(v(0.0F, 0.0F, 128.0F + q * 14.0F + i * 3.0F), v(1.0F, 0.0F, 0.0F), v(0.0F, 1.0F, 0.0F), 5.0F + q * 46.0F,
                    0.8F + q * 3.0F, Fx.argb(0xFFD8D0, 0.8F * (1.0F - q)), 64);
        }
        float flash = 1.0F - clamp01(fire * 2.2F);
        g.glow(MUZZLE, 60.0F, Fx.argb(0xFFF0F0, 0.9F * flash), true);
        g.glowScreen(MUZZLE, 0.5F, Fx.argb(0xFF5040, 0.5F * (0.4F + 0.6F * flash)), false);
        Random r = new Random(99L);
        for (int i = 0; i < 70; i++) {
            Vector3f d = v((float) r.nextGaussian(), (float) r.nextGaussian(), Math.abs((float) r.nextGaussian()) * 0.6F).normalize();
            float dist = fire * (30.0F + r.nextFloat() * 50.0F);
            Vector3f p = new Vector3f(d).mul(dist).add(MUZZLE);
            Vector3f tail = new Vector3f(d).mul(dist * 0.8F).add(MUZZLE);
            g.line(tail, p, 0.001F, 0.003F, Fx.argb(0xFFC0A0, 0.0F), Fx.argb(0xFFD0B0, 0.9F * (1.0F - fire)));
        }
    }

    // ------------------------------------------------------------------ 6: down the beam onto the target

    private static void home(Canvas c, float s, float fade) {
        float q = clamp01((s - 11.7F) / 0.3F);
        float alt = Cam.expLerp(6000.0F, 3.0F, q);
        Cam cam = Film.above(alt, 1.0F, 1.0F);
        Scene3D sc = new Scene3D(c.w, c.h, cam);
        sc.fade = fade;
        Scene3D.Sky sky = new Scene3D.Sky();
        sky.milky = 0.6F;
        sky.stars = 0.8F;
        sc.sky(sky);
        sc.planet(Film.earth(false, RED, 1.5F, s));
        Soft g = sc.soft(true);
        Vector3f t = Film.onEarth(0.0F);
        Vector3f top = Film.onEarth(1.0E5F);
        g.beam(top, t, 3.0F + alt * 0.04F, 1.5F, Fx.argb(0xFF4030, 0.5F), Fx.argb(0xFF6050, 0.9F));
        g.line(top, t, 0.01F, 0.008F, Fx.argb(0xFFFFFF, 0.9F), Fx.argb(0xFFFFFF, 1.0F));
        g.glow(t, 2.0F + alt * 0.05F, Fx.argb(0xFFE0D8, 0.9F), true);
        Stars.warp(sc, g, new Vector3f(Film.TARGET).mul(-4000.0F), 40.0F, 0.02F, 0.8F, 0xFFC8C8);
        g.end();
        sc.end();
    }

    private FilmRailgun() {
    }
}
