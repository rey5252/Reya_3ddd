package com.reya.alfheimheart.machine.orechid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineRender;
import com.reya.alfheimheart.machine.orechid.OrechidMineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.common.block.BotaniaFlowerBlocks;

/**
 * What moves on the Orechid Mine (the pit is the block's model): Botania's orechid growing at its back corner,
 * swaying; the block being turned floating over the pit, turning and trembling as the craft charges, a green
 * glow round it; a burst when it turns, and the ore it became rising out of it.
 */
public class OrechidMineRenderer implements BlockEntityRenderer<OrechidMineBlockEntity> {
    private static final float TOP = 10.0F / 16.0F;
    private static final float CRAFT_TICKS = 22.0F;

    private final ItemRenderer items;

    public OrechidMineRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(OrechidMineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float time = be.clientAge() + partialTick;
        float act = be.activity(partialTick);
        float prog = be.progress(partialTick);
        float since = be.sinceCrafted(partialTick);
        float flash = since < CRAFT_TICKS ? 1.0F - since / CRAFT_TICKS : 0.0F;
        MachineBlockEntity.Job job = be.clientJob();
        ItemStack block = job != null && !job.units().isEmpty() ? job.units().get(0).copyWithCount(1) : ItemStack.EMPTY;

        float y = TOP + 0.3F + 0.03F * Mth.sin(time * 0.08F);
        float g = act * (0.25F + 0.55F * prog) + flash;
        MachineRender.glow(pose, buffers, 0.5F, y + 0.05F, 0.5F, 0.6F + 0.5F * flash, 0.4F * g, g, 0.35F * g);

        // the orechid at the back corner
        pose.pushPose();
        pose.translate(0.8D, TOP, 0.8D);
        pose.mulPose(Axis.ZP.rotationDegrees(3.0F * Mth.sin(time * 0.045F)));
        pose.scale(0.55F, 0.55F, 0.55F);
        pose.translate(-0.5D, 0.0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(BotaniaFlowerBlocks.orechid.defaultBlockState(), pose, buffers, light,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();

        // the block being turned, trembling more as the craft charges
        if (!block.isEmpty() && flash <= 0.4F) {
            float shake = act * prog * 0.02F;
            MachineRender.item(items, be.getLevel(), block, pose, buffers, light, 0.5F + shake * Mth.sin(time * 3.1F), y,
                    0.5F + shake * Mth.cos(time * 2.7F), 0.5F, time * (1.5F + 6.0F * act));
        }
        if (flash > 0.0F) {
            MachineRender.item(items, be.getLevel(), be.crafted(), pose, buffers, LightTexture.FULL_BRIGHT, 0.5F, y + (1.0F - flash) * 0.45F, 0.5F,
                    0.25F + 0.3F * flash, time * 10.0F);
        }
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
