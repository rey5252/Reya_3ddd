package com.reya.extraenchants;

import com.reya.extraenchants.enchantment.ModEnchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EnchantmentEvents {

    private static int level(Enchantment enchantment, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
    }

    /** Volley: fire extra arrows in a fan. They cannot be picked up, so no arrows are duplicated. */
    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack bow = event.getBow();
        int volley = level(ModEnchantments.VOLLEY.get(), bow);
        if (volley <= 0 || level.isClientSide || !event.hasAmmo()) return;

        float power = BowItem.getPowerForTime(event.getCharge());
        if (power < 0.1F) return;

        ItemStack ammo = player.getProjectile(bow);
        if (ammo.isEmpty() || !(ammo.getItem() instanceof ArrowItem)) {
            ammo = new ItemStack(Items.ARROW);
        }
        ArrowItem arrowItem = (ArrowItem) ammo.getItem();

        int powerLevel = level(Enchantments.POWER_ARROWS, bow);
        int punchLevel = level(Enchantments.PUNCH_ARROWS, bow);
        boolean flame = level(Enchantments.FLAMING_ARROWS, bow) > 0;

        for (int i = 1; i <= volley; i++) {
            for (int side = -1; side <= 1; side += 2) {
                AbstractArrow arrow = arrowItem.createArrow(level, ammo, player);
                if (bow.getItem() instanceof BowItem bowItem) {
                    arrow = bowItem.customArrow(arrow);
                }
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + side * 8.0F * i,
                        0.0F, power * 3.0F, 1.0F);
                if (power == 1.0F) arrow.setCritArrow(true);
                if (powerLevel > 0) arrow.setBaseDamage(arrow.getBaseDamage() + powerLevel * 0.5D + 0.5D);
                if (punchLevel > 0) arrow.setKnockback(punchLevel);
                if (flame) arrow.setSecondsOnFire(100);
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                level.addFreshEntity(arrow);
            }
        }
    }

    /** Ore Wisdom: more XP from ores; ores without XP (iron, gold, copper...) give some too. */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getMainHandItem();
        int wisdom = level(ModEnchantments.ORE_WISDOM.get(), tool);
        if (wisdom <= 0 || level(Enchantments.SILK_TOUCH, tool) > 0) return;

        int exp = event.getExpToDrop();
        if (exp > 0) {
            event.setExpToDrop(exp * (1 + wisdom));
        } else if (event.getState().is(Tags.Blocks.ORES)) {
            event.setExpToDrop(wisdom + player.getRandom().nextInt(wisdom * 2 + 1));
        }
    }

    /** Battle Wisdom: +50% XP per level from mobs. */
    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null) return;
        int wisdom = level(ModEnchantments.BATTLE_WISDOM.get(), player.getMainHandItem());
        if (wisdom > 0) {
            event.setDroppedExperience((int) Math.ceil(event.getDroppedExperience() * (1.0D + 0.5D * wisdom)));
        }
    }

    /** Lifesteal and Frost Aspect on melee hits. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (event.getSource().getDirectEntity() != attacker) return;
        if (attacker.level().isClientSide) return;
        ItemStack weapon = attacker.getMainHandItem();

        int lifesteal = level(ModEnchantments.LIFESTEAL.get(), weapon);
        if (lifesteal > 0) {
            attacker.heal(event.getAmount() * 0.1F * lifesteal);
        }

        int frost = level(ModEnchantments.FROST_ASPECT.get(), weapon);
        if (frost > 0) {
            LivingEntity target = event.getEntity();
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 * frost, frost), attacker);
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze()));
        }
    }

    /** Beheading: 5% chance per level for a mob head (players too). */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        int beheading = level(ModEnchantments.BEHEADING.get(), player.getMainHandItem());
        if (beheading <= 0) return;

        LivingEntity victim = event.getEntity();
        if (player.getRandom().nextFloat() >= 0.05F * beheading) return;

        ItemStack head = headFor(victim);
        if (head.isEmpty()) return;
        event.getDrops().add(new ItemEntity(victim.level(), victim.getX(), victim.getY(), victim.getZ(), head));
    }

    private static ItemStack headFor(LivingEntity victim) {
        EntityType<?> type = victim.getType();
        if (type == EntityType.ZOMBIE) return new ItemStack(Items.ZOMBIE_HEAD);
        if (type == EntityType.SKELETON) return new ItemStack(Items.SKELETON_SKULL);
        if (type == EntityType.CREEPER) return new ItemStack(Items.CREEPER_HEAD);
        if (type == EntityType.WITHER_SKELETON) return new ItemStack(Items.WITHER_SKELETON_SKULL);
        if (type == EntityType.PIGLIN) return new ItemStack(Items.PIGLIN_HEAD);
        if (victim instanceof Player target) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.getOrCreateTag().putString("SkullOwner", target.getGameProfile().getName());
            return head;
        }
        return ItemStack.EMPTY;
    }
}
