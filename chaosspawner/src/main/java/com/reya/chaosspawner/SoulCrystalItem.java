package com.reya.chaosspawner;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;

/**
 * Empty: right-click a mob to pull its soul out (the mob is gone). Filled crystals of the same mob
 * stack, and go into the Chaos Spawner: the bigger the stack, the more mobs each round counts.
 */
public class SoulCrystalItem extends Item {
    private static final String TAG = "Soul";

    public SoulCrystalItem(Properties properties) {
        super(properties);
    }

    /** The mob a soul crystal or a spawn egg stands for, or null. */
    @Nullable
    public static EntityType<?> soulType(ItemStack stack) {
        if (stack.getItem() instanceof SpawnEggItem egg) return egg.getType(stack.getTag());
        if (!(stack.getItem() instanceof SoulCrystalItem) || stack.getTag() == null || !stack.getTag().contains(TAG)) return null;
        return EntityType.byString(stack.getTag().getString(TAG)).orElse(null);
    }

    public static boolean isSoul(ItemStack stack) {
        return soulType(stack) != null;
    }

    public static ItemStack filled(Item crystal, EntityType<?> type) {
        ItemStack stack = new ItemStack(crystal);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG, EntityType.getKey(type).toString());
        return stack;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (isSoul(stack)) return InteractionResult.PASS;
        if (!(target instanceof Mob) || !target.isAlive() || target.getType().is(Tags.EntityTypes.BOSSES)) return InteractionResult.PASS;
        Level level = player.level();
        if (level instanceof ServerLevel server) {
            ItemStack soul = filled(this, target.getType());
            server.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    20, 0.3D, 0.4D, 0.3D, 0.05D);
            level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.0F);
            target.discard();
            ItemStack held = player.getItemInHand(hand);
            if (!player.getAbilities().instabuild) held.shrink(1);
            if (held.isEmpty()) {
                player.setItemInHand(hand, soul);
            } else if (!player.getInventory().add(soul)) {
                player.drop(soul, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isSoul(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        EntityType<?> type = soulType(stack);
        if (type == null) return Component.translatable("item.chaosspawner.soul_crystal.empty");
        return Component.translatable("item.chaosspawner.soul_crystal.filled", type.getDescription());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(isSoul(stack) ? "item.chaosspawner.soul_crystal.tip_filled"
                : "item.chaosspawner.soul_crystal.tip_empty").withStyle(ChatFormatting.GRAY));
    }
}
