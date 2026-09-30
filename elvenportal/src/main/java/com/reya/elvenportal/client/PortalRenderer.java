package com.reya.elvenportal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.elvenportal.ElvenPortal;
import com.reya.elvenportal.PortalBlock;
import com.reya.elvenportal.PortalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The portal inside the arch: a green swirl (textures/block/portal_swirl.png, animated) on a pane between the
 * pillars, opening from its middle while the portal has mana for a trade and closing when it hasn't. When
 * the elves trade, the item that went in flies into the swirl from the front, shrinking, and what they send
 * back comes out of it, rises and fades away.
 * <p>
 * Everything is drawn as the model has it (facing north, the front towards -z), turned to the block's facing.
 */
public class PortalRenderer implements BlockEntityRenderer<PortalBlockEntity> {
    public static final ResourceLocation SWIRL = new ResourceLocation(ElvenPortal.MODID, "block/portal_swirl");
    private static final float PI = (float) Math.PI;
    /** The pane between the pillars, in pixels: the middle and half its size, and the part of the texture it shows. */
    private static final float PANE_X = 8.0F, PANE_Y = 7.5F, PANE_HALF_W = 5.0F, PANE_HALF_H = 5.5F;
    private static final float U1 = 3.0F, U2 = 13.0F, V1 = 3.0F, V2 = 14.0F;

    private final ItemRenderer items;

    public PortalRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(PortalBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(PortalBlock.FACING)) return;
        float time = be.clientAge() + partialTick;
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-modelTurn(state.getValue(PortalBlock.FACING))));
        pose.translate(-0.5D, 0.0D, -0.5D);
        float open = be.openness(partialTick);
        if (open > 0.01F) swirl(pose, buffers, open, time);
        flights(be, pose, buffers, light, time);
        pose.popPose();
    }

    /** The blockstate's y turn of the model (it faces north). */
    private static float modelTurn(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    /** The swirl, both sides of the pane, opening from its middle (wider first, then taller) and breathing. */
    private void swirl(PoseStack pose, MultiBufferSource buffers, float open, float time) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(SWIRL);
        float e = 1.0F - (1.0F - open) * (1.0F - open) * (1.0F - open);
        float alpha = e * (0.8F + 0.12F * Mth.sin(time / 9.0F));
        float hw = PANE_HALF_W * (0.2F + 0.8F * e) / 16.0F, hh = PANE_HALF_H * (0.08F + 0.92F * e) / 16.0F;
        float x1 = PANE_X / 16.0F - hw, x2 = PANE_X / 16.0F + hw, y1 = PANE_Y / 16.0F - hh, y2 = PANE_Y / 16.0F + hh, z = 0.5F;
        float u1 = sprite.getU(U1), u2 = sprite.getU(U2), v1 = sprite.getV(V1), v2 = sprite.getV(V2);
        VertexConsumer buffer = buffers.getBuffer(Sheets.translucentItemSheet());
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        // the front, seen from -z (its left is +x)
        vertex(buffer, m, n, x2, y2, z, u1, v1, alpha);
        vertex(buffer, m, n, x2, y1, z, u1, v2, alpha);
        vertex(buffer, m, n, x1, y1, z, u2, v2, alpha);
        vertex(buffer, m, n, x1, y2, z, u2, v1, alpha);
        // the back, seen from +z
        vertex(buffer, m, n, x1, y2, z, u1, v1, alpha);
        vertex(buffer, m, n, x1, y1, z, u1, v2, alpha);
        vertex(buffer, m, n, x2, y1, z, u2, v2, alpha);
        vertex(buffer, m, n, x2, y2, z, u2, v1, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float alpha) {
        // full bright, and lit as a face looking up, so it glows the same from every side
        buffer.vertex(m, x, y, z).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** The last trade's items: the one that went in flies into the swirl from the front, what came back flies out and up. */
    private void flights(PortalBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time) {
        float in = (time - be.flyInAge()) / PortalBlockEntity.FLY_IN_TICKS;
        if (in >= 0.0F && in < 1.0F && !be.flyIn().isEmpty()) {
            float e = in * in * (3.0F - 2.0F * in);
            float y = Mth.lerp(e, 0.9F, PANE_Y / 16.0F), z = Mth.lerp(e, -0.3F, 0.5F);
            item(be, be.flyIn(), pose, buffers, light, y, z, 1.0F - 0.9F * in * in, time);
        }
        float out = (time - be.flyOutAge()) / PortalBlockEntity.FLY_OUT_TICKS;
        if (out >= 0.0F && out < 1.0F && !be.flyOut().isEmpty()) {
            float e = 1.0F - (1.0F - out) * (1.0F - out);
            float y = Mth.lerp(e, PANE_Y / 16.0F, 1.05F), z = Mth.lerp(e, 0.5F, -0.35F);
            // grows out of the swirl, then shrinks away as it rises
            item(be, be.flyOut(), pose, buffers, light, y, z, Mth.sin(Math.min(1.0F, out * 1.15F) * PI), time);
        }
    }

    private void item(PortalBlockEntity be, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, float y, float z,
                      float scale, float time) {
        if (scale <= 0.01F) return;
        pose.pushPose();
        pose.translate(0.5D, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(time * 9.0F));
        pose.scale(0.75F * scale, 0.75F * scale, 0.75F * scale);
        items.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
