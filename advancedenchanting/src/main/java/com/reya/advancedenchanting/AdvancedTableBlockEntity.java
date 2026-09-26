package com.reya.advancedenchanting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Floating book animation like the vanilla table. When curses can be applied (midnight, full moon)
 * and a player stands close, four candles of purple smoke rise around the table.
 */
public class AdvancedTableBlockEntity extends BlockEntity {
    private static final RandomSource RANDOM = RandomSource.create();
    public int time;
    public float flip, oFlip, flipT, flipA;
    public float open, oOpen;
    public float rot, oRot, tRot;

    public AdvancedTableBlockEntity(BlockPos pos, BlockState state) {
        super(AdvancedEnchanting.TABLE_BE.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AdvancedTableBlockEntity be) {
        be.oOpen = be.open;
        be.oRot = be.rot;
        Player player = level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 3.0D, false);
        if (player != null) {
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            be.tRot = (float) Mth.atan2(dz, dx);
            be.open += 0.1F;
            if (be.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float old = be.flipT;
                do {
                    be.flipT += RANDOM.nextInt(4) - RANDOM.nextInt(4);
                } while (old == be.flipT);
            }
            if (EnchantRules.cursesOpen(level) && RANDOM.nextInt(3) == 0) {
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2.0D + Math.PI / 4.0D;
                    level.addParticle(ParticleTypes.WITCH, pos.getX() + 0.5D + Math.cos(a) * 1.1D, pos.getY() + 0.9D,
                            pos.getZ() + 0.5D + Math.sin(a) * 1.1D, 0.0D, 0.05D, 0.0D);
                }
            }
        } else {
            be.tRot += 0.02F;
            be.open -= 0.1F;
        }
        while (be.rot >= Math.PI) be.rot -= (float) (Math.PI * 2);
        while (be.rot < -Math.PI) be.rot += (float) (Math.PI * 2);
        while (be.tRot >= Math.PI) be.tRot -= (float) (Math.PI * 2);
        while (be.tRot < -Math.PI) be.tRot += (float) (Math.PI * 2);
        float diff = be.tRot - be.rot;
        while (diff >= Math.PI) diff -= (float) (Math.PI * 2);
        while (diff < -Math.PI) diff += (float) (Math.PI * 2);
        be.rot += diff * 0.4F;
        be.open = Mth.clamp(be.open, 0.0F, 1.0F);
        be.time++;
        be.oFlip = be.flip;
        float f = Mth.clamp((be.flipT - be.flip) * 0.4F, -0.2F, 0.2F);
        be.flipA += (f - be.flipA) * 0.9F;
        be.flip += be.flipA;
    }
}
