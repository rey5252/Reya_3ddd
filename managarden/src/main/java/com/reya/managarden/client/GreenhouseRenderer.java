package com.reya.managarden.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.managarden.Flowers;
import com.reya.managarden.GreenhouseBlockEntity;
import com.reya.managarden.ManaGarden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * What the block model can't do: the flowers growing on the bed inside (each swaying a little), the
 * mana crystal floating over them, and the bud of four petals on top, which blooms open while someone
 * has the greenhouse open and closes again after.
 */
public class GreenhouseRenderer implements BlockEntityRenderer<GreenhouseBlockEntity> {
    public static final ResourceLocation PETAL_MODEL = new ResourceLocation(ManaGarden.MODID, "block/greenhouse_petal");
    public static final ResourceLocation CORE_MODEL = new ResourceLocation(ManaGarden.MODID, "block/greenhouse_core");
    /** Petal hinge: on the rim, one pixel in from the edge, 14 pixels up. */
    private static final float HINGE_Y = 14.0F / 16.0F, HINGE_IN = 1.0F / 16.0F;
    /** Closed the petals lean in 22.5 degrees and meet over the middle; open they lean out like a lotus. */
    private static final float CLOSED = 22.5F, OPEN = 148.0F;
    /** Where the flowers stand: a ring round the middle, on the soil (4 pixels up). */
    private static final float RING = 0.285F, SOIL_Y = 4.0F / 16.0F, FLOWER_SCALE = 0.36F;

    public GreenhouseRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GreenhouseBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        if (be.getLevel() == null) return;
        Minecraft mc = Minecraft.getInstance();
        int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
        float time = be.clientAge() + partialTick;
        float open = ease(be.openness(partialTick));

        renderFlowers(mc, be, pose, buffers, light, overlay, time);
        renderCore(mc, be, pose, buffers, overlay, time, open);
        renderPetals(mc, pose, buffers, light, overlay, open);
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private void renderFlowers(Minecraft mc, GreenhouseBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time) {
        for (int i = 0; i < GreenhouseBlockEntity.FLOWERS; i++) {
            ItemStack stack = be.clientFlower(i);
            if (stack.isEmpty()) continue;
            Block block = Flowers.plantBlock(stack);
            if (block == Blocks.AIR) continue;
            BlockState state = block.defaultBlockState();
            // clockwise from the north like the GUI's ring, starting at the top
            double a = i * Math.PI / 4.0D - Math.PI / 2.0D;
            float sway = Mth.sin(time * 0.06F + i * 1.7F) * 4.0F;
            pose.pushPose();
            pose.translate(0.5D + Math.cos(a) * RING, SOIL_Y, 0.5D + Math.sin(a) * RING);
            pose.mulPose(Axis.YP.rotationDegrees(i * 37.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(sway));
            pose.scale(FLOWER_SCALE, FLOWER_SCALE, FLOWER_SCALE);
            pose.translate(-0.5D, 0.0D, -0.5D);
            try {
                mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay, ModelData.EMPTY, null);
            } catch (RuntimeException ignored) {
                // a flower from another mod that can't be drawn outside the world
            }
            pose.popPose();
        }
    }

    /**
     * The mana crystal: stands on a corner and turns over the flowers, bobbing; it glows in the dark,
     * swells for a moment when a cycle finishes, and rises out of the bud as it opens.
     */
    private void renderCore(Minecraft mc, GreenhouseBlockEntity be, PoseStack pose, MultiBufferSource buffers, int overlay, float time, float open) {
        BakedModel model = mc.getModelManager().getModel(CORE_MODEL);
        float sinceCycle = be.clientAge() - be.lastCycleAge();
        float pulse = sinceCycle < 10.0F ? Mth.sin(sinceCycle / 10.0F * Mth.PI) * 0.25F : 0.0F;
        float scale = 0.9F + pulse + 0.05F * Mth.sin(time * 0.1F);
        float y = 9.0F / 16.0F + Mth.sin(time * 0.05F) * 0.03F + open * 0.34F;
        pose.pushPose();
        pose.translate(0.5D, y, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(time * (2.0F + 4.0F * open)));
        pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(35.264F));
        pose.scale(scale, scale, scale);
        pose.translate(-0.5D, -0.5D, -0.5D);
        render(mc, model, pose, buffers.getBuffer(RenderType.cutout()), LightTexture.FULL_BRIGHT, overlay);
        pose.popPose();
    }

    /** Four petals hinged on the rim, one on each side. */
    private void renderPetals(Minecraft mc, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float open) {
        BakedModel model = mc.getModelManager().getModel(PETAL_MODEL);
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        float angle = Mth.lerp(open, CLOSED, OPEN);
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            // turn the whole block so this side is the north one
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F * side));
            pose.translate(-0.5D, 0.0D, -0.5D);
            // the north petal lies flat from its hinge towards the middle; tilt it up round the hinge
            pose.translate(0.0D, HINGE_Y, HINGE_IN);
            pose.mulPose(Axis.XP.rotationDegrees(-angle));
            pose.translate(0.0D, -HINGE_Y, -HINGE_IN);
            render(mc, model, pose, vc, light, overlay);
            pose.popPose();
        }
    }

    private static void render(Minecraft mc, BakedModel model, PoseStack pose, VertexConsumer vc, int light, int overlay) {
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), vc, null, model, 1.0F, 1.0F, 1.0F, light, overlay);
    }

    @Override
    public boolean shouldRenderOffScreen(GreenhouseBlockEntity be) {
        return true;
    }
}
