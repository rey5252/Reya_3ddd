package com.reya.singularityfusion.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reya.singularityfusion.SingularityFusion;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.FusionGeometry;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The singularity over a fusion core, after Gargantua: a black shadow with a thin bright photon ring round it; a hot
 * accretion disk seen nearly edge on, its near side crossing in front of the shadow, turning (its inner part faster);
 * the light of the disk's far side bent over the top of the shadow and, fainter, under it. It is drawn facing the eye,
 * so it looks so from everywhere, as big as the core is full and catching up smoothly as it fills or drains.
 * <p>
 * The fuller it is, the more goes on round it: a violet aura from an eighth full, sparks of infalling matter spiralling
 * in from a quarter, lightning leaping to the pylons' points from nearly half, jets from its poles when it is nearly
 * full; a beam feeds it from the core. In a fusion the pylons beam into it, it spins up, then collapses to a point,
 * and a flash and a shock wave burst from it as the result comes down.
 * <p>
 * Drawn once the block entities are (the core's renderer queues it): the shadow first, into the depth buffer, then
 * everything that glows, added to what is behind it, so whatever stands in front of it hides it rightly.
 */
@Mod.EventBusSubscriber(modid = SingularityFusion.MODID, value = Dist.CLIENT)
public final class SingularityRenderer {
    private static final ResourceLocation SHADOW = Fx.effect("shadow"), DISK = Fx.effect("disk"), RING = Fx.effect("ring"), GLOW = Fx.effect("glow"),
            BOLT = Fx.effect("bolt"), BEAM = Fx.effect("beam"), JET = Fx.effect("jet"), SPARK = Fx.effect("spark"), SHOCK = Fx.effect("shock");
    /** How quickly the singularity's size catches up with the core's charge (its gap shrinks by e each 1/GROWTH seconds). */
    private static final float GROWTH = 0.9F;
    /** The disk: how far above its plane the eye is (radians), its inner and outer edges (in the shadow's radii). */
    private static final float TILT = 0.2F, DISK_IN = 1.55F, DISK_OUT = 3.3F;
    private static final int SEGMENTS = 72, BANDS = 6, DISK_REPEAT = 3;
    /** How fast each of the disk's bands turns (the inner faster): eighths, so the spin can wrap at 8 without a jump. */
    private static final float[] BAND_SPEED = {1.0F, 0.75F, 0.625F, 0.5F, 0.375F, 0.25F};
    private static final float SPIN_WRAP = 8.0F;

    private static final List<FusionCoreBlockEntity> QUEUED = new ArrayList<>();

    /** The core's renderer saw it this frame: its singularity is drawn once the block entities are. */
    static void queue(FusionCoreBlockEntity core) {
        if (!QUEUED.contains(core)) QUEUED.add(core);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || QUEUED.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            Vec3 cam = event.getCamera().getPosition();
            Quaternionf facing = event.getCamera().rotation();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            for (FusionCoreBlockEntity core : QUEUED) {
                if (core.isRemoved() || core.getLevel() != mc.level) continue;
                BlockPos pos = core.getBlockPos();
                pose.pushPose();
                pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                draw(core, event.getPartialTick(), pose, buffers, facing, cam.subtract(pos.getX(), pos.getY(), pos.getZ()));
                pose.popPose();
            }
            buffers.endLastBatch();
        } finally {
            QUEUED.clear();
        }
    }

    /** Everything over one core; the pose is at the core's corner, `eye` the camera from there. */
    private static void draw(FusionCoreBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, Quaternionf facing, Vec3 eye) {
        Level level = be.getLevel();
        if (level == null) return;
        long gameTime = level.getGameTime();
        float time = (float) (gameTime % 120000L) + partial;

        // how full it looks catches up with how full it is
        long now = System.nanoTime();
        float dt = be.lastFrameNanos == 0L ? 0.0F : Mth.clamp((now - be.lastFrameNanos) / 1.0E9F, 0.0F, 0.25F);
        be.lastFrameNanos = now;
        float target = be.charge();
        be.shownCharge += (target - be.shownCharge) * (1.0F - (float) Math.exp(-dt * GROWTH));
        if (Math.abs(target - be.shownCharge) < 1.0E-4F) be.shownCharge = target;
        float c = be.shownCharge;

        boolean fusing = be.fusing();
        float p = be.progress(partial);
        float collapse = fusing ? FusionGeometry.collapse(p) : 0.0F;
        float windUp = fusing ? Fx.smooth(0.0F, FusionGeometry.INJECT, p) : 0.0F;
        float sinceDone = (gameTime - be.doneAt) + partial, sinceAbort = (gameTime - be.abortedAt) + partial;
        float pop = sinceDone >= 0.0F && sinceDone < 16.0F ? 0.3F * Mth.sin(sinceDone / 16.0F * Fx.PI) * (1.0F - sinceDone / 16.0F) : 0.0F;
        float abort = sinceAbort >= 0.0F && sinceAbort < 20.0F ? (1.0F - sinceAbort / 20.0F) * (1.0F - sinceAbort / 20.0F) : 0.0F;
        be.spin = (be.spin + dt * (0.035F + 0.09F * c + 0.3F * windUp + 0.7F * collapse)) % SPIN_WRAP;

        float radius = FusionGeometry.HOLE_RADIUS * c * (1.0F - 0.4F * collapse) * (1.0F + pop);
        float bright = Fx.smooth(0.0F, 0.2F, c) * (0.72F + 0.28F * c) * (1.0F + 0.25F * windUp + 0.7F * collapse) * (1.0F - 0.45F * abort
                * (0.5F + 0.5F * Mth.sin(sinceAbort * 2.4F)));
        float aura = Fx.smooth(0.12F, 0.5F, c);
        float sparks = Fx.smooth(0.25F, 0.7F, c);
        float storm = Fx.smooth(0.42F, 0.9F, c);
        float jets = Fx.smooth(0.8F, 1.0F, c) * (1.0F - collapse);
        Vec3 hole = new Vec3(0.5D, 1.0D + FusionCoreBlockEntity.HOLE_HEIGHT, 0.5D);

        // ---------------------------------------------------------------- facing the eye
        Quaternionf toEye = toward(eye, hole, facing);
        pose.pushPose();
        pose.translate(hole.x, hole.y, hole.z);
        pose.mulPose(toEye);        // x to the eye's left, y up, z straight away from it
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        if (radius > 0.003F) {
            shadow(buffers.getBuffer(RenderType.entityCutoutNoCull(SHADOW)), m, n, radius);

            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
            float k = bright * 0.42F;
            Fx.square(glow, m, n, 0.0F, 0.0F, 0.05F * radius, 3.5F * radius, 0.45F * k, 0.29F * k, 0.15F * k, 1.0F);
            k = aura * (0.8F + 0.2F * Mth.sin(time * 0.07F)) * 0.55F;
            Fx.square(glow, m, n, 0.0F, 0.0F, 0.08F * radius, 4.8F * radius + 0.8F, 0.3F * k, 0.09F * k, 0.52F * k, 1.0F);
            if (abort > 0.0F) Fx.square(glow, m, n, 0.0F, 0.0F, 0.08F * radius, 3.2F * radius + 1.0F, 0.9F * abort, 0.12F * abort, 0.3F * abort, 1.0F);

            VertexConsumer disk = buffers.getBuffer(RenderType.eyes(DISK));
            disk(disk, m, n, radius, bright, be.spin);
            arcs(disk, m, n, radius, bright, be.spin);
            photonRing(buffers.getBuffer(RenderType.eyes(RING)), m, n, radius, bright, be.spin);
            if (sparks > 0.0F || fusing) {
                sparks(buffers.getBuffer(RenderType.eyes(SPARK)), m, n, radius, time, sparks + (fusing ? 0.5F * windUp : 0.0F), bright);
            }
            if (jets > 0.01F) jets(buffers.getBuffer(RenderType.eyes(JET)), m, n, radius, time, jets);

            // a haze round the disk's near side, in front
            glow = buffers.getBuffer(RenderType.eyes(GLOW));
            k = bright * 0.16F;
            Fx.rect(glow, m, n, -3.7F * radius, -0.66F * radius, 3.7F * radius, 0.04F * radius, -1.2F * radius, 0.0F, 0.0F, 1.0F, 1.0F, 0.5F * k,
                    0.33F * k, 0.17F * k, 1.0F);
        }
        // the flash as a fusion ends: over everything, the shadow too
        if (sinceDone >= 0.0F && sinceDone < FusionGeometry.FLASH_TICKS) {
            float t = sinceDone / FusionGeometry.FLASH_TICKS, k = (1.0F - t) * (1.0F - t) * 1.6F;
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
            Fx.square(glow, m, n, 0.0F, 0.0F, -Math.max(0.3F, 1.3F * radius), 1.0F + (2.0F + 6.0F * t) * Math.max(radius, 0.6F), 0.95F * k, 0.8F * k,
                    k, 1.0F);
        }
        pose.popPose();

        // ---------------------------------------------------------------- in the world
        m = pose.last().pose();
        n = pose.last().normal();
        float r = Math.max(radius, 0.02F);
        VertexConsumer beam = buffers.getBuffer(RenderType.eyes(BEAM));
        float scroll = (time * 0.045F) % 1.0F;
        // the feed beam from the core up into the singularity
        float feed = Fx.smooth(0.02F, 0.3F, c) * (0.75F + 0.25F * Mth.sin(time * 0.4F)) * (1.0F + 0.6F * windUp);
        if (feed > 0.01F && radius > 0.003F) {
            Vec3 from = new Vec3(0.5D, 1.12D, 0.5D), to = hole.subtract(0.0D, r * 0.9D, 0.0D);
            float length = (float) from.distanceTo(to);
            Fx.band(beam, m, n, from, to, eye, 0.16F + 0.12F * c, -scroll, length / 2.0F - scroll, 0.55F * feed, 0.28F * feed, feed, 1.0F, 1.0F);
            Fx.band(beam, m, n, from, to, eye, 0.06F, -scroll * 1.6F, length / 1.5F - scroll * 1.6F, 0.8F * feed, 0.75F * feed, feed, 1.0F, 1.0F);
        }
        // the pylons beaming into it while it fuses
        if (fusing && p >= FusionGeometry.BEAMS_IN) {
            float in = Fx.smooth(FusionGeometry.BEAMS_IN, FusionGeometry.INJECT, p);
            int count = be.pylons().size();
            for (int i = 0; i < count; i++) {
                BlockPos at = be.pylons().get(i);
                boolean full = level.getBlockEntity(at) instanceof GravitonPylonBlockEntity pylon && !pylon.item().isEmpty();
                float arrived = FusionGeometry.departure(i, count) + FusionGeometry.FLIGHT;
                float k = in * (full ? 1.0F : 0.35F) * (p > arrived ? 0.6F : 1.0F) * (1.0F + 0.8F * collapse)
                        * (0.85F + 0.15F * Mth.sin(time * 0.9F + i * 1.7F));
                Vec3 tip = FusionGeometry.tip(at, be.getBlockPos()).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                Vec3 to = hole.add(tip.subtract(hole).normalize().scale(r * 1.05D));
                float length = (float) tip.distanceTo(to);
                Fx.band(beam, m, n, tip, to, eye, 0.15F, -scroll * 2.0F, length / 1.5F - scroll * 2.0F, 0.55F * k, 0.25F * k, k, 1.0F, 0.8F);
                Fx.band(beam, m, n, tip, to, eye, 0.05F, -scroll * 3.0F, length - scroll * 3.0F, 0.9F * k, 0.85F * k, k, 1.0F, 1.0F);
            }
        }
        // lightning: to the pylons' points, and arcing over the singularity's face
        if ((storm > 0.0F || fusing) && radius > 0.05F) {
            lightning(buffers.getBuffer(RenderType.eyes(BOLT)), m, n, be, hole, r, eye, toEye, time, storm, fusing ? 0.25F + 0.5F * collapse : 0.0F);
        }
        // the shock wave as a fusion ends
        if (sinceDone >= 0.0F && sinceDone < FusionGeometry.SHOCK_TICKS) {
            shock(buffers.getBuffer(RenderType.eyes(SHOCK)), m, n, hole, r, sinceDone / FusionGeometry.SHOCK_TICKS);
        }
    }

    /**
     * The turn that faces the singularity to the eye: its z straight away from the eye (so the disk is seen at its
     * tilt wherever on the screen it is, not flattened when it hangs above where the eye looks), its y as near the
     * camera's up as that allows, its x to the eye's left.
     */
    private static Quaternionf toward(Vec3 eye, Vec3 hole, Quaternionf facing) {
        Vector3f away = new Vector3f((float) (hole.x - eye.x), (float) (hole.y - eye.y), (float) (hole.z - eye.z));
        if (away.lengthSquared() < 1.0E-6F) return new Quaternionf(facing);
        away.normalize();
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(facing);
        Vector3f left = new Vector3f(up).cross(away);
        if (left.lengthSquared() < 1.0E-6F) left = new Vector3f(1.0F, 0.0F, 0.0F).rotate(facing);
        left.normalize();
        Vector3f realUp = new Vector3f(away).cross(left).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(left, realUp, away));
    }

    // ------------------------------------------------------------------ the singularity's parts (facing the eye)

    /** The shadow: a black disc, solid, so what is behind it is hidden. */
    private static void shadow(VertexConsumer vc, Matrix4f m, Matrix3f n, float radius) {
        int segments = 64;
        for (int j = 0; j < segments; j++) {
            float a0 = 2.0F * Fx.PI * j / segments, a1 = 2.0F * Fx.PI * (j + 1) / segments;
            float x0 = radius * Mth.cos(a0), y0 = radius * Mth.sin(a0), x1 = radius * Mth.cos(a1), y1 = radius * Mth.sin(a1);
            Fx.vertex(vc, m, n, 0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F, 1.0F, LightTexture.FULL_BRIGHT);
            Fx.vertex(vc, m, n, x0, y0, 0.0F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F, 1.0F, LightTexture.FULL_BRIGHT);
            Fx.vertex(vc, m, n, x1, y1, 0.0F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F, 1.0F, LightTexture.FULL_BRIGHT);
            Fx.vertex(vc, m, n, x1, y1, 0.0F, 0.5F, 0.5F, 0.0F, 0.0F, 0.0F, 1.0F, LightTexture.FULL_BRIGHT);
        }
    }

    /**
     * The accretion disk: a flat ring round the shadow tipped TILT toward the eye (its near side lower), in bands that
     * turn at their own speeds; the side coming toward the eye a little brighter.
     */
    private static void disk(VertexConsumer vc, Matrix4f m, Matrix3f n, float radius, float bright, float spin) {
        float sinT = Mth.sin(TILT), cosT = Mth.cos(TILT);
        for (int band = 0; band < BANDS; band++) {
            float v0 = band / (float) BANDS, v1 = (band + 1) / (float) BANDS;
            float r0 = radius * Mth.lerp(v0, DISK_IN, DISK_OUT), r1 = radius * Mth.lerp(v1, DISK_IN, DISK_OUT);
            float phase = spin * BAND_SPEED[band] % 1.0F;
            for (int j = 0; j < SEGMENTS; j++) {
                float a0 = 2.0F * Fx.PI * j / SEGMENTS, a1 = 2.0F * Fx.PI * (j + 1) / SEGMENTS;
                float u0 = (float) j / SEGMENTS * DISK_REPEAT + phase, u1 = (float) (j + 1) / SEGMENTS * DISK_REPEAT + phase;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                // brighter where it comes toward the eye, and across the front, where it is seen through the least of itself
                float k0 = bright * (1.0F + 0.3F * c0) * (1.0F + 0.25F * Math.max(0.0F, -s0)), k1 = bright * (1.0F + 0.3F * c1) * (1.0F + 0.25F
                        * Math.max(0.0F, -s1));
                diskQuad(vc, m, n, r0 * c0, r0 * s0 * sinT, r0 * s0 * cosT, u0, v0, k0,
                        r1 * c0, r1 * s0 * sinT, r1 * s0 * cosT, u0, v1, k0,
                        r1 * c1, r1 * s1 * sinT, r1 * s1 * cosT, u1, v1, k1,
                        r0 * c1, r0 * s1 * sinT, r0 * s1 * cosT, u1, v0, k1);
            }
        }
    }

    /**
     * The disk's far side seen bent round the shadow: a broad bright arc over its top, from its edge to where the disk
     * begins, and a thin fainter one under it. The disk's texture on them, turning with it.
     */
    private static void arcs(VertexConsumer vc, Matrix4f m, Matrix3f n, float radius, float bright, float spin) {
        int segments = 40;
        float z = 0.01F * radius, phase = spin % 1.0F;
        for (int j = 0; j < segments; j++) {
            float t0 = j / (float) segments, t1 = (j + 1) / (float) segments;
            // over the top: from the left (+x) round to the right
            float a0 = Fx.PI * t0, a1 = Fx.PI * t1;
            float in = 1.04F * radius;
            // reaching the disk's inner edge at its ends, so the two run into each other
            float out0 = radius * (1.46F + 0.14F * Mth.cos(a0) * Mth.cos(a0)), out1 = radius * (1.46F + 0.14F * Mth.cos(a1) * Mth.cos(a1));
            float k0 = bright * 0.9F * (0.8F + 0.2F * Mth.sin(a0)), k1 = bright * 0.9F * (0.8F + 0.2F * Mth.sin(a1));
            float u0 = t0 * DISK_REPEAT * 0.5F + phase, u1 = t1 * DISK_REPEAT * 0.5F + phase;
            diskQuad(vc, m, n, in * Mth.cos(a0), in * Mth.sin(a0), z, u0, 0.0F, k0,
                    out0 * Mth.cos(a0), out0 * Mth.sin(a0), z, u0, 0.62F, k0,
                    out1 * Mth.cos(a1), out1 * Mth.sin(a1), z, u1, 0.62F, k1,
                    in * Mth.cos(a1), in * Mth.sin(a1), z, u1, 0.0F, k1);
            // under it, thinner and fainter, turning the other way round (its light comes round the other side)
            a0 = Fx.PI * (1.0F + t0);
            a1 = Fx.PI * (1.0F + t1);
            in = 1.03F * radius;
            out0 = radius * (1.03F + 0.24F * (0.7F + 0.3F * Math.abs(Mth.sin(a0))));
            out1 = radius * (1.03F + 0.24F * (0.7F + 0.3F * Math.abs(Mth.sin(a1))));
            k0 = bright * 0.5F;
            u0 = (1.0F - t0) * DISK_REPEAT * 0.5F + phase;
            u1 = (1.0F - t1) * DISK_REPEAT * 0.5F + phase;
            diskQuad(vc, m, n, in * Mth.cos(a0), in * Mth.sin(a0), z, u0, 0.0F, k0,
                    out0 * Mth.cos(a0), out0 * Mth.sin(a0), z, u0, 0.4F, k0,
                    out1 * Mth.cos(a1), out1 * Mth.sin(a1), z, u1, 0.4F, k0,
                    in * Mth.cos(a1), in * Mth.sin(a1), z, u1, 0.0F, k0);
        }
    }

    /** The photon ring: a thin bright line hugging the shadow's edge (its inside hidden by the shadow). */
    private static void photonRing(VertexConsumer vc, Matrix4f m, Matrix3f n, float radius, float bright, float spin) {
        int segments = 64;
        float z = 0.012F * radius, in = 0.985F * radius, out = 1.1F * radius, k = bright * 1.05F, phase = spin * 0.5F % 1.0F;
        for (int j = 0; j < segments; j++) {
            float a0 = 2.0F * Fx.PI * j / segments, a1 = 2.0F * Fx.PI * (j + 1) / segments;
            float u0 = j / (float) segments * 4.0F + phase, u1 = (j + 1) / (float) segments * 4.0F + phase;
            diskQuad(vc, m, n, in * Mth.cos(a0), in * Mth.sin(a0), z, u0, 0.0F, k,
                    out * Mth.cos(a0), out * Mth.sin(a0), z, u0, 1.0F, k,
                    out * Mth.cos(a1), out * Mth.sin(a1), z, u1, 1.0F, k,
                    in * Mth.cos(a1), in * Mth.sin(a1), z, u1, 0.0F, k);
        }
    }

    /** Sparks of matter spiralling in along the disk, faster as they near the shadow, each on a cycle of its own. */
    private static void sparks(VertexConsumer vc, Matrix4f m, Matrix3f n, float radius, float time, float amount, float bright) {
        int count = Math.min(44, (int) (32.0F * amount));
        float sinT = Mth.sin(TILT), cosT = Mth.cos(TILT);
        for (int k = 0; k < count; k++) {
            float period = 70.0F + 60.0F * Fx.hash(k, 1, 0);
            float clock = time + period * Fx.hash(k, 2, 0);
            int cycle = (int) (clock / period);
            float life = clock / period - cycle;
            float r0 = radius * (2.1F + 1.5F * Fx.hash(k, cycle, 3));
            float at = r0 + (radius * 1.02F - r0) * (float) Math.pow(life, 1.6D);
            float angle = Fx.hash(k, cycle, 4) * 2.0F * Fx.PI - life * (2.5F + 2.0F * Fx.hash(k, cycle, 5)) * Fx.PI;
            float x = at * Mth.cos(angle), d = at * Mth.sin(angle);
            float y = d * sinT + (Fx.hash(k, cycle, 6) - 0.5F) * 0.2F * radius * (1.0F - life), z = d * cosT;
            float fade = Fx.smooth(0.0F, 0.15F, life) * (1.0F - Fx.smooth(0.85F, 1.0F, life)) * Math.min(1.0F, bright * 1.4F);
            float size = (0.035F + 0.04F * Fx.hash(k, cycle, 7)) * (1.0F + 0.45F * radius);
            boolean hot = Fx.hash(k, cycle, 8) < 0.6F;
            float red = hot ? 1.0F : 0.68F, green = hot ? 0.74F : 0.38F, blue = hot ? 0.42F : 1.0F;
            Fx.square(vc, m, n, x, y, z, size, red * fade, green * fade, blue * fade, 1.0F);
        }
    }

    /** Jets of light out of the poles, flickering, knots running out along them. */
    private static void jets(VertexConsumer vc, Matrix4f m, Matrix3f n, float radius, float time, float amount) {
        float flicker = 0.85F + 0.15F * Mth.sin(time * 1.3F) * Mth.sin(time * 0.37F);
        float length = radius * (5.5F + 1.2F * Mth.sin(time * 0.05F)) * amount;
        float z = 0.02F * radius, scroll = (time * 0.035F) % 1.0F, k = amount * flicker;
        for (int s = -1; s <= 1; s += 2) {
            float y0 = s * radius * 0.96F, y1 = s * (radius + length), w0 = 0.3F * radius, w1 = 0.07F * radius;
            float u0 = -scroll, u1 = length / 3.0F - scroll;
            diskQuad(vc, m, n, -w0, y0, z, u0, 1.0F, 0.6F * k, -w1, y1, z, u1, 1.0F, 0.0F, w1, y1, z, u1, 0.0F, 0.0F, w0, y0, z, u0, 0.0F, 0.6F * k);
            k *= 0.94F;
        }
    }

    /**
     * A quad of light from four corners (position, texture, strength), seen from both sides; tinted white-hot, its
     * colour coming from the texture.
     */
    private static void diskQuad(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float au, float av, float ak, float bx, float by,
                                 float bz, float bu, float bv, float bk, float cx, float cy, float cz, float cu, float cv, float ck, float dx, float dy,
                                 float dz, float du, float dv, float dk) {
        int l = LightTexture.FULL_BRIGHT;
        Fx.vertex(vc, m, n, ax, ay, az, au, av, ak, 0.94F * ak, 0.88F * ak, 1.0F, l);
        Fx.vertex(vc, m, n, bx, by, bz, bu, bv, bk, 0.94F * bk, 0.88F * bk, 1.0F, l);
        Fx.vertex(vc, m, n, cx, cy, cz, cu, cv, ck, 0.94F * ck, 0.88F * ck, 1.0F, l);
        Fx.vertex(vc, m, n, dx, dy, dz, du, dv, dk, 0.94F * dk, 0.88F * dk, 1.0F, l);
        Fx.vertex(vc, m, n, dx, dy, dz, du, dv, dk, 0.94F * dk, 0.88F * dk, 1.0F, l);
        Fx.vertex(vc, m, n, cx, cy, cz, cu, cv, ck, 0.94F * ck, 0.88F * ck, 1.0F, l);
        Fx.vertex(vc, m, n, bx, by, bz, bu, bv, bk, 0.94F * bk, 0.88F * bk, 1.0F, l);
        Fx.vertex(vc, m, n, ax, ay, az, au, av, ak, 0.94F * ak, 0.88F * ak, 1.0F, l);
    }

    // ------------------------------------------------------------------ in the world

    /**
     * Lightning: bolts leaping from the singularity to the pylons' points now and then (more the fuller it is, and all
     * the time in a fusion), and arcs crawling over its face. Each bolt holds for a few ticks, then jumps elsewhere.
     */
    private static void lightning(VertexConsumer vc, Matrix4f m, Matrix3f n, FusionCoreBlockEntity be, Vec3 hole, float radius, Vec3 eye,
                                  Quaternionf facing, float time, float storm, float fury) {
        int bucket = (int) (time / 3.0F);
        float age = time / 3.0F - bucket, fade = 1.0F - 0.6F * age;
        float chance = storm > 0.0F ? 0.1F + 0.32F * storm + fury : fury;
        int count = be.pylons().size();
        for (int i = 0; i < count; i++) {
            if (Fx.hash(i, bucket, 11) >= chance) continue;
            BlockPos at = be.pylons().get(i);
            Vec3 tip = FusionGeometry.tip(at, be.getBlockPos()).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
            Vec3 from = hole.add(tip.subtract(hole).normalize().scale(radius * 1.02D));
            bolt(vc, m, n, from, tip, eye, i * 31 + bucket * 7, fade);
        }
        // arcs over its face: from one point near the photon ring to another
        if (storm > 0.3F) {
            Vector3f left = new Vector3f(1.0F, 0.0F, 0.0F).rotate(facing), up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(facing);
            int arcs = 1 + (int) (2.0F * storm);
            int fast = (int) (time / 2.0F);
            for (int k = 0; k < arcs; k++) {
                if (Fx.hash(k, fast, 12) > 0.55F + 0.3F * storm) continue;
                float a = Fx.hash(k, fast, 13) * 2.0F * Fx.PI, b = a + (0.5F + 0.9F * Fx.hash(k, fast, 14)) * (Fx.hash(k, fast, 15) < 0.5F ? -1.0F : 1.0F);
                float ra = radius * (1.12F + 0.2F * Fx.hash(k, fast, 16)), rb = radius * (1.12F + 0.2F * Fx.hash(k, fast, 17));
                Vec3 from = hole.add(onFace(left, up, ra, a)), to = hole.add(onFace(left, up, rb, b));
                bolt(vc, m, n, from, to, eye, 1000 + k * 17 + fast * 5, (1.0F - 0.5F * (time / 2.0F - fast)) * storm);
            }
        }
    }

    private static Vec3 onFace(Vector3f left, Vector3f up, float r, float angle) {
        float x = r * Mth.cos(angle), y = r * Mth.sin(angle);
        return new Vec3(left.x() * x + up.x() * y, left.y() * x + up.y() * y, left.z() * x + up.z() * y);
    }

    /** One bolt from a to b: a jagged line (the same for the same seed), a wide violet glow and a thin white core. */
    private static void bolt(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, Vec3 eye, int seed, float strength) {
        int pieces = 10;
        Vec3 along = b.subtract(a);
        double length = along.length();
        if (length < 0.05D) return;
        Vec3 dir = along.scale(1.0D / length);
        Vec3 side1 = dir.cross(Math.abs(dir.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        Vec3 side2 = dir.cross(side1);
        double jag = 0.12D + 0.05D * length;
        Vec3[] points = new Vec3[pieces + 1];
        for (int j = 0; j <= pieces; j++) {
            double t = j / (double) pieces;
            double reach = Math.sin(t * Math.PI) * jag;
            double o1 = (Fx.hash(seed, j, 21) - 0.5D) * 2.0D * reach, o2 = (Fx.hash(seed, j, 22) - 0.5D) * 2.0D * reach;
            points[j] = a.add(along.scale(t)).add(side1.scale(o1)).add(side2.scale(o2));
        }
        for (int j = 0; j < pieces; j++) {
            Fx.band(vc, m, n, points[j], points[j + 1], eye, 0.24F, 0.0F, 1.0F, 0.42F * strength, 0.16F * strength, 0.85F * strength, 1.0F, 1.0F);
            Fx.band(vc, m, n, points[j], points[j + 1], eye, 0.07F, 0.0F, 1.0F, 0.85F * strength, 0.75F * strength, strength, 1.0F, 1.0F);
        }
        // a fork from somewhere along it
        int from = 3 + (int) (Fx.hash(seed, 0, 23) * 4.0F);
        Vec3 fork = points[from].add(dir.scale(length * 0.18D)).add(side1.scale((Fx.hash(seed, 0, 24) - 0.5D) * length * 0.35D))
                .add(side2.scale((Fx.hash(seed, 0, 25) - 0.5D) * length * 0.35D));
        Fx.band(vc, m, n, points[from], fork, eye, 0.14F, 0.0F, 1.0F, 0.32F * strength, 0.12F * strength, 0.7F * strength, 1.0F, 0.0F);
    }

    /** The shock wave: a flat ring of light racing out from the singularity, fading as it goes. */
    private static void shock(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 hole, float radius, float t) {
        float reach = radius * 1.2F + 13.0F * (1.0F - (float) Math.pow(1.0F - t, 2.2D));
        float width = 0.6F + 1.8F * t, k = (float) Math.pow(1.0F - t, 1.6D);
        float out = reach, in = Math.max(0.0F, reach - width);
        int segments = 72;
        float y = (float) hole.y;
        for (int j = 0; j < segments; j++) {
            float a0 = 2.0F * Fx.PI * j / segments, a1 = 2.0F * Fx.PI * (j + 1) / segments;
            float u0 = j / (float) segments * 6.0F, u1 = (j + 1) / (float) segments * 6.0F;
            float x0 = (float) hole.x, z0 = (float) hole.z;
            diskQuad(vc, m, n, x0 + out * Mth.cos(a0), y, z0 + out * Mth.sin(a0), u0, 0.0F, k,
                    x0 + in * Mth.cos(a0), y, z0 + in * Mth.sin(a0), u0, 1.0F, k,
                    x0 + in * Mth.cos(a1), y, z0 + in * Mth.sin(a1), u1, 1.0F, k,
                    x0 + out * Mth.cos(a1), y, z0 + out * Mth.sin(a1), u1, 0.0F, k);
        }
    }

    private SingularityRenderer() {
    }
}
