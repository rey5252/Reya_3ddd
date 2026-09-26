package com.reya.chaosspawner.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reya.chaosspawner.ChaosSpawnerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** A small copy of the first soul's mob spinning and bobbing inside the cage, like a vanilla spawner. */
public class ChaosSpawnerRenderer implements BlockEntityRenderer<ChaosSpawnerBlockEntity> {
    public ChaosSpawnerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ChaosSpawnerBlockEntity spawner, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (spawner.getLevel() == null) return;
        LivingEntity entity = ClientEntities.get(spawner.displayType());
        if (entity == null) return;
        float scale = Math.min(0.5F / Math.max(0.3F, entity.getBbHeight()), 0.45F / Math.max(0.3F, entity.getBbWidth()));
        float time = spawner.getLevel().getGameTime() + partialTick;
        pose.pushPose();
        pose.translate(0.5D, 0.22D + Mth.sin(time * 0.08F) * 0.03D, 0.5D);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(time * 3.0F % 360.0F));
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, pose, buffers, light);
        } catch (RuntimeException ignored) {
            // some modded mobs can't be drawn outside the world
        } finally {
            dispatcher.setRenderShadow(true);
        }
        pose.popPose();
    }
}
