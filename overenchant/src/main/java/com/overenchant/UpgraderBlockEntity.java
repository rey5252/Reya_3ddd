package com.overenchant;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only holds the floating book's animation, the same way the enchanting table does. */
public class UpgraderBlockEntity extends BlockEntity {
    private static final RandomSource RANDOM = RandomSource.create();
    public int time;
    public float flip, oFlip, flipT, flipA;
    public float open, oOpen;
    public float rot, oRot, tRot;

    public UpgraderBlockEntity(BlockPos pos, BlockState state) {
        super(OverEnchant.UPGRADER_BE.get(), pos, state);
    }

    public static void bookAnimationTick(Level level, BlockPos pos, BlockState state, UpgraderBlockEntity be) {
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
