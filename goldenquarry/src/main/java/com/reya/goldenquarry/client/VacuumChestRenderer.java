package com.reya.goldenquarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reya.goldenquarry.VacuumChestBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Outlines the vacuum chest's working area in purple while its red button is on. */
public class VacuumChestRenderer implements BlockEntityRenderer<VacuumChestBlockEntity> {
    public VacuumChestRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(VacuumChestBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.showArea()) return;
        int r = be.range();
        AABB box = new AABB(-r, -r, -r, r + 1, r + 1, r + 1);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 0.66F, 0.25F, 1.0F, 1.0F);
    }

    @Override
    public boolean shouldRenderOffScreen(VacuumChestBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
