package com.reya.singularityfusion.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/**
 * A graviton pylon: an injector holding one ingredient for the fusion core it serves (the core finds its pylons and
 * links them). Its renderer raises a great pylon from it, aimed at the singularity over the core, the ingredient
 * floating at its tip.
 */
public class GravitonPylonBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    @Nullable
    private BlockPos core;
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new Handler());

    // the client's own animation state: how lit it looks (catching up with its core's charge), how far its rings have turned
    public float shownCharge, spin;
    public long lastFrameNanos;

    public GravitonPylonBlockEntity(BlockPos pos, BlockState state) {
        super(SingularityFusion.GRAVITON_PYLON_BE.get(), pos, state);
    }

    public ItemStack item() {
        return item;
    }

    public void setItem(ItemStack stack) {
        item = stack.copyWithCount(Math.min(1, stack.getCount()));
        changed();
    }

    /** The core this pylon serves, or null. */
    @Nullable
    public BlockPos core() {
        return core;
    }

    public void link(@Nullable BlockPos core) {
        if (java.util.Objects.equals(this.core, core)) return;
        this.core = core == null ? null : core.immutable();
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ------------------------------------------------------------------ saving and the client's copy

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeShared(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readShared(tag);
    }

    private void writeShared(CompoundTag tag) {
        if (!item.isEmpty()) tag.put("Item", item.save(new CompoundTag()));
        if (core != null) tag.putLong("Core", core.asLong());
    }

    private void readShared(CompoundTag tag) {
        item = tag.contains("Item") ? ItemStack.of(tag.getCompound("Item")) : ItemStack.EMPTY;
        core = tag.contains("Core") ? BlockPos.of(tag.getLong("Core")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeShared(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readShared(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        readShared(tag == null ? new CompoundTag() : tag);
    }

    /** The pylon rises from the block toward the singularity, a few blocks off. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(6.0D);
    }

    // ------------------------------------------------------------------ hoppers and pipes

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    /** One slot of one item: a hopper fills an empty pylon, and empties a full one. */
    private class Handler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int slot) {
            return item;
        }

        @Override
        @Nonnull
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !item.isEmpty()) return stack;
            if (!simulate) setItem(stack.copyWithCount(1));
            ItemStack left = stack.copy();
            left.shrink(1);
            return left;
        }

        @Override
        @Nonnull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || item.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = item.copy();
            if (!simulate) setItem(ItemStack.EMPTY);
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return true;
        }
    }
}
