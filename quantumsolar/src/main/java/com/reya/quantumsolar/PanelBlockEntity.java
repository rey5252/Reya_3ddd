package com.reya.quantumsolar;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Makes FE each tick (a solar panel under the open sky by day, half of it in rain; a quantum
 * generator always), keeps it up to its capacity and pushes it into every neighbour that takes it.
 * Others can only take energy out of it.
 */
public class PanelBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STATUS_WORKING = 0, STATUS_NIGHT = 1, STATUS_NO_SKY = 2, STATUS_RAIN = 3, STATUS_FULL = 4;

    private final Panels.Panel panel;
    private final Storage energy;
    private final LazyOptional<IEnergyStorage> energyCap;
    private int making, status;

    /** The menu's view of it: energy and making as two 16-bit halves each (containers send shorts), and the status. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored() & 0xFFFF;
                case 1 -> energy.getEnergyStored() >>> 16;
                case 2 -> making & 0xFFFF;
                case 3 -> making >>> 16;
                default -> status;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return PanelMenu.DATA;
        }
    };

    public PanelBlockEntity(BlockPos pos, BlockState state) {
        super(QuantumSolar.PANEL_BE.get(), pos, state);
        panel = state.getBlock() instanceof PanelBlock b ? b.panel() : Panels.ALL[0];
        energy = new Storage(panel.capacity(), panel.output());
        energyCap = LazyOptional.of(() -> energy);
    }

    public Panels.Panel panel() {
        return panel;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PanelBlockEntity be) {
        be.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        int made = panel.generation();
        if (!panel.quantum()) {
            if (!level.canSeeSky(pos.above())) {
                made = 0;
                status = STATUS_NO_SKY;
            } else if (!level.isDay()) {
                made = 0;
                status = STATUS_NIGHT;
            } else if (level.isRaining() || level.isThundering()) {
                made /= 2;
                status = STATUS_RAIN;
            } else {
                status = STATUS_WORKING;
            }
        } else {
            status = STATUS_WORKING;
        }
        int before = energy.getEnergyStored();
        int room = energy.getMaxEnergyStored() - before;
        making = Math.min(made, room);
        if (made > 0 && room <= 0) status = STATUS_FULL;
        energy.fill(making);
        push(level, pos);
        if (energy.getEnergyStored() != before) setChanged();
    }

    /** Shares what it can give this tick out among the neighbours that take energy. */
    private void push(Level level, BlockPos pos) {
        int left = Math.min(energy.getEnergyStored(), panel.output());
        if (left <= 0) return;
        for (Direction dir : Direction.values()) {
            if (left <= 0) break;
            BlockEntity other = level.getBlockEntity(pos.relative(dir));
            if (other == null || other instanceof PanelBlockEntity) continue;
            IEnergyStorage target = other.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).orElse(null);
            if (target == null || !target.canReceive()) continue;
            int given = target.receiveEnergy(left, false);
            if (given > 0) {
                energy.drain(given);
                left -= given;
            }
        }
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PanelMenu(id, inventory, this, data);
    }

    @Override
    public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.set(tag.getInt("Energy"));
    }

    /** Energy that only goes out (to others) and that it fills itself. */
    private static final class Storage extends EnergyStorage {
        Storage(int capacity, int maxExtract) {
            super(capacity, 0, maxExtract);
        }

        void fill(int amount) {
            energy = Math.min(capacity, energy + Math.max(0, amount));
        }

        void drain(int amount) {
            energy = Math.max(0, energy - amount);
        }

        void set(int amount) {
            energy = Math.max(0, Math.min(capacity, amount));
        }
    }
}
