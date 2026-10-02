package com.reya.singularityfusion.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where the structure's moving parts are, and when a fusion's stages come: the renderers, the screen and the client's
 * particles all go by these, so the pylons, the beams and the items in flight meet.
 */
public final class FusionGeometry {
    /** The singularity's radius when its core is full (blocks). */
    public static final float HOLE_RADIUS = 1.5F;
    /** A pylon's parts, in blocks along it from its plinth's top: its crystal's middle and tip, and its ingredient. */
    public static final float CRYSTAL = 2.93F, TIP = 3.26F, ITEM = 3.70F;
    /** Where a pylon's telescoping pieces end along it: its collar, its shaft's three pieces, its neck. */
    public static final float[] PIECES = {0.2F, 0.98F, 1.72F, 2.4F, 2.62F};
    /**
     * A pylon unfolding as it is placed, as how far through it is (0 to 1; it takes DEPLOY_TICKS): when each piece
     * starts sliding out (each taking DEPLOY_PART), when the first ring opens out (each next DEPLOY_RING_STEP later),
     * when the crystal lights, when the ingredient rises to its place.
     */
    public static final float DEPLOY_TICKS = 56.0F, DEPLOY_PART = 0.22F, DEPLOY_RINGS = 0.5F, DEPLOY_RING_STEP = 0.06F, DEPLOY_CRYSTAL = 0.72F,
            DEPLOY_ITEM = 0.86F;
    public static final float[] DEPLOY_PIECES = {0.0F, 0.08F, 0.22F, 0.36F, 0.48F};
    /** A core waking as it is placed, and its structure coming whole: how long their light lasts (ticks). */
    public static final float AWAKEN_TICKS = 50.0F, FORMED_TICKS = 44.0F;
    /** A fusion's stages, as its progress (0 to 1): the beams light, the core's catalyst rises into the singularity... */
    public static final float BEAMS_IN = 0.12F, CATALYST_RISES = 0.18F, CATALYST_GONE = 0.34F;
    /** ...the pylons let go of their items one after another (each flying for FLIGHT), and the singularity collapses. */
    public static final float INJECT = 0.3F, INJECT_SPAN = 0.42F, FLIGHT = 0.13F, COLLAPSE = 0.85F;
    /** Ticks after a fusion ends: the flash and the shock wave, the result coming down to the core. */
    public static final float FLASH_TICKS = 14.0F, SHOCK_TICKS = 24.0F, RESULT_FROM = 4.0F, RESULT_TICKS = 22.0F;

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    /** The singularity's middle over a core. */
    public static Vec3 hole(BlockPos core) {
        return new Vec3(core.getX() + 0.5D, core.getY() + 1.0D + FusionCoreBlockEntity.HOLE_HEIGHT, core.getZ() + 0.5D);
    }

    /** Where a pylon rises from: its plinth's top middle. */
    public static Vec3 base(BlockPos pylon) {
        return new Vec3(pylon.getX() + 0.5D, pylon.getY() + 1.0D, pylon.getZ() + 0.5D);
    }

    /** Which way a pylon points (a unit vector): at its core's singularity, or straight up while it serves none. */
    public static Vec3 aim(BlockPos pylon, @Nullable BlockPos core) {
        if (core == null) return UP;
        Vec3 d = hole(core).subtract(base(pylon));
        double length = d.length();
        return length < 1.0E-3D ? UP : d.scale(1.0D / length);
    }

    /** How big a pylon is drawn: full size, or smaller when it stands close to its singularity. */
    public static float scale(BlockPos pylon, @Nullable BlockPos core) {
        if (core == null) return 1.0F;
        double distance = hole(core).distanceTo(base(pylon));
        return (float) Mth.clamp((distance - 1.9D) / ITEM, 0.45D, 1.0D);
    }

    /** The point `at` blocks along a pylon (at its full size). */
    public static Vec3 along(BlockPos pylon, @Nullable BlockPos core, float at) {
        return base(pylon).add(aim(pylon, core).scale(at * scale(pylon, core)));
    }

    public static Vec3 tip(BlockPos pylon, @Nullable BlockPos core) {
        return along(pylon, core, TIP);
    }

    public static Vec3 item(BlockPos pylon, @Nullable BlockPos core) {
        return along(pylon, core, ITEM);
    }

    /** When the pylon `index` of `count` lets go of its item, as the fusion's progress. */
    public static float departure(int index, int count) {
        return INJECT + INJECT_SPAN * index / Math.max(1, count);
    }

    /** How far the singularity has shrunk in the fusion's last stage (0 to 1). */
    public static float collapse(float progress) {
        float t = Mth.clamp((progress - COLLAPSE) / (1.0F - COLLAPSE), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private FusionGeometry() {
    }
}
