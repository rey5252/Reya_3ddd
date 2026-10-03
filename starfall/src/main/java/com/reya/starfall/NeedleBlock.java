package com.reya.starfall;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Gungnir's needle: two by two blocks, each a quarter of a round black column. {@link #PART} picks the plain
 * shaft, a glowing ember band or the silver foot; {@link #QUARTER} turns the piece to its corner of the column.
 */
public class NeedleBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    public static final IntegerProperty QUARTER = IntegerProperty.create("quarter", 0, 3);
    public static final int PLAIN = 0, BAND = 1, FOOT = 2;

    public NeedleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, PLAIN).setValue(QUARTER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, QUARTER);
    }
}
