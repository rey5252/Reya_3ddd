package com.reya.goldenquarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.goldenquarry.GoldenQuarry;
import com.reya.goldenquarry.QuarryBlock;
import com.reya.goldenquarry.QuarryBlockEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws what the block model can't: the drill in the cage on top, spinning while the quarry digs,
 * and the glowing golden square around the dig area, lying on the ground the stand is on: a
 * white-hot core line, a wider orange glow around it and a soft haze rising from it, all drawn
 * additively (the lightning render type) so it shines in the dark and pulses slowly.
 */
public class QuarryAreaRenderer implements BlockEntityRenderer<QuarryBlockEntity> {
    public static final ResourceLocation DRILL_MODEL = new ResourceLocation(GoldenQuarry.MODID, "block/golden_quarry_drill");
    private static final float Y = 0.03F;

    public QuarryAreaRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(QuarryBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderDrill(be, partialTick, pose, buffers, overlay);
        if (!be.showArea()) return;
        int r = be.radius();
        float min = -r, max = r + 1;
        float pulse = 0.8F + 0.2F * Mth.sin(Util.getMillis() / 400.0F);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        // outer glow, middle glow, core
        frame(vc, m, min, max, 0.90F, 255, 140, 20, 0.16F * pulse);
        frame(vc, m, min, max, 0.50F, 255, 170, 40, 0.30F * pulse);
        frame(vc, m, min, max, 0.24F, 255, 205, 90, 0.55F * pulse);
        frame(vc, m, min, max, 0.10F, 255, 250, 210, 0.95F);
        haze(vc, m, min, max, 0.45F, 255, 180, 50, 0.28F * pulse);
    }

    /** The drill model hangs in the cage of the upper half; it turns around its middle and shakes a little while digging. */
    private static void renderDrill(QuarryBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int overlay) {
        if (be.getLevel() == null) return;
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(DRILL_MODEL);
        int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        float angle = be.drillAngle(partialTick);
        float shake = be.drillSpin() * 0.012F * Mth.sin(angle * 0.35F);
        // turned with the chest like the block model (blockstate y rotation), then spinning
        int facingRot = switch (be.getBlockState().getValue(QuarryBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.pushPose();
        pose.translate(0.5D, 1.0D + shake, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(angle - facingRot));
        pose.translate(-0.5D, 0.0D, -0.5D);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, model,
                1.0F, 1.0F, 1.0F, light, overlay);
        pose.popPose();
    }

    /** Four flat strips of the given width centred on the square's border. */
    private static void frame(VertexConsumer vc, Matrix4f m, float min, float max, float width, int cr, int cg, int cb, float a) {
        float h = width / 2.0F;
        int alpha = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255);
        flat(vc, m, min - h, min - h, max + h, min + h, cr, cg, cb, alpha);   // north
        flat(vc, m, min - h, max - h, max + h, max + h, cr, cg, cb, alpha);   // south
        flat(vc, m, min - h, min + h, min + h, max - h, cr, cg, cb, alpha);   // west
        flat(vc, m, max - h, min + h, max + h, max - h, cr, cg, cb, alpha);   // east
    }

    private static void flat(VertexConsumer vc, Matrix4f m, float x1, float z1, float x2, float z2, int r, int g, int b, int a) {
        // both windings, the render type culls back faces
        vc.vertex(m, x1, Y, z1).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, Y, z2).color(r, g, b, a).endVertex();
        vc.vertex(m, x2, Y, z2).color(r, g, b, a).endVertex();
        vc.vertex(m, x2, Y, z1).color(r, g, b, a).endVertex();
        vc.vertex(m, x2, Y, z1).color(r, g, b, a).endVertex();
        vc.vertex(m, x2, Y, z2).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, Y, z2).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, Y, z1).color(r, g, b, a).endVertex();
    }

    /** Thin upright curtains on the border that fade out towards the top. */
    private static void haze(VertexConsumer vc, Matrix4f m, float min, float max, float height, int r, int g, int b, float a) {
        int alpha = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255);
        wall(vc, m, min, min, max, min, height, r, g, b, alpha);
        wall(vc, m, max, min, max, max, height, r, g, b, alpha);
        wall(vc, m, max, max, min, max, height, r, g, b, alpha);
        wall(vc, m, min, max, min, min, height, r, g, b, alpha);
    }

    private static void wall(VertexConsumer vc, Matrix4f m, float x1, float z1, float x2, float z2, float height, int r, int g, int b, int a) {
        float top = Y + height;
        vc.vertex(m, x1, Y, z1).color(r, g, b, a).endVertex();
        vc.vertex(m, x2, Y, z2).color(r, g, b, a).endVertex();
        vc.vertex(m, x2, top, z2).color(r, g, b, 0).endVertex();
        vc.vertex(m, x1, top, z1).color(r, g, b, 0).endVertex();
        vc.vertex(m, x1, top, z1).color(r, g, b, 0).endVertex();
        vc.vertex(m, x2, top, z2).color(r, g, b, 0).endVertex();
        vc.vertex(m, x2, Y, z2).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, Y, z1).color(r, g, b, a).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(QuarryBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
