package com.reya.advancedenchanting;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Right-click a vanilla enchanting table to turn it into the advanced one. */
public class TableUpgradeItem extends Item {
    public TableUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, AdvancedEnchanting.TABLE.get().defaultBlockState());
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                        40, 0.4D, 0.3D, 0.4D, 0.6D);
            }
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.advancedenchanting.table_upgrade.tip").withStyle(ChatFormatting.GRAY));
    }
}
