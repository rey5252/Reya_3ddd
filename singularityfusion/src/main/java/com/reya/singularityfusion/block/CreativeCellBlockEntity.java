package com.reya.singularityfusion.block;

import javax.annotation.Nullable;

import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/** Pushes as much energy as they take into its neighbours every tick, and gives it to whatever pulls. */
public class CreativeCellBlockEntity extends BlockEntity {
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> new IEnergyStorage() {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            return Math.max(0, max);
        }

        @Override
        public int getEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    });

    public CreativeCellBlockEntity(BlockPos pos, BlockState state) {
        super(SingularityFusion.CREATIVE_CELL_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeCellBlockEntity cell) {
        for (Direction side : Direction.values()) {
            BlockEntity next = level.getBlockEntity(pos.relative(side));
            if (next == null || next instanceof CreativeCellBlockEntity) continue;
            next.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(storage -> storage.receiveEnergy(Integer.MAX_VALUE, false));
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }
}
