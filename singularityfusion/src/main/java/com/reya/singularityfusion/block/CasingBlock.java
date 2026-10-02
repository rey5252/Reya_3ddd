package com.reya.singularityfusion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Void casing: set down, it gives off a breath of starlight and void, with a soft chime. */
public class CasingBlock extends Block {
    public CasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!(level instanceof ServerLevel server) || old.is(this) || moving) return;
        double x = pos.getX() + 0.5D, y = pos.getY() + 0.5D, z = pos.getZ() + 0.5D;
        server.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.34D, 0.34D, 0.34D, 0.02D);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 16, 0.42D, 0.42D, 0.42D, 0.04D);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 0.7F + 0.3F * level.random.nextFloat());
    }
}
