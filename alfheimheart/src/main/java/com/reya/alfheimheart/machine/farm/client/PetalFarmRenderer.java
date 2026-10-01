package com.reya.alfheimheart.machine.farm.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineRender;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.farm.PetalFarmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * What moves on the Petal Farm (the planter is the block's model): the flowers in its inputs growing in its soil,
 * each kind once, swaying in a breeze that quickens while it works, a soft glow of their colours over them, and
 * a shake when a cycle's petals are picked.
 */
public class PetalFarmRenderer implements BlockEntityRenderer<PetalFarmBlockEntity> {
    private static final float SOIL = 7.0F / 16.0F;
    private static final float CRAFT_TICKS = 16.0F;
    /** Where the flowers grow (x, z), up to six. */
    private static final float[][] SPOTS = {{0.27F, 0.3F}, {0.5F, 0.27F}, {0.73F, 0.3F}, {0.27F, 0.7F}, {0.5F, 0.73F}, {0.73F, 0.7F}};

    public PetalFarmRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PetalFarmBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float time = be.clientAge() + partialTick;
        float act = be.activity(partialTick);
        float since = be.sinceCrafted(partialTick);
        float flash = since < CRAFT_TICKS ? 1.0F - since / CRAFT_TICKS : 0.0F;
        MachineBlockEntity.Job job = be.clientJob();
        List<ItemStack> flowers = job != null ? job.units() : MachineRender.inputs(be, SPOTS.length);
        int n = Math.min(SPOTS.length, flowers.size());

        for (int i = 0; i < n; i++) {
            if (act > 0.05F || flash > 0.0F) {
                int c = MachineScreen.dyeColour(flowers.get(i));
                float g = act * 0.25F * (0.6F + 0.4F * MachineRender.pulse(time, 40.0F, i)) + flash * 0.5F;
                MachineRender.glow(pose, buffers, SPOTS[i][0], SOIL + 0.4F, SPOTS[i][1], 0.4F, (c >> 16 & 0xFF) / 255.0F * g,
                        (c >> 8 & 0xFF) / 255.0F * g, (c & 0xFF) / 255.0F * g);
            }
        }
        for (int i = 0; i < n; i++) {
            Block block = Block.byItem(flowers.get(i).getItem());
            if (block == Blocks.AIR) continue;
            float breeze = (2.5F + 4.0F * act) * Mth.sin(time * (0.05F + 0.04F * act) + i * 1.3F) + 10.0F * flash * Mth.sin(since * 1.4F);
            pose.pushPose();
            pose.translate(SPOTS[i][0], SOIL, SPOTS[i][1]);
            pose.mulPose(Axis.ZP.rotationDegrees(breeze));
            pose.mulPose(Axis.XP.rotationDegrees(breeze * 0.6F));
            pose.scale(0.5F, 0.5F, 0.5F);
            pose.translate(-0.5D, 0.0D, -0.5D);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
