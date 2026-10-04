package com.reya.starfall.client;

import com.reya.starfall.BigDipper;
import com.reya.starfall.Skill;
import com.reya.starfall.Sounds;
import com.reya.starfall.network.StrikePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/** One strike as this client sees it: everything is timed from the strike's start in game ticks. */
final class ClientStrike {
    static final int RED = 0xFF2A2A, RED_SOFT = 0xFF6A6A, PINK = 0xFFE0E0, WHITE = 0xFFFFFF;
    static final int EMBER = 0xFF7A1E, EMBER_HOT = 0xFFD27A;
    static final int VIOLET = 0xA060FF, VIOLET_HOT = 0xE8D0FF, FIRE = 0xFF6A2A, BOLT = 0xB8D4FF;
    /** How long the melted wall of SS-01's shaft takes to cool after the beam is gone, in ticks. */
    static final int AFTERGLOW = 2400;
    /** How long this client keeps the shaft dark inside after the beam, in ticks. */
    static final int SHAFT_LIFE = 6000;
    /** Points around the shaft's rim where the client looks up how high the land is. */
    private static final int RIM = 256;

    final int id;
    final Skill skill;
    final BlockPos target;
    final long start;
    final float radius, yaw, width;
    final boolean caster;
    final double[] nx, ny, nz;
    final float[] sizes;
    private int lastTick = -1;
    private final RandomSource random = RandomSource.create();
    private final double[] rim = new double[RIM];
    private int rimAt = Integer.MIN_VALUE;

    ClientStrike(StrikePacket p) {
        id = p.id();
        skill = Skill.byIndex(p.skill());
        target = p.target();
        start = p.start();
        radius = p.radius();
        yaw = p.yaw();
        width = p.width();
        caster = p.caster();
        int n = p.nodes().length / 3;
        nx = new double[n];
        ny = new double[n];
        nz = new double[n];
        for (int i = 0; i < n; i++) {
            nx[i] = p.nodes()[i * 3] + 0.5D;
            ny[i] = p.nodes()[i * 3 + 1] + 1.0D;
            nz[i] = p.nodes()[i * 3 + 2] + 0.5D;
        }
        sizes = p.sizes();
        Arrays.fill(rim, groundY());
    }

    double cx() {
        return target.getX() + 0.5D;
    }

    double cz() {
        return target.getZ() + 0.5D;
    }

    double groundY() {
        return target.getY() + 1.0D;
    }

    boolean expired(long gameTime) {
        int life = skill.duration + 40;
        if (skill == Skill.RAILGUN) life = Math.max(life, Skill.MARK + 100 + SHAFT_LIFE);
        return gameTime - start > life;
    }

    /** Gungnir's boom reaches the listener this many ticks after the impact: it outruns its own sound. */
    private int boomDelay(Vec3 listener) {
        double d = Math.hypot(listener.x - cx(), listener.z - cz());
        return 10 + (int) (d / 17.0D);
    }

    // ------------------------------------------------------------------ ticking: sounds and particles

    void tick(ClientLevel level, Vec3 listener) {
        int now = (int) (level.getGameTime() - start);
        for (int t = Math.max(lastTick + 1, now - 20); t <= now; t++) at(level, listener, t);
        lastTick = Math.max(lastTick, now);
        if (skill == Skill.SEVEN_STARS) refineNodes(level, now);
        if (skill == Skill.RAILGUN && now >= Skill.MARK - 20 && now - rimAt >= 10) {
            rimAt = now;
            refreshRim(level);
        }
    }

