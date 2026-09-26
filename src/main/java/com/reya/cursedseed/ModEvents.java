package com.reya.cursedseed;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ModEvents {

    /** Wither skeletons ("black skeletons") have a small chance to drop a Cursed Seed. */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton skeleton)) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;

        double chance = Config.SEED_DROP_CHANCE.get()
                + Config.SEED_DROP_LOOTING_BONUS.get() * event.getLootingLevel();
        if (skeleton.getRandom().nextDouble() < chance) {
            event.getDrops().add(new ItemEntity(skeleton.level(),
                    skeleton.getX(), skeleton.getY(), skeleton.getZ(),
                    new ItemStack(ModRegistry.CURSED_SEED.get())));
        }
    }

    /** Monsters that spawn naturally on Cursed Earth are empowered too. */
    @SubscribeEvent
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || !(mob instanceof Enemy)) return;
        if (mob.level().getBlockState(mob.blockPosition().below()).is(ModRegistry.CURSED_EARTH.get())) {
            Empowerment.empower(mob);
        }
    }
}
