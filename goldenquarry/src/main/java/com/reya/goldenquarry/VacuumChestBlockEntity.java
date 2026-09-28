package com.reya.goldenquarry;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * The vacuum chest: pulls in the items lying around it (up to 8 blocks out every way) into its 27
 * slots. An item filter of five entries: with the white list it takes only those (or everything
 * while the filter is empty), with the black list everything but those. Pipes and hoppers can take
 * the items out on any side.
 */
public class VacuumChestBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 27;
    public static final int FILTERS = 5;
    public static final int MAX_RANGE = 8;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler filter = new ItemStackHandler(FILTERS) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final LazyOptional<ItemStackHandler> itemCap = LazyOptional.of(() -> items);
    private int range = MAX_RANGE;
    private boolean blacklist;
    private boolean showArea;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> range;
                case 1 -> blacklist ? 1 : 0;
                case 2 -> showArea ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> range = value;
                case 1 -> blacklist = value != 0;
                case 2 -> showArea = value != 0;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public VacuumChestBlockEntity(BlockPos pos, BlockState state) {
        super(GoldenQuarry.VACUUM_CHEST_BE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public ItemStackHandler filter() {
        return filter;
    }

    public int range() {
        return range;
    }

    public boolean showArea() {
        return showArea;
    }

    public void setRange(int value) {
        range = Math.max(1, Math.min(MAX_RANGE, value));
        changed();
    }

    public void toggleBlacklist() {
        blacklist = !blacklist;
        changed();
    }

    public void toggleShowArea() {
        showArea = !showArea;
        changed();
    }

    /** Saved, and sent to the clients around (the working area's outline is drawn there). */
    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** The area it pulls from: range blocks out from the chest in every direction. */
    public AABB area() {
        return new AABB(worldPosition).inflate(range);
    }

    public boolean accepts(ItemStack stack) {
        boolean listed = false, any = false;
        for (int i = 0; i < FILTERS; i++) {
            ItemStack f = filter.getStackInSlot(i);
            if (f.isEmpty()) continue;
            any = true;
            if (ItemStack.isSameItem(f, stack)) listed = true;
        }
        return blacklist ? !listed : !any || listed;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VacuumChestBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 4 != 0) return;
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, be.area(),
                e -> e.isAlive() && !e.hasPickUpDelay() && !e.getItem().isEmpty());
        for (ItemEntity e : found) {
            ItemStack stack = e.getItem();
            if (!be.accepts(stack)) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(be.items, stack.copy(), false);
            if (rest.getCount() == stack.getCount()) continue;
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + 0.2D, e.getZ(), 6, 0.1D, 0.1D, 0.1D, 0.4D);
            }
            if (rest.isEmpty()) e.discard();
            else e.setItem(rest);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.goldenquarry.vacuum_chest");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new VacuumChestMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.put("Filter", filter.serializeNBT());
        tag.putInt("Range", range);
        tag.putBoolean("Blacklist", blacklist);
        tag.putBoolean("ShowArea", showArea);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        if (tag.contains("Filter")) filter.deserializeNBT(tag.getCompound("Filter"));
        range = tag.contains("Range") ? Math.max(1, Math.min(MAX_RANGE, tag.getInt("Range"))) : MAX_RANGE;
        blacklist = tag.getBoolean("Blacklist");
        showArea = tag.getBoolean("ShowArea");
    }

    /** The clients only need the range and whether to show the area. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Range", range);
        tag.putBoolean("ShowArea", showArea);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        range = tag.contains("Range") ? tag.getInt("Range") : MAX_RANGE;
        showArea = tag.getBoolean("ShowArea");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) handleUpdateTag(tag);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }
}