    /** Takes the height of the land along the shaft's rim from the client's own chunks, so its glow follows it. */
    private void refreshRim(ClientLevel level) {
        for (int i = 0; i < RIM; i++) {
            double a = Math.PI * 2.0D * i / RIM;
            int x = Mth.floor(cx() + Math.cos(a) * (radius + 1.0D)), z = Mth.floor(cz() + Math.sin(a) * (radius + 1.0D));
            if (level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                rim[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            }
        }
    }

    /** How hot the shaft's wall still is, 0..1: it takes over from the beam as the beam dies, then cools. */
    private float wallHeat(float bt) {
        float rise = Mth.clamp((bt - 50.0F) / 50.0F, 0.0F, 1.0F);
        float cool = 1.0F - Mth.clamp((bt - 100.0F) / AFTERGLOW, 0.0F, 1.0F);
        return rise * cool * cool;
    }

    /** Takes the ground height of each node from the client's own chunks, until its star has landed. */
    private void refineNodes(ClientLevel level, int now) {
        for (int i = 0; i < nx.length; i++) {
            if (now >= Skill.starImpact(i)) continue;
            int x = Mth.floor(nx[i]), z = Mth.floor(nz[i]);
            if (level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                ny[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            }
        }
    }

    private void at(ClientLevel level, Vec3 listener, int t) {
        switch (skill) {
            case RAILGUN -> railgunAt(level, listener, t);
            case GUNGNIR -> gungnirAt(level, listener, t);
            case SEVEN_STARS -> sevenAt(level, listener, t);
        }
    }

    private void railgunAt(ClientLevel level, Vec3 l, int t) {
        double d = Math.hypot(l.x - cx(), l.z - cz());
        float near = proximity(d, radius * 3.0D, radius * 25.0D);
        if (film()) {
            // the film's own sounds, for whoever pressed the button
            switch (t) {
                case 0 -> play(Sounds.FILM_UPLINK.get(), 1.0F, 0.7F);
                case 4 -> play(Sounds.FILM_LOCK.get(), 1.0F, 0.6F);
                case 22, 50, 82 -> play(Sounds.FILM_WHOOSH.get(), 0.85F + t * 0.002F, 0.7F);
                case 112, 141 -> play(Sounds.FILM_WARP.get(), t == 112 ? 0.9F : 1.05F, 0.75F);
                case 154 -> play(Sounds.FILM_TITLE.get(), 1.0F, 0.9F);
                case 174 -> play(Sounds.RAILGUN_CHARGE.get(), 1.0F, 1.0F);
                case 190, 198, 205, 211, 216 -> play(Sounds.RAILGUN_ARC.get(), 0.8F + random.nextFloat() * 0.5F, 0.6F);
                case 220 -> play(Sounds.RAILGUN_FIRE.get(), 1.0F, 1.0F);
                case 229 -> play(Sounds.FILM_WARP.get(), 1.3F, 0.8F);
                default -> {
                }
            }
        }
        if (t == Skill.MARK) {
            float v = Math.max(0.25F, near);
            play(Sounds.RAILGUN_IMPACT.get(), 1.0F, v);
            play(Sounds.RAILGUN_BEAM.get(), 1.0F, v * 0.9F);
            if (!caster) play(Sounds.RAILGUN_FIRE.get(), 0.8F, v * 0.6F);
        } else if (t == Skill.MARK + 60) {
            play(Sounds.RAILGUN_BEAM.get(), 0.85F, Math.max(0.2F, near) * 0.6F);
        }
        if (t >= Skill.MARK && t < Skill.MARK + 70 && d < radius + 96.0D) {
            // sparks crawling up the edge of the beam where you can see them
            for (int i = 0; i < 12; i++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double x = cx() + Math.cos(a) * radius, z = cz() + Math.sin(a) * radius;
                if (Math.hypot(x - l.x, z - l.z) > 96.0D) continue;
                double y = l.y + (random.nextDouble() - 0.3D) * 40.0D;
                spark(level, random.nextBoolean() ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.LAVA, x, y, z);
            }
        }
        float heat = wallHeat(t - Skill.MARK);
        if (heat > 0.05F && d < radius + 96.0D) {
            // melt spitting off the lip and smoke rising from it, near you
            for (int i = 0; i < 10; i++) {
                int k = random.nextInt(RIM);
                double a = Math.PI * 2.0D * k / RIM;
                double x = cx() + Math.cos(a) * (radius - 0.3D), z = cz() + Math.sin(a) * (radius - 0.3D);
                if (Math.hypot(x - l.x, z - l.z) > 80.0D || random.nextFloat() > heat) continue;
                double y = rim[k] - random.nextDouble() * random.nextDouble() * 24.0D;
                spark(level, i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : i % 3 == 1 ? ParticleTypes.LAVA : ParticleTypes.FLAME, x, y, z);
            }
        }
    }

    private void gungnirAt(ClientLevel level, Vec3 l, int t) {
        double d = Math.hypot(l.x - cx(), l.z - cz());
        float near = proximity(d, radius * 2.0D, radius * 20.0D);
        if (film()) {
            switch (t) {
                case 0 -> play(Sounds.FILM_UPLINK.get(), 0.9F, 0.7F);
                case 4 -> play(Sounds.FILM_LOCK.get(), 0.9F, 0.6F);
                case 22, 44 -> play(Sounds.FILM_WHOOSH.get(), t == 22 ? 0.8F : 0.95F, 0.7F);
                case 66 -> play(Sounds.GUNGNIR_SPIN.get(), 1.0F, 0.9F);
                case 72 -> play(Sounds.FILM_TITLE.get(), 0.9F, 0.8F);
                case 160 -> play(Sounds.GUNGNIR_LAUNCH.get(), 1.0F, 1.0F);
                case 169 -> play(Sounds.FILM_WARP.get(), 0.8F, 0.7F);
                case 226 -> play(Sounds.GUNGNIR_FALL.get(), 1.0F, 0.9F);
                default -> {
                }
            }
        }
        if (t == Skill.MARK && near > 0.05F) play(Sounds.GUNGNIR_FALL.get(), 1.3F, near * 0.8F);
        // it strikes in silence: only the shock and the boom are heard, and the boom comes late
        int boom = Skill.GUNGNIR_IMPACT + boomDelay(l);
        if (t == boom) play(Sounds.GUNGNIR_BOOM.get(), 1.0F, Math.max(0.25F, near));
        int bt = t - Skill.GUNGNIR_IMPACT;
        float reach = width > 0 ? width : 2.0F;
        if (bt >= 0 && bt < Skill.SHOCK_TIME * 2) {
            double p = bt / (double) Skill.SHOCK_TIME;
            double rr = p <= 1.0D ? radius * easeOut(p) : radius * (1.0D + (reach - 1.0D) * (p - 1.0D));
            for (int i = 0; i < 28; i++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double x = cx() + Math.cos(a) * rr, z = cz() + Math.sin(a) * rr;
                if (Math.hypot(x - l.x, z - l.z) > 160.0D) continue;
                spark(level, i % 3 == 0 ? ParticleTypes.EXPLOSION : ParticleTypes.CAMPFIRE_COSY_SMOKE, x, groundY() + random.nextDouble() * 3.0D, z);
            }
        }
    }

    private void sevenAt(ClientLevel level, Vec3 l, int t) {
        if (film()) {
            switch (t) {
                case 0 -> play(Sounds.FILM_UPLINK.get(), 1.1F, 0.7F);
                case 4 -> play(Sounds.FILM_LOCK.get(), 1.1F, 0.6F);
                case 24, 48 -> play(Sounds.FILM_WHOOSH.get(), t == 24 ? 0.9F : 1.05F, 0.65F);
                case 64 -> play(Sounds.FILM_TITLE.get(), 1.1F, 0.8F);
                case 132 -> play(Sounds.SEVEN_ARRAY.get(), 1.0F, 0.9F);
                case 183 -> play(Sounds.FILM_WHOOSH.get(), 0.75F, 0.7F);
                default -> {
                }
            }
            for (int i = 0; i < 7; i++) {
                int wake = Math.round((2.9F + i * 0.42F) * 20.0F), beam = Math.round((9.55F + i * 0.17F) * 20.0F);
                float pitch = (float) (1.35D - 0.25D * BigDipper.size(i));
                if (t == wake) play(Sounds.SEVEN_WAKE.get(), pitch, 0.8F);
                if (t == beam) play(Sounds.SEVEN_BEAM.get(), 0.9F + i * 0.05F, 0.6F);
                if (t == beam + 7) play(Sounds.SEVEN_IMPACT.get(), 1.1F, 0.35F);
            }
        }
        for (int i = 0; i < nx.length; i++) {
            int impact = Skill.starImpact(i);
            double d = Math.hypot(l.x - nx[i], l.z - nz[i]);
            float near = proximity(d, sizes[i] * 3.0D, sizes[i] * 40.0D);
            if (t == impact - Skill.STAR_FALL) {
                play(Sounds.SEVEN_BEAM.get(), 1.0F, Math.max(0.3F, near));
            } else if (t == impact) {
                play(Sounds.SEVEN_IMPACT.get(), 0.9F + 0.04F * i, Math.max(0.2F, near));
                if (d < 200.0D) {
                    for (int k = 0; k < 40; k++) {
                        double a = random.nextDouble() * Math.PI * 2.0D, r = random.nextDouble() * sizes[i];
                        spark(level, k % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.DRAGON_BREATH,
                                nx[i] + Math.cos(a) * r, ny[i] + random.nextDouble() * 6.0D, nz[i] + Math.sin(a) * r);
                    }
                }
            }
        }
        for (int k = 0; k < BigDipper.LINES.length; k++) {
            if (t == Skill.lineStart(k)) play(Sounds.SEVEN_IGNITE.get(), 0.9F + 0.05F * k, 0.6F);
        }
        if (t == Skill.FLARE) play(Sounds.SEVEN_FLARE.get(), 1.0F, 1.0F);
    }

    /** This client's film is playing for this strike. */
    private boolean film() {
        return caster && Film.active();
    }

    private void spark(ClientLevel level, ParticleOptions type, double x, double y, double z) {
        level.addAlwaysVisibleParticle(type, x, y, z, (random.nextDouble() - 0.5D) * 0.2D, random.nextDouble() * 0.3D,
                (random.nextDouble() - 0.5D) * 0.2D);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        if (volume <= 0.01F) return;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.PLAYERS,
                Math.min(1.0F, volume), pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE,
                0.0D, 0.0D, 0.0D, true));
    }

