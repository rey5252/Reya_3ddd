package com.reya.starfall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The wall of the shaft SS-01 cuts: rock melted by the beam, white-hot at the lip and darker the deeper it goes.
 * It cools slowly, a step at a time, down to a dark crust.
 */
public class MoltenRockBlock extends Block {
    public static final int HOTTEST = 7;
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, HOTTEST);

    public MoltenRockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HEAT, HOTTEST));
    }

    /** How hot the wall is {@code depth} blocks below the lip. */
    public BlockState at(int depth) {
        int[] steps = {4, 10, 18, 30, 48, 75, 115};
        int heat = HOTTEST;
        for (int step : steps) if (depth >= step) heat--;
        return defaultBlockState().setValue(HEAT, heat);
    }

    public static int light(BlockState state) {
        return switch (state.getValue(HEAT)) {
            case 7 -> 12;
            case 6 -> 10;
            case 5 -> 8;
            case 4 -> 6;
            case 3 -> 3;
            default -> 0;
        };
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HEAT) > 0;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.15F) level.setBlock(pos, state.setValue(HEAT, state.getValue(HEAT) - 1), 2);
    }

    /** The hottest rock drips melt off its open faces and smokes on top. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int heat = state.getValue(HEAT);
        if (heat < 4 || random.nextInt(2 + (HOTTEST - heat) * 3) != 0) return;
        Direction dir = Direction.getRandom(random);
        if (dir == Direction.DOWN || !level.getBlockState(pos.relative(dir)).isAir()) return;
        double x = pos.getX() + 0.5D + dir.getStepX() * 0.52D + (dir.getStepX() == 0 ? random.nextDouble() - 0.5D : 0.0D);
        double y = pos.getY() + 0.5D + dir.getStepY() * 0.52D + (dir.getStepY() == 0 ? random.nextDouble() - 0.5D : 0.0D);
        double z = pos.getZ() + 0.5D + dir.getStepZ() * 0.52D + (dir.getStepZ() == 0 ? random.nextDouble() - 0.5D : 0.0D);
        if (dir == Direction.UP) {
            level.addParticle(heat >= 6 && random.nextBoolean() ? ParticleTypes.LAVA : ParticleTypes.SMOKE, x, y, z, 0.0D, 0.04D, 0.0D);
        } else {
            level.addParticle(ParticleTypes.FALLING_LAVA, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(HEAT) >= 3 && !entity.isSteppingCarefully() && !entity.fireImmune()) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F + state.getValue(HEAT) * 0.5F);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT);
    }
}
