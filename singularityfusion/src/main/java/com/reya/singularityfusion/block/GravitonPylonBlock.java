package com.reya.singularityfusion.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A graviton pylon's plinth. Use it with an item to put one on it (an ingredient for the fusion), with an empty hand to
 * take it back.
 */
public class GravitonPylonBlock extends BaseEntityBlock {
    public GravitonPylonBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravitonPylonBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof GravitonPylonBlockEntity pylon)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (pylon.item().isEmpty()) {
            if (held.isEmpty()) return InteractionResult.PASS;
            if (!level.isClientSide) {
                pylon.setItem(held.copyWithCount(1));
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND || !held.isEmpty() && !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack out = pylon.item();
            pylon.setItem(ItemStack.EMPTY);
            if (!player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GravitonPylonBlockEntity pylon && !pylon.item().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, pylon.item());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
