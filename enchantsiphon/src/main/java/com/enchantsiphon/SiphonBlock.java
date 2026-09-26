package com.enchantsiphon;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SiphonBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public SiphonBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inv, player) -> new SiphonMenu(id, inv, ContainerLevelAccess.create(level, pos)),
                Component.translatable("container.enchantsiphon.siphon"));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        player.openMenu(state.getMenuProvider(level, pos));
        return InteractionResult.CONSUME;
    }

    /** Enchanting glyphs drifting up out of the bowl. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.85D;
        double z = pos.getZ() + 0.5D;
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ENCHANT,
                    x + (random.nextDouble() - 0.5D) * 0.5D, y + random.nextDouble() * 0.3D, z + (random.nextDouble() - 0.5D) * 0.5D,
                    (random.nextDouble() - 0.5D) * 0.2D, 0.15D, (random.nextDouble() - 0.5D) * 0.2D);
        }
    }
}
