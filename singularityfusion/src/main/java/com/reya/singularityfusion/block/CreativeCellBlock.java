package com.reya.singularityfusion.block;

import javax.annotation.Nullable;

import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A creative energy cell: endless energy into everything next to it (the last thing a core can fuse, or a creative player's). */
public class CreativeCellBlock extends BaseEntityBlock {
    public CreativeCellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeCellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        BlockEntityTicker<CreativeCellBlockEntity> ticker = CreativeCellBlockEntity::serverTick;
        return createTickerHelper(type, SingularityFusion.CREATIVE_CELL_BE.get(), ticker);
    }
}
