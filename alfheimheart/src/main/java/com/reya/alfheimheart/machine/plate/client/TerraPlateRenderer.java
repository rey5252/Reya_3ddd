package com.reya.alfheimheart.machine.plate.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineRender;
import com.reya.alfheimheart.machine.plate.TerraPlateBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * What moves on the Terrestrial Plate (the base, the plate and its posts are the block's model):
 * <ul>
 * <li>the ingredients hover over the plate; as a craft charges they circle faster, rise and close in on its
 * middle, where a ball of light grows, turning from mana blue to terrasteel green, with a column of light over it;</li>
 * <li>the plate's sun lights up in the same colour (plate_glow.png over the plate);</li>
 * <li>the crystals on the posts glow;</li>
 * <li>a finished craft bursts in a green flash, and what it made rises out of it and fades.</li>
 * </ul>
 */
public class TerraPlateRenderer implements BlockEntityRenderer<TerraPlateBlockEntity> {
    private static final ResourceLocation TOP_GLOW = MachineRender.texture("plate_glow");
    private static final float TOP = 5.0F / 16.0F;
    private static final float[][] CRYSTALS = {{1.0F, 1.0F}, {15.0F, 1.0F}, {1.0F, 15.0F}, {15.0F, 15.0F}};
    private static final float CRAFT_TICKS = 30.0F;
    /** Mana blue to terrasteel green. */
    private static final float[] BLUE = {0.25F, 0.6F, 1.0F}, GREEN = {0.35F, 1.0F, 0.45F};

    private final ItemRenderer items;

    public TerraPlateRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    private static float[] colour(float p) {
        return new float[]{Mth.lerp(p, BLUE[0], GREEN[0]), Mth.lerp(p, BLUE[1], GREEN[1]), Mth.lerp(p, BLUE[2], GREEN[2])};
    }

    @Override
    public void render(TerraPlateBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float time = be.clientAge() + partialTick;
        float act = be.activity(partialTick);
        float prog = be.progress(partialTick);
        float charge = act * prog;
        float since = be.sinceCrafted(partialTick);
        float flash = since < CRAFT_TICKS ? 1.0F - since / CRAFT_TICKS : 0.0F;
        MachineBlockEntity.Job job = be.clientJob();
        List<ItemStack> shown = MachineRender.shown(be, 6);
        float[] c = colour(Math.max(charge, flash));

        // the glows first (all in one batch), then the items
        float k = 0.12F + (job != null ? 0.12F : 0.0F) + act * (0.2F + 0.6F * prog) + 0.7F * flash
                + 0.05F * MachineRender.pulse(time, 70.0F, 0.0F);
        MachineRender.flat(pose, buffers, RenderType.eyes(TOP_GLOW), 1.0F / 16.0F, 1.0F / 16.0F, 15.0F / 16.0F, 15.0F / 16.0F,
                TOP + 0.002F, 1.0F / 16.0F, 1.0F / 16.0F, 15.0F / 16.0F, 15.0F / 16.0F, c[0] * k, c[1] * k, c[2] * k, 1.0F,
                LightTexture.FULL_BRIGHT, false);
        for (int i = 0; i < CRYSTALS.length; i++) {
            float g = 0.12F + 0.12F * MachineRender.pulse(time, 45.0F, i * 1.9F) + act * 0.3F + flash * 0.5F;
            MachineRender.glow(pose, buffers, CRYSTALS[i][0] / 16.0F, 7.4F / 16.0F, CRYSTALS[i][1] / 16.0F, 0.3F, 0.3F * g, 0.75F * g, g);
        }

        // the ingredients, closing in as the craft charges
        int n = shown.size();
        float phase = be.phase(partialTick);
        float radius = 0.34F * (1.0F - 0.72F * charge);
        float orbY = TOP + 0.38F + 0.2F * charge;
        float[][] at = new float[n][];
        for (int i = 0; i < n; i++) {
            float a = phase * 1.6F * MachineRender.DEG + i * 2.0F * MachineRender.PI / n;
            float y = TOP + 0.3F + 0.25F * charge + 0.04F * Mth.sin(time * 0.09F + i * 2.1F);
            at[i] = new float[]{0.5F + Mth.cos(a) * radius, y, 0.5F + Mth.sin(a) * radius};
            if (act > 0.05F) {
                float s = act * (0.2F + 0.5F * prog);
                MachineRender.glow(pose, buffers, at[i][0], y + 0.08F, at[i][2], 0.3F, c[0] * s, c[1] * s, c[2] * s);
            }
        }
        // the ball of light and the column over it
        if (act > 0.01F || flash > 0.0F) {
            float size = 0.12F + 0.55F * charge + 0.04F * Mth.sin(time * 0.4F) * act + 1.2F * flash;
            float s = act * (0.35F + 0.65F * prog) + flash;
            MachineRender.glow(pose, buffers, 0.5F, orbY, 0.5F, size, c[0] * s, c[1] * s, c[2] * s);
            MachineRender.glow(pose, buffers, 0.5F, orbY, 0.5F, size * 0.45F, 0.8F * s, 0.9F * s, 0.8F * s);
            float b = act * (0.15F + 0.45F * prog) + 0.6F * flash;
            MachineRender.beam(pose, buffers, 0.5F, 0.5F, TOP, TOP + 1.6F + 1.2F * charge, 0.16F + 0.2F * charge, c[0] * b, c[1] * b,
                    c[2] * b);
        }

        for (int i = 0; i < n; i++) {
            MachineRender.item(items, be.getLevel(), shown.get(i), pose, buffers, light, at[i][0], at[i][1], at[i][2],
                    0.38F * (1.0F - 0.35F * charge), time * 3.0F + i * 60.0F);
        }
        // what a finished craft made rises out of the flash
        if (flash > 0.0F) {
            float rise = 1.0F - flash;
            MachineRender.item(items, be.getLevel(), be.crafted(), pose, buffers, LightTexture.FULL_BRIGHT, 0.5F, orbY + rise * 0.6F, 0.5F,
                    0.15F + 0.4F * flash, time * 10.0F);
        }
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
