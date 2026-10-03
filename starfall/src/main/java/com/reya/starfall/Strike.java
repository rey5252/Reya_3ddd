package com.reya.starfall;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.reya.starfall.carve.CarveData;
import com.reya.starfall.carve.CarveJob;
import com.reya.starfall.carve.ColumnShape;
import com.reya.starfall.carve.CraterShape;
import com.reya.starfall.carve.TrenchShape;
import com.reya.starfall.network.Net;
import com.reya.starfall.network.StrikePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

/** A weapon on its way: walks the skill's timeline on the server and does the damage when it lands. */
final class Strike {
    private static final AtomicInteger IDS = new AtomicInteger();
    /** Radius of ground spared under a creative player's feet (SS-04). */
    private static final double SPARE_RADIUS = 3.0D;

    final int id = IDS.incrementAndGet();
    final Skill skill;
    final ServerLevel level;
    final BlockPos target;
    final long start;
    final float yaw;
    @Nullable final UUID caster;
    final float radius;
    /** Seven Stars: where each star lands, and how wide its crater is. */
    final BlockPos[] nodes;
    final float[] sizes;
    final float width;
    private int done = -1;

    Strike(ServerLevel level, Skill skill, BlockPos target, float yaw, @Nullable ServerPlayer caster) {
        this.skill = skill;
        this.level = level;
        this.target = target;
        this.start = level.getGameTime();
        this.yaw = yaw;
        this.caster = caster == null ? null : caster.getUUID();
        switch (skill) {
            case RAILGUN -> {
                radius = Config.RAILGUN_RADIUS.get();
                nodes = new BlockPos[0];
                sizes = new float[0];
                width = 0;
            }
            case GUNGNIR -> {
                radius = Config.GUNGNIR_RADIUS.get();
                nodes = new BlockPos[0];
                sizes = new float[0];
                width = 0;
            }
            default -> {
                double span = Config.SEVEN_SPAN.get();
                radius = (float) span;
                nodes = new BlockPos[7];
                sizes = new float[7];
                for (int i = 0; i < 7; i++) {
                    double[] o = BigDipper.offset(i, yaw, span);
                    int x = target.getX() + (int) Math.round(o[0]);
                    int z = target.getZ() + (int) Math.round(o[1]);
                    nodes[i] = new BlockPos(x, surface(level, x, z, target.getY()), z);
                    sizes[i] = (float) Math.max(4.0D, span / 80.0D * Config.SEVEN_CRATER_SCALE.get() * BigDipper.size(i));
                }
                width = Config.SEVEN_TRENCH_WIDTH.get();
            }
        }
    }

    /** Height of the ground if the chunk is loaded, otherwise a guess. */
    private static int surface(ServerLevel level, int x, int z, int fallback) {
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return fallback;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /** Tells every player in the dimension; the one who pressed the button gets the film. */
    void announce() {
        int[] flat = new int[nodes.length * 3];
        for (int i = 0; i < nodes.length; i++) {
            flat[i * 3] = nodes[i].getX();
            flat[i * 3 + 1] = nodes[i].getY();
            flat[i * 3 + 2] = nodes[i].getZ();
        }
        for (ServerPlayer player : level.players()) {
            boolean isCaster = player.getUUID().equals(caster);
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new StrikePacket(id, skill.ordinal(), target, start, radius, yaw, isCaster, flat, sizes, width));
        }
    }

    /** Runs every timeline event up to now. Returns true once the strike is over. */
    boolean tick() {
        int now = (int) (level.getGameTime() - start);
        for (int t = done + 1; t <= now; t++) at(t);
        done = Math.max(done, now);
        return now >= skill.duration;
    }

    @Nullable
    private Entity attacker() {
        return caster == null ? null : level.getPlayerByUUID(caster);
    }

    private AABB column(double x, double z, double r) {
        return new AABB(x - r, level.getMinBuildHeight() - 64, z - r, x + r, level.getMaxBuildHeight() + 256, z + r);
    }

    private void at(int t) {
        switch (skill) {
            case RAILGUN -> railgun(t);
            case GUNGNIR -> gungnir(t);
            case SEVEN_STARS -> sevenStars(t);
        }
    }