    /** 1 within {@code full} blocks, falling to 0 at {@code none}. */
    static float proximity(double d, double full, double none) {
        if (d <= full) return 1.0F;
        if (d >= none) return 0.0F;
        return (float) (1.0D - (d - full) / (none - full));
    }

    static double easeOut(double p) {
        p = Math.max(0.0D, Math.min(1.0D, p));
        return 1.0D - (1.0D - p) * (1.0D - p) * (1.0D - p);
    }

    // ------------------------------------------------------------------ full-screen flash and shake

    /** The flash this strike throws over the screen right now (ARGB), or 0. */
    int flash(float t, Vec3 eye, boolean strobe) {
        switch (skill) {
            case RAILGUN -> {
                float bt = t - Skill.MARK;
                if (bt < 0 || bt >= 30) return 0;
                float k = proximity(Math.hypot(eye.x - cx(), eye.z - cz()), radius * 3.0D, radius * 10.0D);
                if (k <= 0) return 0;
                if (!strobe) return Fx.argb(WHITE, 0.6F * (1 - bt / 30F) * k);
                if (bt < 2) return Fx.argb(WHITE, k);
                if (bt < 4) return Fx.argb(0xFF2020, 0.9F * k);
                if (bt < 5) return Fx.argb(0x000000, 0.85F * k);
                return Fx.argb(WHITE, 0.8F * (1 - (bt - 5) / 25F) * k);
            }
            case GUNGNIR -> {
                float bt = t - Skill.GUNGNIR_IMPACT;
                if (bt < 0 || bt >= 20) return 0;
                float k = proximity(Math.hypot(eye.x - cx(), eye.z - cz()), radius * 3.0D, radius * 8.0D);
                if (k <= 0) return 0;
                if (bt < 3) return Fx.argb(0xFFE6C0, 0.9F * k);
                if (strobe && bt < 9 && ((int) bt & 2) != 0) return Fx.argb(0x000000, 0.7F * k);
                return Fx.argb(EMBER, 0.6F * (1 - (bt - 3) / 17F) * k);
            }
            default -> {
                int best = 0;
                for (int i = 0; i < nx.length; i++) {
                    float bt = t - Skill.starImpact(i);
                    if (bt < 0 || bt >= 12) continue;
                    float k = proximity(Math.hypot(eye.x - nx[i], eye.z - nz[i]), sizes[i] * 6.0D, sizes[i] * 20.0D);
                    if (k > 0) best = Fx.argb(VIOLET_HOT, 0.7F * (1 - bt / 12F) * k);
                }
                float ft = t - Skill.FLARE;
                if (ft >= 0 && ft < 30) {
                    float k = proximity(Math.hypot(eye.x - cx(), eye.z - cz()), radius * 1.5D, radius * 3.0D);
                    int flare = Fx.argb(VIOLET, 0.45F * (1 - ft / 30F) * k);
                    if (((flare >>> 24) & 0xFF) > ((best >>> 24) & 0xFF)) best = flare;
                }
                return best;
            }
        }
    }

