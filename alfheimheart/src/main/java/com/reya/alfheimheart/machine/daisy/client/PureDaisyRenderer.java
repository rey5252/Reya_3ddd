package com.reya.alfheimheart.machine.daisy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineRender;
import com.reya.alfheimheart.machine.daisy.PureDaisyBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import vazkii.botania.common.block.BotaniaFlowerBlocks;

/**
 * What moves on the Pure Daisy (the planter is the block's model): Botania's own pure daisy growing in it,
 * swaying, glowing white while it works (mana blue while mana hurries it); the blocks being purified set round
 * it on the planter's rim, a white light rising over them; a flash when they turn, and what they became rising
 * out of it.
 */
public class PureDaisyRenderer implements BlockEntityRenderer<PureDaisyBlockEntity> {
    private static final float TOP = 9.0F / 16.0F;
    private static final float CRAFT_TICKS = 24.0F;
    /** The eight places round the daisy on the planter's rim (x, z). */
    private static final float[][] SPOTS = {{0.2F, 0.2F}, {0.5F, 0.16F}, {0.8F, 0.2F}, {0.84F, 0.5F}, {0.8F, 0.8F}, {0.5F, 0.84F},
            {0.2F, 0.8F}, {0.16F, 0.5F}};

    private final ItemRenderer items;

    public PureDaisyRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(PureDaisyBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float time = be.clientAge() + partialTick;
        float act = be.activity(partialTick);
        float prog = be.progress(partialTick);
        float since = be.sinceCrafted(partialTick);
        float flash = since < CRAFT_TICKS ? 1.0F - since / CRAFT_TICKS : 0.0F;
        boolean blue = act > 0.5F && be.clientMana() > 0;
        MachineBlockEntity.Job job = be.clientJob();
        ItemStack block = job != null && !job.units().isEmpty() ? job.units().get(0) : ItemStack.EMPTY;
        int n = block.isEmpty() ? 0 : Math.min(SPOTS.length, block.getCount());

        // the glows: round the daisy, over the blocks, the flash
        float g = 0.1F + act * (0.3F + 0.25F * prog) + 0.08F * MachineRender.pulse(time, 50.0F, 0.0F) + flash * 0.8F;
        MachineRender.glow(pose, buffers, 0.5F, TOP + 0.3F, 0.5F, 0.7F + 0.6F * flash, (blue ? 0.45F : 0.9F) * g, (blue ? 0.85F : 0.95F) * g, g);
        for (int i = 0; i < n; i++) {
            float s = act * (0.15F + 0.5F * prog);
            MachineRender.glow(pose, buffers, SPOTS[i][0], TOP + 0.12F + 0.1F * prog, SPOTS[i][1], 0.3F, s, s, s);
        }

        // the daisy, swaying
        BlockState daisy = BotaniaFlowerBlocks.pureDaisy.defaultBlockState();
        pose.pushPose();
        pose.translate(0.5D, TOP, 0.5D);
        pose.mulPose(Axis.ZP.rotationDegrees(3.0F * Mth.sin(time * 0.05F)));
        pose.mulPose(Axis.XP.rotationDegrees(2.5F * Mth.sin(time * 0.037F + 1.0F)));
        float s = 0.85F + 0.05F * flash;
        pose.scale(s, s, s);
        pose.translate(-0.5D, 0.0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(daisy, pose, buffers, act > 0.3F ? LightTexture.FULL_BRIGHT : light,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();

        // the blocks being purified round it, what they became after a craft
        ItemStack shown = flash > 0.0F && !be.crafted().isEmpty() ? be.crafted() : block;
        int count = flash > 0.0F && !be.crafted().isEmpty() ? Math.max(1, n) : n;
        for (int i = 0; i < count; i++) {
            float bob = 0.015F * Mth.sin(time * 0.1F + i);
            MachineRender.item(items, be.getLevel(), shown, pose, buffers, light, SPOTS[i][0], TOP + 0.02F + bob, SPOTS[i][1], 0.32F,
                    i * 45.0F + time * 0.5F);
        }
        if (flash > 0.0F) {
            float rise = 1.0F - flash;
            MachineRender.item(items, be.getLevel(), be.crafted(), pose, buffers, LightTexture.FULL_BRIGHT, 0.5F, TOP + 0.45F + rise * 0.5F,
                    0.5F, 0.2F + 0.3F * flash, time * 10.0F);
        }
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
