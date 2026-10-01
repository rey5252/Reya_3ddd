package com.reya.alfheimheart.machine.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * What the machines' renderers draw alike, in blocks (0 to 1 across the block): soft glows facing the camera,
 * flat overlays on a face, columns of light, floating items. Glows go through an additive render type (eyes):
 * their colour is their strength, and black adds nothing.
 * <p>
 * Each helper asks the buffer source for its buffer itself: drawing an item in between ends the batch of
 * whatever came before, so a buffer can't be kept from one drawing to the next.
 */
public final class MachineRender {
    public static final ResourceLocation GLOW = texture("glow"), BEAM = texture("beam"), MANA = texture("mana");
    public static final int MANA_FRAMES = 16;
    public static final float PI = (float) Math.PI, DEG = PI / 180.0F;

    public static ResourceLocation texture(String name) {
        return new ResourceLocation(AlfheimHeart.MODID, "textures/entity/machines/" + name + ".png");
    }

    /** The items in the input slots, one of each slot, at most `max` (what the machine is about to use). */
    public static List<ItemStack> inputs(MachineBlockEntity be, int max) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < MachineBlockEntity.INPUTS && out.size() < max; i++) {
            ItemStack stack = be.clientItems().get(MachineBlockEntity.INPUT_START + i);
            if (!stack.isEmpty()) out.add(stack.copyWithCount(1));
        }
        return out;
    }

    /** What the machine shows being used: its craft's items, or else what its inputs hold. */
    public static List<ItemStack> shown(MachineBlockEntity be, int max) {
        MachineBlockEntity.Job job = be.clientJob();
        if (job != null && !job.units().isEmpty()) {
            List<ItemStack> units = job.units();
            return units.size() > max ? units.subList(0, max) : units;
        }
        return inputs(be, max);
    }

    /** A glow facing the camera round (x, y, z), `size` across, adding the colour (r, g, b). */
    public static void glow(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float size, float r, float g, float b) {
        if (r + g + b <= 0.005F || size <= 0.001F) return;
        VertexConsumer buffer = buffers.getBuffer(RenderType.eyes(GLOW));
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float h = size / 2.0F;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        // both windings: the render type culls back faces
        vertex(buffer, m, n, -h, h, 0.0F, 0.0F, 0.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, -h, -h, 0.0F, 0.0F, 1.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, h, -h, 0.0F, 1.0F, 1.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, h, h, 0.0F, 1.0F, 0.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, h, h, 0.0F, 1.0F, 0.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, h, -h, 0.0F, 1.0F, 1.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, -h, -h, 0.0F, 0.0F, 1.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        vertex(buffer, m, n, -h, h, 0.0F, 0.0F, 0.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /**
     * A flat square lying at height y over (x1..x2, z1..z2), seen from above (and, for culling render types, from
     * below too when `both`), with the texture's (u1..u2, v1..v2) on it.
     */
    public static void flat(PoseStack pose, MultiBufferSource buffers, RenderType type, float x1, float z1, float x2, float z2, float y,
                            float u1, float v1, float u2, float v2, float r, float g, float b, float a, int light, boolean both) {
        VertexConsumer buffer = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(buffer, m, n, x1, y, z1, u1, v1, r, g, b, a, light);
        vertex(buffer, m, n, x1, y, z2, u1, v2, r, g, b, a, light);
        vertex(buffer, m, n, x2, y, z2, u2, v2, r, g, b, a, light);
        vertex(buffer, m, n, x2, y, z1, u2, v1, r, g, b, a, light);
        if (both) {
            vertex(buffer, m, n, x2, y, z1, u2, v1, r, g, b, a, light);
            vertex(buffer, m, n, x2, y, z2, u2, v2, r, g, b, a, light);
            vertex(buffer, m, n, x1, y, z2, u1, v2, r, g, b, a, light);
            vertex(buffer, m, n, x1, y, z1, u1, v1, r, g, b, a, light);
        }
    }

    /** A column of light from y1 up to y2 over (x, z), `width` across: two crossed panes, fading upwards (beam.png). */
    public static void beam(PoseStack pose, MultiBufferSource buffers, float x, float z, float y1, float y2, float width, float r, float g, float b) {
        if (r + g + b <= 0.005F) return;
        VertexConsumer buffer = buffers.getBuffer(RenderType.eyes(BEAM));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float h = width / 2.0F;
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? h : 0.0F, dz = k == 0 ? 0.0F : h;
            vertex(buffer, m, n, x - dx, y2, z - dz, 0.0F, 0.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
            vertex(buffer, m, n, x - dx, y1, z - dz, 0.0F, 1.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
            vertex(buffer, m, n, x + dx, y1, z + dz, 1.0F, 1.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
            vertex(buffer, m, n, x + dx, y2, z + dz, 1.0F, 0.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
            // and its back
            vertex(buffer, m, n, x + dx, y2, z + dz, 1.0F, 0.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
            vertex(buffer, m, n, x + dx, y1, z + dz, 1.0F, 1.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
            vertex(buffer, m, n, x - dx, y1, z - dz, 0.0F, 1.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
            vertex(buffer, m, n, x - dx, y2, z - dz, 0.0F, 0.0F, r, g, b, 1.0F, LightTexture.FULL_BRIGHT);
        }
    }

    /** An item floating at (x, y, z), turned `spin` degrees round the upright, `scale` of its dropped size. */
    public static void item(ItemRenderer items, Level level, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
                            float x, float y, float z, float scale, float spin) {
        if (stack.isEmpty() || scale <= 0.01F) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(scale, scale, scale);
        items.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               float red, float green, float blue, float alpha, int light) {
        buffer.vertex(m, x, y, z).color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** 0 to 1 and back over `period` ticks, eased. */
    public static float pulse(float time, float period, float offset) {
        return 0.5F + 0.5F * Mth.sin(time / period * 2.0F * PI + offset);
    }

    /** (r, g, b) of 0xRRGGBB, scaled. */
    public static float[] rgb(int color, float k) {
        return new float[]{(color >> 16 & 0xFF) / 255.0F * k, (color >> 8 & 0xFF) / 255.0F * k, (color & 0xFF) / 255.0F * k};
    }

    private MachineRender() {
    }
}