    private void railgun(int t) {
        if (t < Skill.MARK || t > Skill.MARK + 90 || (t - Skill.MARK) % 10 != 0) return;
        double cx = target.getX() + 0.5D, cz = target.getZ() + 0.5D;
        // the beam keeps burning for a while: whatever wanders or falls in is erased too
        Zones.obliterate(level, column(cx, cz, radius), (x, z) -> sq(x - cx) + sq(z - cz) <= sq(radius),
                attacker(), Zones.Creative.EVACUATE);
        if (t == Skill.MARK) {
            CarveData.get(level).add(new CarveJob(new ColumnShape(cx, cz, radius, Config.RAILGUN_BEDROCK.get())));
        }
    }

    private void gungnir(int t) {
        double cx = target.getX() + 0.5D, cz = target.getZ() + 0.5D;
        if (t == Skill.GUNGNIR_IMPACT) {
            Zones.obliterate(level, column(cx, cz, radius), (x, z) -> sq(x - cx) + sq(z - cz) <= sq(radius),
                    attacker(), Zones.Creative.IGNORE);
            CarveData.get(level).add(new CarveJob(new CraterShape(target.getX(), target.getY(), target.getZ(), radius,
                    Config.GUNGNIR_DEPTH.get(), CraterShape.Palette.MOLTEN, true, false)));
        } else if (t == Skill.GUNGNIR_IMPACT + Skill.SHOCK_TIME / 2) {
            Zones.shock(level, cx, target.getY(), cz, radius, radius * Config.GUNGNIR_SHOCK.get(), attacker());
        }
    }

    private void sevenStars(int t) {
        for (int i = 0; i < 7; i++) {
            if (t != Skill.starImpact(i)) continue;
            BlockPos n = nodes[i];
            double cx = n.getX() + 0.5D, cz = n.getZ() + 0.5D, r = sizes[i];
            List<Player> spared = Zones.obliterate(level, column(cx, cz, r), (x, z) -> sq(x - cx) + sq(z - cz) <= sq(r),
                    attacker(), Zones.Creative.SPARE);
            int depth = Math.max(3, (int) Math.round(r * 0.45D));
            CarveJob job = new CarveJob(new CraterShape(n.getX(), CraterShape.RELATIVE, n.getZ(), r, depth,
                    CraterShape.Palette.STARRY, false, true));
            for (Player p : spared) job.spare(p.getX(), p.getZ(), SPARE_RADIUS);
            CarveData.get(level).add(job);
        }
        for (int k = 0; k < BigDipper.LINES.length; k++) {
            if (t != Skill.lineStart(k)) continue;
            int a = BigDipper.LINES[k][0], b = BigDipper.LINES[k][1];
            double ax = nodes[a].getX() + 0.5D, az = nodes[a].getZ() + 0.5D;
            double bx = nodes[b].getX() + 0.5D, bz = nodes[b].getZ() + 0.5D;
            double half = width / 2.0D;
            double reach = half + 1.0D;
            AABB box = new AABB(Math.min(ax, bx) - reach, level.getMinBuildHeight() - 64, Math.min(az, bz) - reach,
                    Math.max(ax, bx) + reach, level.getMaxBuildHeight() + 256, Math.max(az, bz) + reach);
            List<Player> spared = Zones.obliterate(level, box, (x, z) -> segment(x, z, ax, az, bx, bz) <= reach,
                    attacker(), Zones.Creative.SPARE);
            CarveJob job = new CarveJob(new TrenchShape(ax, az, bx, bz, half, Config.SEVEN_TRENCH_DEPTH.get(), sizes[a], sizes[b]));
            for (Player p : spared) job.spare(p.getX(), p.getZ(), SPARE_RADIUS);
            CarveData.get(level).add(job);
        }
    }

    private static double segment(double x, double z, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double t = len2 < 1.0E-6D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((x - ax) * vx + (z - az) * vz) / len2));
        return Math.hypot(ax + vx * t - x, az + vz * t - z);
    }

    private static double sq(double v) {
        return v * v;
    }
}
