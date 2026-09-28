package com.reya.goldenquarry;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Fortune block: placed beside a quarry (either half), the quarry digs with this fortune level. */
public class FortuneBoosterBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
            Block.box(2.0D, 4.0D, 2.0D, 14.0D, 10.0D, 14.0D),
            Block.box(1.0D, 10.0D, 1.0D, 15.0D, 16.0D, 15.0D));

    public final int level;

    public FortuneBoosterBlock(int level, Properties properties) {
        super(properties);
        this.level = level;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.goldenquarry.fortune_block.tip", this.level).withStyle(ChatFormatting.GRAY));
    }
}
