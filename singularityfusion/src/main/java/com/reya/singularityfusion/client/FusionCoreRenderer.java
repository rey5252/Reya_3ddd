package com.reya.singularityfusion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.FusionGeometry;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The fusion core's renderer: what floats over the core (its catalyst, or what it made), and the items a fusion pours
 * into the singularity, flying from the pylons' points; the result coming down out of it when the fusion ends. As the
 * core is placed, a pillar of light rises out of it to where its singularity will hang and a ring of light races out
 * across the floor; as its structure comes whole, threads of light run out from it to its pylons and up them. The
 * singularity itself is drawn later in the frame, over everything solid (SingularityRenderer): this queues it.
 */
public class FusionCoreRenderer implements BlockEntityRenderer<FusionCoreBlockEntity> {
    private static final ResourceLocation BEAM = Fx.effect("beam"), GLOW = Fx.effect("glow"), SHOCK = Fx.effect("shock");
    /** Where an item floats over the core (from the block's corner). */
    private static final double FLOAT_Y = 1.45D;

    private final ItemRenderer items;

    public FusionCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(FusionCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        SingularityRenderer.queue(be);
        BlockPos pos = be.getBlockPos();
        float time = (float) (level.getGameTime() % 120000L) + partialTick;
        boolean fusing = be.fusing();
        float progress = be.progress(partialTick);
        float sinceDone = (float) (level.getGameTime() - be.doneAt) + partialTick;
        boolean resultComing = sinceDone >= 0.0F && sinceDone < FusionGeometry.RESULT_FROM + FusionGeometry.RESULT_TICKS;
        int light = LevelRenderer.getLightColor(level, pos.above());
        Vec3 hole = new Vec3(0.5D, 1.0D + FusionCoreBlockEntity.HOLE_HEIGHT, 0.5D);
        float radius = FusionGeometry.HOLE_RADIUS * Math.max(0.15F, be.shownCharge);

        // over the core: the catalyst (rising into the singularity as a fusion begins), else what it made
        ItemStack catalyst = be.items().getStackInSlot(FusionCoreBlockEntity.CATALYST);
        ItemStack output = be.items().getStackInSlot(FusionCoreBlockEntity.OUTPUT);
        Vec3 rest = new Vec3(0.5D, FLOAT_Y + 0.06D * Mth.sin(time / 10.0F), 0.5D);
        if (!resultComing) {
            if (!catalyst.isEmpty()) {
                float rise = fusing ? Mth.clamp((progress - FusionGeometry.CATALYST_RISES)
                        / (FusionGeometry.CATALYST_GONE - FusionGeometry.CATALYST_RISES), 0.0F, 1.0F) : 0.0F;
                if (rise < 1.0F) {
                    float e = rise * rise * (3.0F - 2.0F * rise);
                    Vec3 at = rest.lerp(hole.subtract(0.0D, radius * 0.3D, 0.0D), e);
                    item(be, catalyst, pose, buffers, at, 0.9F * (1.0F - 0.85F * e), time * (4.0F + 20.0F * e), rise > 0.0F ? LightTexture.FULL_BRIGHT : light);
                }
            } else if (!output.isEmpty()) {
                item(be, output, pose, buffers, rest, 0.9F, time * 4.0F, light);
            }
        } else if (sinceDone >= FusionGeometry.RESULT_FROM && !output.isEmpty()) {
            // the result comes down out of the singularity, growing, and settles over the core
            float t = (sinceDone - FusionGeometry.RESULT_FROM) / FusionGeometry.RESULT_TICKS;
            float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            item(be, output, pose, buffers, hole.lerp(rest, e), 0.2F + 0.7F * e, time * (14.0F - 10.0F * e), LightTexture.FULL_BRIGHT);
        }

        awakening(be, pose, buffers, level, partialTick);
        forming(be, pose, buffers, level, partialTick);

        // the pylons' items on their way into the singularity
        if (!fusing || progress < FusionGeometry.INJECT) return;
        int count = be.pylons().size();
        for (int i = 0; i < count; i++) {
            BlockPos at = be.pylons().get(i);
            if (!(level.getBlockEntity(at) instanceof GravitonPylonBlockEntity pylon) || pylon.item().isEmpty()) continue;
            float t = (progress - FusionGeometry.departure(i, count)) / FusionGeometry.FLIGHT;
            if (t < 0.0F || t >= 1.0F) continue;
            float e = t * t * (3.0F - 2.0F * t);
            Vec3 from = FusionGeometry.item(at, pos).subtract(pos.getX(), pos.getY(), pos.getZ());
            // a little sideways swing on the way, as if caught by the spin
            Vec3 way = hole.subtract(from);
            Vec3 side = way.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (side.lengthSqr() > 1.0E-6D) side = side.normalize().scale(0.9D * Mth.sin(t * Fx.PI) * (1.0F - t));
            Vec3 p = from.lerp(hole, e).add(side);
            item(be, pylon.item(), pose, buffers, p, 1.1F * (1.0F - 0.88F * e), time * (6.0F + 30.0F * t), LightTexture.FULL_BRIGHT);
        }
    }

