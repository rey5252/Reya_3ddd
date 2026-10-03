package com.reya.starfall;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The plate Gungnir's shock planes the land into. Its glowing hex grid spans four by four blocks; {@link #TILE}
 * says which piece of it this block shows, so a floor laid by position joins up seamlessly.
 */
public class PlateBlock extends Block {
    public static final IntegerProperty TILE = IntegerProperty.create("tile", 0, 15);

    public PlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TILE, 5));
    }

    /** The piece of the grid that belongs at (x, z). */
    public BlockState at(int x, int z) {
        return defaultBlockState().setValue(TILE, Math.floorMod(x, 4) + Math.floorMod(z, 4) * 4);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TILE);
    }
}
