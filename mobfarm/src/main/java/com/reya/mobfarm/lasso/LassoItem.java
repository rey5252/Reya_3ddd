package com.reya.mobfarm.lasso;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;

/**
 * Right-click a mob to catch it (see {@link com.reya.mobfarm.LassoEvents}); right-click a block to let it go,
 * or put the lasso into a mob farm.
 */
public class LassoItem extends Item {
    private static final String TAG = "CapturedEntity";

    public LassoItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static EntityType<?> getType(ItemStack stack) {
        if (!(stack.getItem() instanceof LassoItem) || !stack.hasTag() || !stack.getTag().contains(TAG)) return null;
        return EntityType.byString(stack.getTag().getString(TAG)).orElse(null);
    }

    public static boolean hasMob(ItemStack stack) {
        return getType(stack) != null;
    }

    public static boolean canCapture(LivingEntity entity) {
        return entity instanceof Mob && entity.isAlive() && !(entity instanceof Player)
                && !entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    public static void capture(ItemStack stack, LivingEntity entity) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG, EntityType.getKey(entity.getType()).toString());
    }

    /** A lasso that already holds a mob of this type. */
    public static ItemStack withMob(Item lasso, EntityType<?> type) {
        ItemStack stack = new ItemStack(lasso);
        stack.getOrCreateTag().putString(TAG, EntityType.getKey(type).toString());
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasMob(stack);
    }

    /** Releases the caught mob next to the clicked block. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        EntityType<?> type = getType(stack);
        if (type == null) return InteractionResult.PASS;

        Level level = context.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            if (type.spawn(serverLevel, pos, MobSpawnType.SPAWN_EGG) != null) {
                stack.getTag().remove(TAG);
                if (stack.getTag().isEmpty()) stack.setTag(null);
                level.playSound(null, pos, SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        EntityType<?> type = getType(stack);
        if (type != null) {
            tooltip.add(Component.translatable("item.mobfarm.lasso.captured", type.getDescription())
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.mobfarm.lasso.release").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.mobfarm.lasso.empty").withStyle(ChatFormatting.GRAY));
        }
    }
}
