package com.reya.chaosspawner;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public class ChaosSpawnerBlock extends BaseEntityBlock {
    public ChaosSpawnerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChaosSpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ChaosSpawner.SPAWNER_BE.get(), ChaosSpawnerBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ChaosSpawnerBlockEntity be && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, be, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ChaosSpawnerBlockEntity be) {
            for (int i = 0; i < ChaosSpawnerBlockEntity.SLOTS; i++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.items().getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Soul flames drifting round the cage. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        level.addParticle(random.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL,
                pos.getX() + 0.2D + random.nextDouble() * 0.6D, pos.getY() + 0.3D + random.nextDouble() * 0.6D,
                pos.getZ() + 0.2D + random.nextDouble() * 0.6D, 0.0D, 0.02D, 0.0D);
    }
}
