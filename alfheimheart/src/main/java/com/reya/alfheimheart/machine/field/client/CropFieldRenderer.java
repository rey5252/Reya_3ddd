package com.reya.alfheimheart.machine.field.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.client.MachineRender;
import com.reya.alfheimheart.machine.field.CropFieldBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What moves on the Crop Field (the tilled bed is the block's model): the plants of the seeds in its inputs, each
 * kind once, growing through their stages as a cycle goes by, swaying, a soft green glow over them while it
 * works, and a shake when they are harvested.
 */
public class CropFieldRenderer implements BlockEntityRenderer<CropFieldBlockEntity> {
    private static final float SOIL = 7.0F / 16.0F;
    private static final float CRAFT_TICKS = 16.0F;
    private static final float[][] SPOTS = {{0.3F, 0.3F}, {0.7F, 0.3F}, {0.3F, 0.7F}, {0.7F, 0.7F}};

    public CropFieldRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CropFieldBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float time = be.clientAge() + partialTick;
        float act = be.activity(partialTick);
        float prog = be.progress(partialTick);
        float since = be.sinceCrafted(partialTick);
        float flash = since < CRAFT_TICKS ? 1.0F - since / CRAFT_TICKS : 0.0F;
        MachineBlockEntity.Job job = be.clientJob();
        List<ItemStack> seeds = job != null ? job.units() : MachineRender.inputs(be, SPOTS.length);
        int n = Math.min(SPOTS.length, seeds.size());

        if (act > 0.05F || flash > 0.0F) {
            float g = act * 0.2F + flash * 0.4F;
            MachineRender.glow(pose, buffers, 0.5F, SOIL + 0.35F, 0.5F, 1.0F, 0.3F * g, g, 0.3F * g);
        }
        for (int i = 0; i < n; i++) {
            BlockState state = growing(seeds.get(i), act > 0.05F ? prog : 1.0F);
            if (state == null) continue;
            float breeze = 2.0F * Mth.sin(time * 0.05F + i * 1.7F) + 9.0F * flash * Mth.sin(since * 1.3F);
            pose.pushPose();
            pose.translate(SPOTS[i][0], SOIL, SPOTS[i][1]);
            pose.mulPose(Axis.ZP.rotationDegrees(breeze));
            pose.scale(0.42F, 0.42F, 0.42F);
            pose.translate(-0.5D, 0.0D, -0.5D);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /** The plant of a seed at a stage of its growth (crops through their ages; others as they are planted, or grown). */
    private static BlockState growing(ItemStack seed, float growth) {
        if (!(seed.getItem() instanceof BlockItem item)) return null;
        Block block = item.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.getStateForAge(Mth.clamp(Math.round(growth * crop.getMaxAge()), 0, crop.getMaxAge()));
        }
        BlockState grown = CropFieldBlockEntity.grown(seed);
        return grown != null && !(grown.getBlock() == block) && growth < 1.0F ? block.defaultBlockState() : grown;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
