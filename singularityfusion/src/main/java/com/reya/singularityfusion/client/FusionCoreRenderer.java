package com.reya.singularityfusion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.FusionGeometry;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The fusion core's renderer: what floats over the core (its catalyst, or what it made), and the items a fusion pours
 * into the singularity, flying from the pylons' points; the result coming down out of it when the fusion ends. The
 * singularity itself is drawn later in the frame, over everything solid (SingularityRenderer): this queues it.
 */
public class FusionCoreRenderer implements BlockEntityRenderer<FusionCoreBlockEntity> {
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