    /** How hard this strike shakes the camera right now, in degrees. */
    float shake(float t, Vec3 eye) {
        switch (skill) {
            case RAILGUN -> {
                float bt = t - Skill.MARK;
                if (bt < 0 || bt >= 80) return 0;
                return 2.5F * (1 - bt / 80F) * proximity(Math.hypot(eye.x - cx(), eye.z - cz()), radius * 2.0D, radius * 8.0D);
            }
            case GUNGNIR -> {
                float bt = t - Skill.GUNGNIR_IMPACT - boomDelay(eye);
                if (bt < 0 || bt >= 40) return 0;
                return 2.2F * (1 - bt / 40F) * proximity(Math.hypot(eye.x - cx(), eye.z - cz()), radius * 2.0D, radius * 8.0D);
            }
            default -> {
                float a = 0;
                for (int i = 0; i < nx.length; i++) {
                    float bt = t - Skill.starImpact(i);
                    if (bt < 0 || bt >= 20) continue;
                    a += 1.4F * (1 - bt / 20F) * proximity(Math.hypot(eye.x - nx[i], eye.z - nz[i]), sizes[i] * 3.0D, sizes[i] * 25.0D);
                }
                return a;
            }
        }
    }

    // ------------------------------------------------------------------ the world's picture while it lands

    /**
     * How the world's picture reels while this strike lands: where it lands, how hard it smears out from there,
     * how far the colours part, the colour it drains to and how much, and how much it greys. Null when calm.
     */
    record Reel(Vec3 at, float zoom, float aberration, int tint, float tintAmount, float desaturate) {
        float weight() {
            return zoom * 4.0F + tintAmount + aberration * 20.0F;
        }
    }

    Reel reel(float t, Vec3 eye) {
        switch (skill) {
            case RAILGUN -> {
                float bt = t - Skill.MARK;
                if (bt < 0.0F || bt > 100.0F) return null;
                double d = Math.hypot(eye.x - cx(), eye.z - cz());
                Vec3 at = new Vec3(cx(), Math.max(groundY(), eye.y - 20.0D), cz());
                if (d < radius) {
                    // inside the beam: everything red, streaming
                    float k = bt < 80.0F ? 1.0F : 1.0F - (bt - 80.0F) / 20.0F;
                    return new Reel(new Vec3(cx(), eye.y + 200.0D, cz()), 0.22F * k, 0.02F * k, 0xFF2A2A, 0.62F * k, 0.4F * k);
                }
                float near = proximity(d, radius * 3.0D, radius * 14.0D);
                if (bt > 40.0F || near <= 0.0F) return null;
                float k = (1.0F - bt / 40.0F);
                k *= k * near;
                return new Reel(at, 0.26F * k, 0.022F * k, 0xFF3030, 0.42F * k, 0.45F * k);
            }
            case GUNGNIR -> {
                float bt = t - Skill.GUNGNIR_IMPACT;
                if (bt < 0.0F || bt > 34.0F) return null;
                float near = proximity(Math.hypot(eye.x - cx(), eye.z - cz()), radius * 2.0D, radius * 10.0D);
                if (near <= 0.0F) return null;
                float k = 1.0F - bt / 34.0F;
                k *= k * near;
                return new Reel(new Vec3(cx(), groundY(), cz()), 0.2F * k, 0.016F * k, EMBER, 0.32F * k, 0.3F * k);
            }
            default -> {
                Reel best = null;
                for (int i = 0; i < nx.length; i++) {
                    float bt = t - Skill.starImpact(i);
                    if (bt < 0.0F || bt > 22.0F) continue;
                    float near = proximity(Math.hypot(eye.x - nx[i], eye.z - nz[i]), sizes[i] * 4.0D, sizes[i] * 30.0D);
                    if (near <= 0.0F) continue;
                    float k = 1.0F - bt / 22.0F;
                    k *= k * near;
                    Reel r = new Reel(new Vec3(nx[i], ny[i], nz[i]), 0.13F * k, 0.012F * k, VIOLET_HOT, 0.25F * k, 0.2F * k);
                    if (best == null || r.weight() > best.weight()) best = r;
                }
                float ft = t - Skill.FLARE;
                if (ft >= 0.0F && ft < 40.0F) {
                    float near = proximity(Math.hypot(eye.x - cx(), eye.z - cz()), radius * 1.5D, radius * 4.0D);
                    float k = (ft < 6.0F ? ft / 6.0F : 1.0F - (ft - 6.0F) / 34.0F) * near;
                    Reel r = new Reel(new Vec3(cx(), groundY(), cz()), 0.1F * k, 0.01F * k, VIOLET, 0.3F * k, 0.25F * k);
                    if (k > 0.0F && (best == null || r.weight() > best.weight())) best = r;
                }
                return best;
            }
        }
    }

    // ------------------------------------------------------------------ the evacuation warning

    /** What to tell someone standing where a strike will land: which line, and how many seconds are left. */
    record Warning(String key, float seconds) {
    }

