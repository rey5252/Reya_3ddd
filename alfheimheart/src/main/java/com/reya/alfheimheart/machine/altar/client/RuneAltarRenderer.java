package com.reya.alfheimheart.machine.altar.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.altar.RuneAltarBlockEntity;
import com.reya.alfheimheart.machine.client.MachineRender;
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
 * What moves on the Runic Altar (the plinth, the body, the top and its crystals are the block's model):
 * <ul>
 * <li>the ingredients circle over the altar's top, slowly while it waits, faster and higher as a craft charges;</li>
 * <li>the rune being made takes shape over the top's hollow, growing as the mana goes in, in a glow;</li>
 * <li>the top's gold ring and runes light up (altar_glow.png over the top), brighter as the craft charges;</li>
 * <li>the crystals on the corners glow;</li>
 * <li>a finished rune leaps out of the hollow in a flash and fades as it rises.</li>
 * </ul>
 */
public class RuneAltarRenderer implements BlockEntityRenderer<RuneAltarBlockEntity> {
    private static final ResourceLocation TOP_GLOW = MachineRender.texture("altar_glow");
    private static final float TOP = 12.0F / 16.0F;
    /** The crystals' tips (x, z), a little over the top. */
    private static final float[][] CRYSTALS = {{2.5F, 2.5F}, {13.5F, 2.5F}, {2.5F, 13.5F}, {13.5F, 13.5F}};
    private static final float CRAFT_TICKS = 24.0F;

    private final ItemRenderer items;

    public RuneAltarRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(RuneAltarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float time = be.clientAge() + partialTick;
        float act = be.activity(partialTick);
        float prog = be.progress(partialTick);
        float since = be.sinceCrafted(partialTick);
        float flash = since < CRAFT_TICKS ? 1.0F - since / CRAFT_TICKS : 0.0F;
        MachineBlockEntity.Job job = be.clientJob();
        List<ItemStack> shown = MachineRender.shown(be, 8);

        // the top's ring and runes
        float k = 0.1F + (job != null ? 0.15F : 0.0F) + act * (0.25F + 0.45F * prog) + 0.6F * flash
                + 0.06F * MachineRender.pulse(time, 60.0F, 0.0F);
        VertexConsumer top = buffers.getBuffer(RenderType.eyes(TOP_GLOW));
        MachineRender.flat(pose, top, 1.0F / 16.0F, 1.0F / 16.0F, 15.0F / 16.0F, 15.0F / 16.0F, TOP + 0.002F,
                1.0F / 16.0F, 1.0F / 16.0F, 15.0F / 16.0F, 15.0F / 16.0F, 0.4F * k, 0.85F * k, k, 1.0F, LightTexture.FULL_BRIGHT, false);

        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(MachineRender.GLOW));
        // the corner crystals
        for (int i = 0; i < CRYSTALS.length; i++) {
            float c = 0.12F + 0.12F * MachineRender.pulse(time, 50.0F, i * 1.7F) + act * 0.25F + flash * 0.4F;
            MachineRender.glow(pose, glow, CRYSTALS[i][0] / 16.0F, 14.6F / 16.0F, CRYSTALS[i][1] / 16.0F, 0.32F, 0.3F * c, 0.8F * c, c);
        }

        // the ingredients circling over the top
        int n = shown.size();
        float phase = be.phase(partialTick);
        float radius = 0.33F + 0.03F * act;
        for (int i = 0; i < n; i++) {
            float a = phase * 2.0F * MachineRender.DEG + i * 2.0F * MachineRender.PI / n;
            float y = TOP + 0.28F + 0.035F * Mth.sin(time * 0.1F + i * 1.3F) + act * (0.06F + 0.08F * prog);
            float x = 0.5F + Mth.cos(a) * radius, z = 0.5F + Mth.sin(a) * radius;
            MachineRender.item(items, be.getLevel(), shown.get(i), pose, buffers, light, x, y, z, 0.32F, time * 2.5F + i * 45.0F);
            if (act > 0.05F) {
                float s = act * (0.25F + 0.35F * prog);
                MachineRender.glow(pose, glow, x, y + 0.07F, z, 0.28F, 0.25F * s, 0.65F * s, 0.8F * s);
            }
        }

        // the rune taking shape over the hollow
        if (job != null && !job.outputs().isEmpty()) {
            float grow = 0.12F + act * 0.25F * prog;
            float y = TOP + 0.18F + 0.18F * prog * act + 0.02F * Mth.sin(time * 0.15F);
            float g = 0.15F + act * (0.3F + 0.6F * prog);
            MachineRender.glow(pose, glow, 0.5F, y + 0.06F, 0.5F, 0.35F + 0.4F * prog * act, 0.3F * g, 0.8F * g, g);
            MachineRender.item(items, be.getLevel(), job.outputs().get(0), pose, buffers, LightTexture.FULL_BRIGHT, 0.5F, y, 0.5F, grow,
                    time * (4.0F + 10.0F * act));
        }

        // a finished rune leaps out in a flash
        if (flash > 0.0F) {
            MachineRender.glow(pose, glow, 0.5F, TOP + 0.3F, 0.5F, 1.4F * (1.0F - flash * 0.4F), 0.4F * flash, 0.9F * flash, flash);
            float rise = 1.0F - flash;
            MachineRender.item(items, be.getLevel(), be.crafted(), pose, buffers, LightTexture.FULL_BRIGHT, 0.5F,
                    TOP + 0.35F + rise * 0.55F, 0.5F, 0.15F + 0.35F * flash, time * 12.0F);
        }
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
