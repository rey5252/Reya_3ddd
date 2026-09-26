package com.reya.cursedseed.item;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.cursedseed.ModRegistry;
import com.reya.cursedseed.block.CursedEarthBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Right-click grass or dirt to turn a 3x3 patch into Cursed Earth. */
public class CursedSeedItem extends Item {

    public CursedSeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        if (!CursedEarthBlock.canCurse(level.getBlockState(center))) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            BlockState cursed = ModRegistry.CURSED_EARTH.get().defaultBlockState();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
                if (pos.equals(center)
                        || (CursedEarthBlock.canCurse(level.getBlockState(pos)) && CursedEarthBlock.isExposed(level, pos))) {
                    level.setBlockAndUpdate(pos, cursed);
                }
            }

            level.playSound(null, center, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, center, SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.BLOCKS, 0.8F, 0.5F);
            ((ServerLevel) level).sendParticles(ParticleTypes.LARGE_SMOKE,
                    center.getX() + 0.5D, center.getY() + 1.1D, center.getZ() + 0.5D,
                    30, 1.0D, 0.2D, 1.0D, 0.02D);

            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cursedseed.cursed_seed.desc").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