    /** The warning for someone at {@code eye} right now, or null when they're clear of it. */
    Warning warning(float t, Vec3 eye) {
        switch (skill) {
            case RAILGUN -> {
                if (Math.hypot(eye.x - cx(), eye.z - cz()) > radius + 12.0D) return null;
                if (t >= Skill.MARK - 120 && t < Skill.MARK) return new Warning("hud.starfall.evac.railgun", (Skill.MARK - t) / 20.0F);
                if (t >= Skill.MARK && t < Skill.MARK + 90) return new Warning("hud.starfall.evac.beam", 0.0F);
                return null;
            }
            case GUNGNIR -> {
                if (Math.hypot(eye.x - cx(), eye.z - cz()) > radius * Math.max(1.0F, width) + 12.0D) return null;
                if (t >= Skill.MARK - 120 && t < Skill.GUNGNIR_IMPACT) {
                    return new Warning("hud.starfall.evac.gungnir", (Skill.GUNGNIR_IMPACT - t) / 20.0F);
                }
                return null;
            }
            default -> {
                Warning best = null;
                for (int i = 0; i < nx.length; i++) {
                    float left = Skill.starImpact(i) - t;
                    if (left <= 0.0F || left > 120.0F + i * Skill.STAR_GAP) continue;
                    if (Math.hypot(eye.x - nx[i], eye.z - nz[i]) > sizes[i] + 12.0D) continue;
                    if (best == null || left / 20.0F < best.seconds()) best = new Warning("hud.starfall.evac.seven", left / 20.0F);
                }
                return best;
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    void renderSolid(Fx fx, float t, ClientLevel level) {
        if (skill == Skill.RAILGUN) shaft(fx, t, level);
        if (skill != Skill.GUNGNIR || t < Skill.MARK || t >= Skill.GUNGNIR_IMPACT) return;
        // the needle coming down, faster than its own sound
        double p = (t - Skill.MARK) / Skill.NEEDLE_FALL;
        double tip = groundY() + (1.0D - p) * (1.0D - p) * 2600.0D;
        // the needle stands two by two around the target's corner
        fx.prism(target.getX(), target.getZ(), tip, tip + 900.0D, 1.0D, 0xFF0A0A0E, 0xFF1C1A22);
    }

    /**
     * The shaft goes down to the void, and the void would show the sky's colour: it is darkened instead, black at
     * the bottom and fading up the walls, so the hole looks as deep as it is.
     */
    private void shaft(Fx fx, float t, ClientLevel level) {
        float bt = t - Skill.MARK;
        if (bt < 20.0F) return;
        float k = Mth.clamp((bt - 20.0F) / 50.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((bt - 100.0F - SHAFT_LIFE + 200.0F) / 200.0F, 0.0F, 1.0F));
        if (k <= 0.0F) return;
        double cx = cx(), cz = cz(), gy = groundY();
        double floor = level.getMinBuildHeight() - 1.0D, r = radius - 0.9D;
        fx.ring(cx, floor, cz, 0.0D, r, 160, Fx.argb(0x000000, k), Fx.argb(0x000000, k));
        fx.cylinder(cx, cz, floor, gy - 30.0D, r, 160, Fx.argb(0x050102, 0.97F * k), Fx.argb(0x200404, 0.0F));
    }

    void renderGlow(Fx fx, float t, ClientLevel level) {
        switch (skill) {
            case RAILGUN -> railgunGlow(fx, t, level);
            case GUNGNIR -> gungnirGlow(fx, t, level);
            case SEVEN_STARS -> sevenGlow(fx, t);
        }
    }

    void renderXray(Fx fx, float t) {
        if (skill == Skill.SEVEN_STARS) sevenXray(fx, t);
    }

    private void railgunGlow(Fx fx, float t, ClientLevel level) {
        double cx = cx(), cz = cz(), gy = groundY();
        double top = level.getMaxBuildHeight() + 900.0D;
        double bottom = level.getMinBuildHeight() - 64.0D;
        if (t < Skill.MARK) {
            // the red laser from space marking the ground
            float p = t / Skill.MARK;
            float pulse = 0.75F + 0.25F * Mth.sin(t * 0.6F);
            double core = 0.15D + 0.35D * p * p;
            fx.cylinder(cx, cz, gy, top, core, 8, Fx.argb(0xFF5050, 0.95F * pulse), Fx.argb(0xFF5050, 0.6F));
            fx.cylinder(cx, cz, gy, top, core * 4.0D, 12, Fx.argb(RED, 0.25F * pulse), Fx.argb(RED, 0.05F));
            fx.ring(cx, gy + 0.05D, cz, 0.0D, 2.5D + 3.0D * p, 24, Fx.argb(RED_SOFT, 0.85F), Fx.argb(RED, 0.0F));
            fx.dashedRing(cx, gy + 0.08D, cz, 6.0D + 2.0D * Mth.sin(t * 0.2F), 0.5D, 48, Fx.argb(RED, 0.9F), -t * 0.15D, 6);
            fx.dashedRing(cx, gy + 0.1D, cz, radius, 1.6D, 160, Fx.argb(RED, 0.6F * Math.min(1.0F, t / 20.0F)), t * 0.05D, 32);
            // where the laser passes your eye it catches the lens like a star
            Vec3 eye = fx.camera();
            double dist = Math.hypot(eye.x - cx, eye.z - cz);
            if (dist > 4.0D) {
                double fy = Mth.clamp(eye.y, gy + 2.0D, top);
                float a = 0.7F * pulse * Math.min(1.0F, t / 10.0F) * proximity(dist, 64.0D, 2400.0D);
                fx.flare(cx, fy, cz, Math.min(dist * 0.9D, 900.0D), Fx.argb(0xFF4040, a));
            }
            return;
        }
        float bt = t - Skill.MARK;
        float heat = wallHeat(bt);
        if (heat > 0.004F) {
            // the melted wall of the shaft: white-hot at the lip, dying to dark red far down
            fx.skirt(cx, cz, radius - 0.8D, rim, -150.0D, 0.6D, Fx.argb(RED, 0.0F), Fx.argb(EMBER, 0.6F * heat));
            fx.skirt(cx, cz, radius - 0.7D, rim, -16.0D, 0.6D, Fx.argb(EMBER, 0.0F), Fx.argb(EMBER_HOT, 0.65F * heat));
            // and the air shimmering over it
            fx.skirt(cx, cz, radius - 0.5D, rim, 0.6D, 12.0D, Fx.argb(EMBER, 0.2F * heat), Fx.argb(EMBER, 0.0F));
        }
        if (bt < 100) {
            double open = easeOut(Math.min(1.0D, bt / 6.0D));
            float fade = bt < 70 ? 1.0F : Math.max(0.0F, 1.0F - (bt - 70) / 30.0F);
            double r = radius * open;
            int seg = 128;
            // deep red through and through, with a narrow white-hot core
            fx.cylinder(cx, cz, bottom, top, r * 1.03D, seg, Fx.argb(RED, 0.32F * fade), Fx.argb(RED, 0.16F * fade));
            fx.cylinder(cx, cz, bottom, top, r * 0.85D, seg, Fx.argb(RED, 0.24F * fade), Fx.argb(RED, 0.12F * fade));
            fx.cylinder(cx, cz, bottom, top, r * 0.55D, seg, Fx.argb(RED_SOFT, 0.16F * fade), Fx.argb(RED_SOFT, 0.08F * fade));
            fx.cylinder(cx, cz, bottom, top, r * 0.2D, seg / 2, Fx.argb(PINK, 0.25F * fade), Fx.argb(PINK, 0.14F * fade));
            fx.cylinder(cx, cz, bottom, top, r * 0.1D, seg / 2, Fx.argb(WHITE, 0.6F * fade), Fx.argb(WHITE, 0.4F * fade));
            for (int k = 0; k < 18; k++) {
                double a = k * Math.PI * 2.0D / 18.0D + bt * 0.07D;
                double x = cx + Math.cos(a) * r * 0.93D, z = cz + Math.sin(a) * r * 0.93D;
                fx.ribbon(x, bottom, z, x, top, z, 1.5D + r * 0.012D, Fx.argb(RED_SOFT, 0.35F * fade), Fx.argb(RED, 0.1F * fade));
            }
            if (bt < 30) {
                double rr = radius * (1.0D + 2.0D * bt / 30.0D);
                float a = 0.7F * (1.0F - bt / 30.0F);
                fx.ring(cx, gy, cz, rr * 0.82D, rr, 160, Fx.argb(PINK, 0.0F), Fx.argb(PINK, a));
                fx.cylinder(cx, cz, gy - 2.0D, gy + 24.0D * (1.0D - bt / 30.0D), rr, 160, Fx.argb(RED_SOFT, a), Fx.argb(RED, 0.0F));
            }
        }
    }

    private void gungnirGlow(Fx fx, float t, ClientLevel level) {
        double cx = target.getX(), cz = target.getZ(), gy = groundY();
        double top = level.getMaxBuildHeight() + 900.0D;
        if (t < Skill.MARK) {
            // the crosshair marked in ember
            float pulse = 0.7F + 0.3F * Mth.sin(t * 0.4F);
            fx.cylinder(cx, cz, gy, top, 0.2D, 8, Fx.argb(EMBER_HOT, 0.9F * pulse), Fx.argb(EMBER, 0.4F));
            fx.cylinder(cx, cz, gy, top, 0.9D, 10, Fx.argb(EMBER, 0.25F), Fx.argb(EMBER, 0.03F));
            fx.dashedRing(cx, gy + 0.06D, cz, 3.0D, 0.4D, 32, Fx.argb(EMBER_HOT, 0.9F), t * 0.2D, 4);
            fx.dashedRing(cx, gy + 0.07D, cz, 7.0D, 0.5D, 48, Fx.argb(EMBER, 0.8F), -t * 0.12D, 8);
            fx.dashedRing(cx, gy + 0.08D, cz, 12.0D, 0.6D, 64, Fx.argb(EMBER, 0.6F), t * 0.08D, 3);
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2.0D + t * 0.02D;
                double c = Math.cos(a), s = Math.sin(a);
                fx.groundBand(cx + c * 14.0D, gy + 0.09D, cz + s * 14.0D, cx + c * 22.0D, gy + 0.09D, cz + s * 22.0D, 0.35D,
                        Fx.argb(EMBER_HOT, 0.9F * pulse), Fx.argb(EMBER, 0.2F));
            }
            fx.dashedRing(cx, gy + 0.1D, cz, radius, 1.6D, 160, Fx.argb(EMBER, 0.55F * Math.min(1.0F, t / 20.0F)), t * 0.04D, 40);
            return;
        }
        if (t < Skill.GUNGNIR_IMPACT) {
            double p = (t - Skill.MARK) / Skill.NEEDLE_FALL;
            double tip = gy + (1.0D - p) * (1.0D - p) * 2600.0D;
            fx.cylinder(cx, cz, tip, tip + 2600.0D, 3.5D, 12, Fx.argb(EMBER, 0.55F), Fx.argb(EMBER, 0.0F));
            fx.glow(cx, tip, cz, 10.0D, Fx.argb(EMBER_HOT, 0.95F));
            return;
        }
        float bt = t - Skill.GUNGNIR_IMPACT;
        float reach = width > 0 ? width : 2.0F;
        if (bt < Skill.SHOCK_TIME * 2) {
            double p = bt / (double) Skill.SHOCK_TIME;
            double rr = p <= 1.0D ? radius * easeOut(p) : radius * (1.0D + (reach - 1.0D) * (p - 1.0D));
            float a = (float) (p <= 1.0D ? 0.9D : 0.9D * (2.0D - p));
            fx.cylinder(cx, cz, gy - 2.0D, gy + 16.0D * (1.0D - p * 0.4D), rr, 160, Fx.argb(EMBER_HOT, a), Fx.argb(EMBER, 0.0F));
            fx.ring(cx, gy + 0.2D, cz, rr * 0.78D, rr, 160, Fx.argb(EMBER, 0.0F), Fx.argb(EMBER_HOT, a));
            fx.ring(cx, gy + 0.15D, cz, 0.0D, Math.min(rr, radius) * 0.78D, 96, Fx.argb(EMBER, 0.3F * a), Fx.argb(EMBER, 0.06F * a));
        }
        if (bt < 10) fx.glow(cx, gy + 30.0D, cz, 90.0D * (1.0D - bt / 10.0D) + 10.0D, Fx.argb(EMBER_HOT, 0.8F * (1.0F - bt / 10.0F)));
        // the needle stays standing, hot from the fall
        float heat = Math.max(0.0F, 1.0F - bt / 150.0F);
        if (heat > 0) {
            fx.cylinder(cx, cz, level.getMinBuildHeight(), level.getMaxBuildHeight(), 2.3D, 12, Fx.argb(EMBER, 0.5F * heat), Fx.argb(EMBER, 0.15F * heat));
        }
    }

    private void sevenGlow(Fx fx, float t) {
        float fwdX = -Mth.sin(yaw * Mth.DEG_TO_RAD), fwdZ = Mth.cos(yaw * Mth.DEG_TO_RAD);
        for (int i = 0; i < nx.length; i++) {
            int impact = Skill.starImpact(i);
            double x = nx[i], y = ny[i], z = nz[i], s = sizes[i];
            if (t >= impact - Skill.STAR_FALL && t < impact) {
                // its shooting star comes down out of Ursa Major
                double p = (t - (impact - Skill.STAR_FALL)) / Skill.STAR_FALL;
                double e = p * p;
                double sx = x + fwdX * 700.0D, sy = y + 1600.0D, sz = z + fwdZ * 700.0D;
                double hx = sx + (x - sx) * e, hy = sy + (y - sy) * e, hz = sz + (z - sz) * e;
                double tail = Math.min(1.0D, e + 0.35D);
                double tx = sx + (x - sx) * (e - tail * 0.35D), ty = sy + (y - sy) * (e - tail * 0.35D), tz = sz + (z - sz) * (e - tail * 0.35D);
                fx.ribbon(hx, hy, hz, tx, ty, tz, s * 0.35D + 2.0D, Fx.argb(VIOLET_HOT, 0.9F), Fx.argb(VIOLET, 0.0F));
                fx.glow(hx, hy, hz, s * 1.2D + 4.0D, Fx.argb(WHITE, 0.95F));
                fx.glow(hx, hy, hz, s * 3.5D + 10.0D, Fx.argb(VIOLET, 0.55F));
            }
            float bt = t - impact;
            if (bt >= 0 && bt < 30) {
                double k = 1.0D - bt / 30.0D;
                double rr = s * (1.0D + 2.5D * bt / 30.0D);
                fx.cylinder(x, z, y - 5.0D, y + 320.0D * k, s * 0.5D * (1.0D + bt / 10.0D), 32,
                        Fx.argb(VIOLET_HOT, (float) (0.6D * k)), Fx.argb(VIOLET, 0.0F));
                fx.ring(x, y + 0.5D, z, rr * 0.7D, rr, 64, Fx.argb(VIOLET, 0.0F), Fx.argb(VIOLET_HOT, (float) (0.85D * k)));
                fx.glow(x, y + 8.0D, z, s * 2.5D * k + 4.0D, Fx.argb(WHITE, (float) (0.8D * k)));
            }
            if (bt >= 0 && bt < 28) impactBolts(fx, i, bt);
            if (bt >= 0) {
                float f = Math.max(0.0F, 1.0F - (t - impact) / (skill.duration - impact + 1.0F));
                // the star-core crystal glowing at the bottom of its crater
                fx.cylinder(x, z, y - s * 0.45D, y + 60.0D, 0.45D, 6, Fx.argb(VIOLET_HOT, 0.75F * f), Fx.argb(VIOLET, 0.0F));
                fx.glow(x, y - s * 0.4D, z, 6.0D, Fx.argb(VIOLET_HOT, 0.7F * f));
            }
        }
        if (nx.length < 7) return;
        for (int k = 0; k < BigDipper.LINES.length; k++) {
            int a = BigDipper.LINES[k][0], b = BigDipper.LINES[k][1];
            lineBolts(fx, k, t - Skill.lineStart(k), nx[a], ny[a] + 1.5D, nz[a], nx[b], ny[b] + 1.5D, nz[b]);
        }
    }

    /** White-blue lightning crawling out over a fresh crater, and for a moment the strike itself out of the sky. */
    private void impactBolts(Fx fx, int i, float bt) {
        double x = nx[i], y = ny[i], z = nz[i], s = sizes[i];
        int frame = (int) (bt / 2.0F);
        float k = 1.0F - bt / 28.0F;
        float flicker = 0.55F + 0.45F * (float) hash(id * 31L + i * 7L + frame);
        float a = k * flicker;
        double half = 0.12D + s * 0.006D;
        int bolts = 5 + Math.min(4, (int) (s / 10.0D));
        for (int b = 0; b < bolts; b++) {
            long seed = id * 7919L + i * 131L + b * 17L + frame * 1013L;
            double ang = Math.PI * 2.0D * (b + 0.7D * hash(seed)) / bolts;
            double reach = s * (0.75D + 0.6D * hash(seed + 5L)) * (0.6D + 0.4D * Math.min(1.0D, bt / 6.0D));
            fx.bolt(x, y + 1.0D + s * 0.12D, z, x + Math.cos(ang) * reach, y + 0.6D, z + Math.sin(ang) * reach,
                    half, reach * 0.18D, seed, Fx.argb(WHITE, 0.95F * a), Fx.argb(BOLT, 0.3F * a));
        }
        if (bt < 8) {
            float f = 1.0F - bt / 8.0F;
            long seed = id * 104729L + i * 389L + frame;
            fx.bolt(x + (hash(seed) - 0.5D) * s, y + 260.0D, z + (hash(seed + 1L) - 0.5D) * s, x, y + 0.5D, z,
                    half * 2.5D, 22.0D, seed, Fx.argb(WHITE, f), Fx.argb(BOLT, 0.45F * f));
        }
    }

    /** Arcs racing along a line of the figure as it burns, and jumping off its burning front. */
    private void lineBolts(Fx fx, int k, float lt, double ax, double ay, double az, double bx, double by, double bz) {
        float span = Skill.LINE_BURN + 14.0F;
        if (lt < 0 || lt >= span) return;
        double p = Math.min(1.0D, lt / Skill.LINE_BURN);
        double ex = ax + (bx - ax) * p, ey = ay + (by - ay) * p, ez = az + (bz - az) * p;
        int frame = (int) (lt / 2.0F);
        float fade = lt < Skill.LINE_BURN ? 1.0F : 1.0F - (lt - Skill.LINE_BURN) / 14.0F;
        float a = fade * (0.6F + 0.4F * (float) hash(id * 53L + k * 11L + frame));
        double w = width * 0.5D + 2.0D;
        long seed = id * 15485863L + k * 977L + frame * 31L;
        fx.bolt(ax, ay, az, ex, ey, ez, 0.18D + width * 0.01D, w * 0.8D, seed, Fx.argb(WHITE, 0.9F * a), Fx.argb(BOLT, 0.28F * a));
        if (p >= 1.0D) return;
        double dx = bx - ax, dz = bz - az, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6D) return;
        for (int j = 0; j < 3; j++) {
            double side = (hash(seed + j * 3L) - 0.5D) * 2.0D;
            double fwd = hash(seed + j * 3L + 1L) * 0.6D;
            double reach = w * 1.6D + 6.0D;
            double tx = ex + (dx * fwd - dz * side) / len * reach, tz = ez + (dz * fwd + dx * side) / len * reach;
            fx.bolt(ex, ey + 1.0D, ez, tx, ey - 0.5D, tz, 0.12D + width * 0.006D, reach * 0.25D, seed + j * 101L,
                    Fx.argb(WHITE, 0.85F * a), Fx.argb(BOLT, 0.25F * a));
        }
    }

    /** A steady random number in 0..1 for a given seed. */
    static double hash(long n) {
        n = (n ^ (n >>> 33)) * 0xFF51AFD7ED558CCDL;
        n = (n ^ (n >>> 33)) * 0xC4CEB9FE1A85EC53L;
        n ^= n >>> 33;
        return (n >>> 11) * 0x1.0p-53;
    }

    private void sevenXray(Fx fx, float t) {
        if (nx.length < 7) return;
        float show = Math.min(1.0F, t / 20.0F);
        float flareT = t - Skill.FLARE;
        float flare = flareT < 0 ? 0 : flareT < 8 ? flareT / 8.0F : Math.max(0.0F, 1.0F - (flareT - 8) / 40.0F);
        float lit = t < Skill.FLARE + 8 ? 1.0F : Math.max(0.0F, 1.0F - (flareT - 8) / 40.0F);
        // the projection: seven nodes and the figure's lines laid over the land
        for (int i = 0; i < 7; i++) {
            double x = nx[i], y = ny[i], z = nz[i], s = sizes[i];
            boolean landed = t >= Skill.starImpact(i);
            float a = show * (landed ? 0.35F * lit : 0.9F);
            if (a <= 0.01F) continue;
            if (!landed) fx.cylinder(x, z, y, y + 400.0D, 0.7D, 8, Fx.argb(VIOLET, 0.8F * a), Fx.argb(VIOLET, 0.0F));
            fx.sigil(x, y + 0.5D, z, s, t * 0.01D * (i % 2 == 0 ? 1 : -1), Fx.argb(VIOLET_HOT, a), Fx.argb(VIOLET, 0.25F * a));
            fx.glow(x, y + 2.0D, z, 4.0D + s * 0.15D, Fx.argb(VIOLET_HOT, a));
            if (flare > 0) fx.glow(x, y + 6.0D, z, s * 2.5D, Fx.argb(VIOLET_HOT, 0.9F * flare));
        }
        for (int k = 0; k < BigDipper.LINES.length; k++) {
            int a = BigDipper.LINES[k][0], b = BigDipper.LINES[k][1];
            double ax = nx[a], ay = ny[a] + 1.5D, az = nz[a];
            double bx = nx[b], by = ny[b] + 1.5D, bz = nz[b];
            float start = Skill.lineStart(k);
            if (t < start) {
                fx.ribbon(ax, ay, az, bx, by, bz, 0.7D, Fx.argb(VIOLET, 0.55F * show), Fx.argb(VIOLET, 0.55F * show));
                continue;
            }
            double p = Math.min(1.0D, (t - start) / Skill.LINE_BURN);
            double ex = ax + (bx - ax) * p, ey = ay + (by - ay) * p, ez = az + (bz - az) * p;
            double half = width * 0.5D + 1.0D;
            if (lit > 0) {
                fx.ribbon(ax, ay, az, ex, ey, ez, half, Fx.argb(FIRE, 0.85F * lit), Fx.argb(VIOLET_HOT, 0.85F * lit));
                fx.ribbon(ax, ay, az, ex, ey, ez, half * 2.5D, Fx.argb(VIOLET, 0.25F * lit), Fx.argb(FIRE, 0.25F * lit));
            }
            if (p < 1.0D) fx.glow(ex, ey, ez, 6.0D + width, Fx.argb(0xFFD0A0, 0.95F));
            if (flare > 0) fx.ribbon(ax, ay, az, bx, by, bz, half * 4.0D, Fx.argb(VIOLET_HOT, 0.7F * flare), Fx.argb(VIOLET_HOT, 0.7F * flare));
        }
    }
}
