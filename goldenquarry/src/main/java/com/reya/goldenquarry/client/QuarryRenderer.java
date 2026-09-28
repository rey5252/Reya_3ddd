package com.reya.goldenquarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.goldenquarry.GoldenQuarry;
import com.reya.goldenquarry.QuarryBlock;
import com.reya.goldenquarry.QuarryBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws what the block model can't: the drill standing in the chest, turning slowly while the
 * quarry digs, and the glass over the cage and the chest's side openings (see-through, so it is
 * drawn translucent after the drill).
 */
public class QuarryRenderer implements BlockEntityRenderer<QuarryBlockEntity> {
    public static final ResourceLocation DRILL_MODEL = new ResourceLocation(GoldenQuarry.MODID, "block/golden_quarry_drill");
    public static final ResourceLocation GLASS_MODEL = new ResourceLocation(GoldenQuarry.MODID, "block/golden_quarry_glass");

    public QuarryRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(QuarryBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        if (be.getLevel() == null) return;
        Minecraft mc = Minecraft.getInstance();
        int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
        // turned with the chest like the block model (blockstate y rotation)
        int facingRot = switch (be.getBlockState().getValue(QuarryBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        float angle = be.drillAngle(partialTick);
        float shake = be.drillSpin() * 0.004F * Mth.sin(angle * 0.35F);
        pose.pushPose();
        pose.translate(0.5D, shake, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(angle - facingRot));
        pose.translate(-0.5D, 0.0D, -0.5D);
        render(mc, mc.getModelManager().getModel(DRILL_MODEL), pose, buffers.getBuffer(RenderType.cutout()), light, overlay);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-facingRot));
        pose.translate(-0.5D, 0.0D, -0.5D);
        render(mc, mc.getModelManager().getModel(GLASS_MODEL), pose, buffers.getBuffer(Sheets.translucentCullBlockSheet()), light, overlay);
        pose.popPose();
    }

    private static void render(Minecraft mc, BakedModel model, PoseStack pose, VertexConsumer vc, int light, int overlay) {
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), vc, null, model, 1.0F, 1.0F, 1.0F, light, overlay);
    }
}
