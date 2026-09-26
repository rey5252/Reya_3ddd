package com.reya.mobfarm;

import com.reya.mobfarm.lasso.LassoItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class LassoEvents {

    /**
     * Catching happens here, before the mob's own interaction (villager trading, milking...),
     * so the lasso works on every mob.
     */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof LassoItem) || LassoItem.hasMob(stack)) return;
        if (!(event.getTarget() instanceof LivingEntity target) || !LassoItem.canCapture(target)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player.level().isClientSide) return;

        LassoItem.capture(stack, target);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(event.getHand()));
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    12, target.getBbWidth() / 2, target.getBbHeight() / 2, target.getBbWidth() / 2, 0.02D);
            level.playSound(null, target.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        target.discard();
    }
}