    /** The camera, from the core's corner. */
    private static Vec3 eye(FusionCoreBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    /** As the core is placed: a pillar of light rising out of it to where its singularity will hang, a ring of light racing out across the floor. */
    private static void awakening(FusionCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, Level level, float partialTick) {
        if (be.awakenedAt == Long.MIN_VALUE) return;
        float t = (level.getGameTime() - be.awakenedAt + partialTick) / FusionGeometry.AWAKEN_TICKS;
        if (t < 0.0F || t >= 1.0F) return;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vec3 eye = eye(be);
        float rise = 1.0F - (float) Math.pow(1.0F - Math.min(1.0F, t / 0.3F), 3.0D), fade = 1.0F - Fx.smooth(0.3F, 1.0F, t);
        Vec3 from = new Vec3(0.5D, 1.0D, 0.5D), to = new Vec3(0.5D, 1.0D + FusionCoreBlockEntity.HOLE_HEIGHT * rise, 0.5D);
        float length = (float) from.distanceTo(to), scroll = t * 3.0F;
        VertexConsumer beam = buffers.getBuffer(RenderType.eyes(BEAM));
        Fx.band(beam, m, n, from, to, eye, 0.9F - 0.4F * t, -scroll, length / 2.0F - scroll, 0.6F * fade, 0.35F * fade, fade, 1.0F, 0.35F);
        Fx.band(beam, m, n, from, to, eye, 0.3F, -scroll * 1.6F, length / 1.5F - scroll * 1.6F, 0.95F * fade, 0.9F * fade, fade, 1.0F, 0.6F);
        Quaternionf camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        float k = fade * (0.5F + 0.5F * rise);
        GravitonPylonRenderer.billboard(pose, glow, camera, to, 0.5F + 1.4F * rise, 0.55F * k, 0.3F * k, k);
        GravitonPylonRenderer.billboard(pose, glow, camera, from.add(0.0D, 0.05D, 0.0D), 1.2F * (1.0F - t), 0.5F * fade, 0.25F * fade, 0.9F * fade);
        float out = 0.7F + 6.0F * (1.0F - (float) Math.pow(1.0F - t, 2.4D)), in = Math.max(0.0F, out - 0.35F - 0.9F * t);
        floorRing(buffers.getBuffer(RenderType.eyes(SHOCK)), m, n, 0.03F, in, out, (float) Math.pow(1.0F - t, 1.5D), 0.7F, 0.45F, 1.0F);
    }

    /** As the structure comes whole: threads of light racing out from the core to each pylon's foot, flaring there, then running up each to its crystal. */
    private static void forming(FusionCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, Level level, float partialTick) {
        if (be.formedAt == Long.MIN_VALUE || be.pylons().isEmpty()) return;
        float t = (level.getGameTime() - be.formedAt + partialTick) / FusionGeometry.FORMED_TICKS;
        if (t < 0.0F || t >= 1.0F) return;
        BlockPos pos = be.getBlockPos();
        Vec3 corner = Vec3.atLowerCornerOf(pos), eye = eye(be);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer beam = buffers.getBuffer(RenderType.eyes(BEAM));
        float head = 1.0F - (float) Math.pow(1.0F - Math.min(1.0F, t / 0.45F), 2.0D), fade = 1.0F - Fx.smooth(0.55F, 1.0F, t);
        float climb = Fx.smooth(0.42F, 0.72F, t), scroll = t * 4.0F;
        Vec3 from = new Vec3(0.5D, 1.02D, 0.5D);
        Quaternionf camera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        for (BlockPos p : be.pylons()) {
            Vec3 foot = FusionGeometry.base(p).subtract(corner), to = from.lerp(foot, head);
            float length = (float) from.distanceTo(to);
            Fx.band(beam, m, n, from, to, eye, 0.24F, -scroll, length / 1.5F - scroll, 0.6F * fade, 0.32F * fade, fade, 0.35F, 1.0F);
            if (climb > 0.0F) {
                Vec3 crystal = FusionGeometry.along(p, pos, FusionGeometry.CRYSTAL).subtract(corner), top = foot.lerp(crystal, climb);
                length = (float) foot.distanceTo(top);
                Fx.band(beam, m, n, foot, top, eye, 0.18F, -scroll, length / 1.5F - scroll, 0.8F * fade, 0.6F * fade, fade, 0.5F, 1.0F);
            }
        }
        // each foot flares as its thread arrives
        float flare = Fx.smooth(0.38F, 0.45F, t) * (1.0F - Fx.smooth(0.45F, 0.8F, t));
        if (flare > 0.0F) {
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
            for (BlockPos p : be.pylons()) {
                GravitonPylonRenderer.billboard(pose, glow, camera, FusionGeometry.base(p).subtract(corner), 0.5F + 0.6F * flare, 0.6F * flare, 0.35F * flare,
                        flare);
            }
        }
    }

    /** A flat ring of light on the floor round the core (y up from its foot), from `in` to `out` across, both sides. */
    private static void floorRing(VertexConsumer vc, Matrix4f m, Matrix3f n, float y, float in, float out, float k, float r, float g, float b) {
        int segments = 64;
        int light = LightTexture.FULL_BRIGHT;
        for (int j = 0; j < segments; j++) {
            float a0 = 2.0F * Fx.PI * j / segments, a1 = 2.0F * Fx.PI * (j + 1) / segments;
            float u0 = j / (float) segments * 6.0F, u1 = (j + 1) / (float) segments * 6.0F;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float x0o = 0.5F + out * c0, z0o = 0.5F + out * s0, x0i = 0.5F + in * c0, z0i = 0.5F + in * s0;
            float x1o = 0.5F + out * c1, z1o = 0.5F + out * s1, x1i = 0.5F + in * c1, z1i = 0.5F + in * s1;
            // seen from above, then from below
            Fx.vertex(vc, m, n, x0o, y, z0o, u0, 0.0F, r * k, g * k, b * k, 1.0F, light);
            Fx.vertex(vc, m, n, x1o, y, z1o, u1, 0.0F, r * k, g * k, b * k, 1.0F, light);
            Fx.vertex(vc, m, n, x1i, y, z1i, u1, 1.0F, r * k, g * k, b * k, 1.0F, light);
            Fx.vertex(vc, m, n, x0i, y, z0i, u0, 1.0F, r * k, g * k, b * k, 1.0F, light);
            Fx.vertex(vc, m, n, x0i, y, z0i, u0, 1.0F, r * k, g * k, b * k, 1.0F, light);
            Fx.vertex(vc, m, n, x1i, y, z1i, u1, 1.0F, r * k, g * k, b * k, 1.0F, light);
            Fx.vertex(vc, m, n, x1o, y, z1o, u1, 0.0F, r * k, g * k, b * k, 1.0F, light);
            Fx.vertex(vc, m, n, x0o, y, z0o, u0, 0.0F, r * k, g * k, b * k, 1.0F, light);
        }
    }

    /** An item floating at a point (block coordinates), `size` big, turned `turn` degrees. */
    private void item(FusionCoreBlockEntity be, ItemStack stack, PoseStack pose, MultiBufferSource buffers, Vec3 at, float size, float turn, int light) {
        if (size <= 0.02F) return;
        pose.pushPose();
        // a ground item sits 2 pixels up from where it is put
        pose.translate(at.x, at.y - 0.125D * size, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(turn % 360.0F));
        pose.scale(size, size, size);
        items.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(),
                (int) be.getBlockPos().asLong());
        pose.popPose();
    }

    /** The singularity hangs high over the core: drawn whenever it can be seen, even when the core can't. */
    @Override
    public boolean shouldRenderOffScreen(FusionCoreBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
