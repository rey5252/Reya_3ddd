package com.overenchant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.overenchant.UpgraderBlockEntity;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Floating enchanting-table book above the block, tinted cyan. */
public class UpgraderRenderer implements BlockEntityRenderer<UpgraderBlockEntity> {
    private static final Material BOOK = new Material(TextureAtlas.LOCATION_BLOCKS, new ResourceLocation("entity/enchanting_table_book"));
    private final BookModel bookModel;

    public UpgraderRenderer(BlockEntityRendererProvider.Context ctx) {
        this.bookModel = new BookModel(ctx.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public void render(UpgraderBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5F, 0.75F, 0.5F);
        float t = be.time + partialTick;
        pose.translate(0.0F, 0.1F + Mth.sin(t * 0.1F) * 0.01F, 0.0F);
        float rotDiff = be.rot - be.oRot;
        while (rotDiff >= Math.PI) rotDiff -= (float) (Math.PI * 2);
        while (rotDiff < -Math.PI) rotDiff += (float) (Math.PI * 2);
        float rot = be.oRot + rotDiff * partialTick;
        pose.mulPose(Axis.YP.rotation(-rot));
        pose.mulPose(Axis.ZP.rotationDegrees(80.0F));
        float flip = Mth.lerp(partialTick, be.oFlip, be.flip);
        float page1 = Mth.clamp(Mth.frac(flip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float page2 = Mth.clamp(Mth.frac(flip + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float open = Mth.lerp(partialTick, be.oOpen, be.open);
        bookModel.setupAnim(t, page1, page2, open);
        VertexConsumer vc = BOOK.buffer(buffer, RenderType::entitySolid);
        bookModel.renderToBuffer(pose, vc, light, overlay, 0.55F, 0.9F, 1.0F, 1.0F);
        pose.popPose();
    }
}
