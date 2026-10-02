package com.reya.alfheimheart.machine.apothecary.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.apothecary.PetalApothecaryBlockEntity;
import com.reya.alfheimheart.machine.client.MachineRender;
import com.reya.alfheimheart.machine.client.MachineScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * What moves in the Petal Apothecary (the bowl, its stem and foot are the block's model): the water in the bowl,
 * light running over it and taking the petals' colours as a craft charges; the petals turning on it, closing in
 * on the middle; the flower they make rising out of the water in a glow; a ripple and a flash when it is done.
 */
public class PetalApothecaryRenderer implements BlockEntityRenderer<PetalApothecaryBlockEntity> {
    private static final float WATER = 12.2F / 16.0F, IN1 = 2.0F / 16.0F, IN2 = 14.0F / 16.0F;
    private static final float CRAFT_TICKS = 20.0F;

    private final ItemRenderer items;

    public PetalApothecaryRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(PetalApothecaryBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float time = be.clientAge() + partialTick;
        float act = be.activity(partialTick);
        float prog = be.progress(partialTick);
        float since = be.sinceCrafted(partialTick);
        float flash = since < CRAFT_TICKS ? 1.0F - since / CRAFT_TICKS : 0.0F;
        MachineBlockEntity.Job job = be.clientJob();
        List<ItemStack> petals = job != null ? job.units() : MachineRender.inputs(be, 8);
        float[] tint = tint(petals, act * prog);

        // the water
        int frame = (int) (time / 2.0F) % MachineRender.MANA_FRAMES;
        float v1 = frame / (float) MachineRender.MANA_FRAMES, v2 = (frame + 1) / (float) MachineRender.MANA_FRAMES;
        MachineRender.flat(pose, buffers, RenderType.entityTranslucent(MachineRender.MANA), IN1, IN1, IN2, IN2, WATER, 0.0F, v1, 1.0F, v2,
                tint[0], tint[1], tint[2], 0.85F, light, true);
        // the glows: a ripple after a craft, the light round the rising flower
        if (flash > 0.0F) {
            float r = (1.0F - flash) * 0.42F + 0.05F, s = flash * 0.6F;
            MachineRender.flat(pose, buffers, RenderType.eyes(MachineRender.GLOW), 0.5F - r, 0.5F - r, 0.5F + r, 0.5F + r, WATER + 0.003F,
                    0.0F, 0.0F, 1.0F, 1.0F, s, s, s, 1.0F, LightTexture.FULL_BRIGHT, false);
        }
        float rise = act * prog;
        float flowerY = WATER + 0.05F + 0.35F * rise + 0.02F * Mth.sin(time * 0.1F);
        if (job != null && act > 0.05F) {
            float g = act * (0.2F + 0.6F * prog);
            MachineRender.glow(pose, buffers, 0.5F, flowerY + 0.1F, 0.5F, 0.4F + 0.3F * rise, g * tint[0], g * tint[1], g * tint[2]);
        }

        // the petals turning on the water
        int n = Math.min(10, petals.size());
        float phase = be.phase(partialTick);
        for (int i = 0; i < n; i++) {
            float a = phase * 1.4F * MachineRender.DEG + i * 2.0F * MachineRender.PI / n;
            float r = 0.32F * (1.0F - 0.75F * rise);
            pose.pushPose();
            pose.translate(0.5F + Mth.cos(a) * r, WATER + 0.01F, 0.5F + Mth.sin(a) * r);
            pose.mulPose(Axis.YP.rotationDegrees(-a / MachineRender.DEG + 90.0F));
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.scale(0.22F, 0.22F, 0.22F);
            items.renderStatic(petals.get(i), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }
        // the flower rising out of the water, and the one just made flying up
        if (job != null && !job.outputs().isEmpty() && act > 0.05F) {
            MachineRender.item(items, be.getLevel(), job.outputs().get(0), pose, buffers, LightTexture.FULL_BRIGHT, 0.5F, flowerY, 0.5F,
                    0.15F + 0.3F * rise, time * 3.0F);
        }
        if (flash > 0.0F) {
            MachineRender.item(items, be.getLevel(), be.crafted(), pose, buffers, LightTexture.FULL_BRIGHT, 0.5F, WATER + 0.4F + (1.0F - flash) * 0.5F,
                    0.5F, 0.2F + 0.3F * flash, time * 10.0F);
        }
    }

    /** The water's colour: clear blue, taking the petals' colours as the craft charges. */
    private static float[] tint(List<ItemStack> petals, float p) {
        float r = 0.5F, g = 0.8F, b = 1.0F;
        if (petals.isEmpty() || p <= 0.0F) return new float[]{r, g, b};
        float pr = 0, pg = 0, pb = 0;
        for (ItemStack petal : petals) {
            int c = MachineScreen.dyeColour(petal);
            pr += (c >> 16 & 0xFF) / 255.0F;
            pg += (c >> 8 & 0xFF) / 255.0F;
            pb += (c & 0xFF) / 255.0F;
        }
        int n = petals.size();
        float k = 0.5F * p;
        return new float[]{Mth.lerp(k, r, pr / n), Mth.lerp(k, g, pg / n), Mth.lerp(k, b, pb / n)};
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
