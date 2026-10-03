package com.reya.starfall.client;

import com.reya.starfall.BigDipper;
import com.reya.starfall.Skill;
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

/** One strike as this client sees it: everything is timed from the strike's start in game ticks. */
final class ClientStrike {
    static final int RED = 0xFF2A2A, RED_SOFT = 0xFF6A6A, PINK = 0xFFE0E0, WHITE = 0xFFFFFF;
    static final int EMBER = 0xFF7A1E, EMBER_HOT = 0xFFD27A;
    static final int VIOLET = 0xA060FF, VIOLET_HOT = 0xE8D0FF, FIRE = 0xFF6A2A;

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
        return gameTime - start > skill.duration + 40;
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
    }

    /** Takes the ground height of each node from the client's own chunks, until its star has landed. */
    private void refineNodes(ClientLevel level, int now) {
        for (int i = 0; i < nx.length; i++) {
            if (now >= Skill.starImpact(i)) continue;
            int x = Mth.floor(nx[i]), z = Mth.floor(nz[i]);
            if (level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                ny[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
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
        if (t == 0) {
            play(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.0F);
            play(SoundEvents.GUARDIAN_ATTACK, 0.5F, 0.6F * Math.max(0.3F, near));
        } else if (t == Skill.MARK - 50) {
            play(SoundEvents.BEACON_POWER_SELECT, 0.5F, 0.8F);
        } else if (t == Skill.MARK - 25) {
            play(SoundEvents.WARDEN_SONIC_CHARGE, 0.5F, Math.max(0.3F, near));
        } else if (t == Skill.MARK) {
            float v = Math.max(0.25F, near);
            play(SoundEvents.WARDEN_SONIC_BOOM, 0.5F, v);
            play(SoundEvents.GENERIC_EXPLODE, 0.4F, v);
            play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, v);
            play(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, v);
        } else if (t == Skill.MARK + 6) {
            play(SoundEvents.GENERIC_EXPLODE, 0.3F, Math.max(0.2F, near));
            play(SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.5F, Math.max(0.2F, near));
        } else if (t == Skill.MARK + 90) {
            play(SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.8F);
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
    }

    private void gungnirAt(ClientLevel level, Vec3 l, int t) {
        double d = Math.hypot(l.x - cx(), l.z - cz());
        float near = proximity(d, radius * 2.0D, radius * 20.0D);
        if (t == 0) {
            play(SoundEvents.BEACON_ACTIVATE, 0.5F, 0.8F);
            play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 0.8F);
        } else if (t == Skill.MARK - 30) {
            play(SoundEvents.TRIDENT_RIPTIDE_3, 0.5F, 0.8F);
        }
        // it strikes in silence: only the shock and the boom are heard, and the boom comes late
        int boom = Skill.GUNGNIR_IMPACT + boomDelay(l);
        if (t == boom) {
            float v = Math.max(0.25F, near);
            play(SoundEvents.GENERIC_EXPLODE, 0.3F, v);
            play(SoundEvents.WARDEN_SONIC_BOOM, 0.4F, v);
            play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, v);
            play(SoundEvents.TRIDENT_THUNDER, 0.5F, v);
        } else if (t == boom + 5) {
            play(SoundEvents.GENERIC_EXPLODE, 0.45F, Math.max(0.2F, near));
        }
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
        if (t == 0) {
            play(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.4F);
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.0F);
        } else if (t == Skill.MARK - 40) {
            play(SoundEvents.END_PORTAL_FRAME_FILL, 0.5F, 0.7F);
        }
        for (int i = 0; i < nx.length; i++) {
            int impact = Skill.starImpact(i);
            double d = Math.hypot(l.x - nx[i], l.z - nz[i]);
            float near = proximity(d, sizes[i] * 3.0D, sizes[i] * 40.0D);
            if (t == impact - Skill.STAR_FALL) {
                play(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.4F, Math.max(0.3F, near));
            } else if (t == impact) {
                float v = Math.max(0.2F, near);
                play(SoundEvents.GENERIC_EXPLODE, 0.6F, v);
                play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.5F, v);
                play(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, v);
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
            if (t == Skill.lineStart(k)) {
                play(SoundEvents.BLAZE_SHOOT, 0.5F, 0.6F);
                play(SoundEvents.FIRECHARGE_USE, 0.6F, 0.5F);
            }
        }
        if (t == Skill.FLARE) {
            play(SoundEvents.END_PORTAL_SPAWN, 0.8F, 0.6F);
            play(SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.0F);
        }
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

    // ------------------------------------------------------------------ rendering

    void renderSolid(Fx fx, float t, ClientLevel level) {
        if (skill != Skill.GUNGNIR || t < Skill.MARK || t >= Skill.GUNGNIR_IMPACT) return;
        // the needle coming down, faster than its own sound
        double p = (t - Skill.MARK) / Skill.NEEDLE_FALL;
        double tip = groundY() + (1.0D - p) * (1.0D - p) * 2600.0D;
        fx.prism(cx(), cz(), tip, tip + 900.0D, 1.3D, 0xFF0A0A0E, 0xFF1C1A22);
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
            return;
        }
        float bt = t - Skill.MARK;
        if (bt < 100) {
            double open = easeOut(Math.min(1.0D, bt / 6.0D));
            float fade = bt < 70 ? 1.0F : Math.max(0.0F, 1.0F - (bt - 70) / 30.0F);
            double r = radius * open;
            int seg = 128;
            fx.cylinder(cx, cz, bottom, top, r * 1.03D, seg, Fx.argb(RED, 0.35F * fade), Fx.argb(RED, 0.18F * fade));
            fx.cylinder(cx, cz, bottom, top, r * 0.85D, seg, Fx.argb(RED_SOFT, 0.35F * fade), Fx.argb(RED_SOFT, 0.15F * fade));
            fx.cylinder(cx, cz, bottom, top, r * 0.6D, seg, Fx.argb(PINK, 0.4F * fade), Fx.argb(PINK, 0.2F * fade));
            fx.cylinder(cx, cz, bottom, top, r * 0.3D, seg / 2, Fx.argb(WHITE, 0.5F * fade), Fx.argb(WHITE, 0.3F * fade));
            for (int k = 0; k < 18; k++) {
                double a = k * Math.PI * 2.0D / 18.0D + bt * 0.07D;
                double x = cx + Math.cos(a) * r * 0.93D, z = cz + Math.sin(a) * r * 0.93D;
                fx.ribbon(x, bottom, z, x, top, z, 1.5D + r * 0.012D, Fx.argb(WHITE, 0.45F * fade), Fx.argb(RED, 0.15F * fade));
            }
            if (bt < 30) {
                double rr = radius * (1.0D + 2.0D * bt / 30.0D);
                float a = 0.7F * (1.0F - bt / 30.0F);
                fx.ring(cx, gy, cz, rr * 0.82D, rr, 160, Fx.argb(PINK, 0.0F), Fx.argb(PINK, a));
                fx.cylinder(cx, cz, gy - 2.0D, gy + 24.0D * (1.0D - bt / 30.0D), rr, 160, Fx.argb(RED_SOFT, a), Fx.argb(RED, 0.0F));
            }
            return;
        }
        // afterglow on the rim of the hole
        float k = Math.max(0.0F, 1.0F - (bt - 100) / (skill.duration - Skill.MARK - 100.0F));
        fx.cylinder(cx, cz, gy - 60.0D, gy + 40.0D, radius, 160, Fx.argb(RED, 0.3F * k), Fx.argb(RED, 0.0F));
    }

    private void gungnirGlow(Fx fx, float t, ClientLevel level) {
        double cx = cx(), cz = cz(), gy = groundY();
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
            fx.cylinder(cx, cz, level.getMinBuildHeight(), level.getMaxBuildHeight(), 1.6D, 8, Fx.argb(EMBER, 0.5F * heat), Fx.argb(EMBER, 0.15F * heat));
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
            if (bt >= 0) {
                float f = Math.max(0.0F, 1.0F - (t - impact) / (skill.duration - impact + 1.0F));
                // the star-core crystal glowing at the bottom of its crater
                fx.cylinder(x, z, y - s * 0.45D, y + 60.0D, 0.45D, 6, Fx.argb(VIOLET_HOT, 0.75F * f), Fx.argb(VIOLET, 0.0F));
                fx.glow(x, y - s * 0.4D, z, 6.0D, Fx.argb(VIOLET_HOT, 0.7F * f));
            }
        }
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
            fx.dashedRing(x, y + 0.5D, z, s, 1.2D, 64, Fx.argb(VIOLET_HOT, a), t * 0.06D * (i % 2 == 0 ? 1 : -1), 12);
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
