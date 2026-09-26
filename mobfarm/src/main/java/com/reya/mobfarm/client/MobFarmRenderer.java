package com.reya.mobfarm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reya.mobfarm.farm.MobFarmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;

/** Draws a tiny, slowly turning copy of the caught mob standing inside the cage. */
public class MobFarmRenderer implements BlockEntityRenderer<MobFarmBlockEntity> {

    public MobFarmRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MobFarmBlockEntity farm, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        LivingEntity entity = ClientEntities.get(farm.mobType());
        if (entity == null || farm.getLevel() == null) return;

        // Fit inside the glass case: 12/16 wide, 11/16 tall above the base plate.
        float scale = Math.min(0.62F / Math.max(0.3F, entity.getBbHeight()),
                0.7F / Math.max(0.3F, entity.getBbWidth()));
        float angle = ((farm.getLevel().getGameTime() + partialTick) * 1.5F) % 360.0F;

        pose.pushPose();
        pose.translate(0.5D, 0.125D, 0.5D);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(angle));

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, pose, buffers, light);
        } catch (RuntimeException ignored) {
            // some modded mobs can't be drawn outside the world; the cage just stays empty
        } finally {
            dispatcher.setRenderShadow(true);
        }
        pose.popPose();
    }
}
