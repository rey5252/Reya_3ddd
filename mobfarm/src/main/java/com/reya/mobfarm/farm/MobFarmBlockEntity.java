package com.reya.mobfarm.farm;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.reya.mobfarm.FarmTier;
import com.reya.mobfarm.ModRegistry;
import com.reya.mobfarm.lasso.LassoItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

/**
 * Holds a caught lasso (slot 0) and nine output slots. Every {@link FarmTier#ticks} ticks it rolls the
 * mob's loot table as if a player had killed it, and once a second pushes loot into neighbouring
 * containers. A redstone signal pauses it.
 */
public class MobFarmBlockEntity extends BlockEntity implements MenuProvider {
    public static final int LASSO_SLOT = 0;
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_COUNT = 9;

    public static final int STATUS_NO_MOB = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_REDSTONE = 2;
    public static final int STATUS_FULL = 3;

    private final ItemStackHandler items = new ItemStackHandler(OUTPUT_START + OUTPUT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == LASSO_SLOT) {
                progress = 0;
                lootEntity = null;
                sync();
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == LASSO_SLOT && LassoItem.hasMob(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == LASSO_SLOT ? 1 : 64;
        }
    };

    /** Hoppers and pipes may only take loot out. */
    private final LazyOptional<IItemHandler> outputCap =
            LazyOptional.of(() -> new RangedWrapper(items, OUTPUT_START, OUTPUT_START + OUTPUT_COUNT));

    private int progress;
    private int status;
    @Nullable
    private LivingEntity lootEntity;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress();
                case 2 -> status;
                case 3 -> tier().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) progress = value;
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public MobFarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MOB_FARM.get(), pos, state);
    }

    public FarmTier tier() {
        return getBlockState().getBlock() instanceof MobFarmBlock block ? block.tier : FarmTier.WOODEN;
    }

    public int maxProgress() {
        return tier().ticks;
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public ContainerData getData() {
        return data;
    }

    public ItemStack getLasso() {
        return items.getStackInSlot(LASSO_SLOT);
    }

    public void setLasso(ItemStack stack) {
        items.setStackInSlot(LASSO_SLOT, stack);
    }

    @Nullable
    public EntityType<?> mobType() {
        return LassoItem.getType(getLasso());
    }

    public List<ItemStack> drops() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < items.getSlots(); i++) {
            if (!items.getStackInSlot(i).isEmpty()) list.add(items.getStackInSlot(i));
        }
        return list;
    }

    // ---------------------------------------------------------------- ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, MobFarmBlockEntity farm) {
        if (level instanceof ServerLevel serverLevel) farm.tick(serverLevel);
    }

    private void tick(ServerLevel level) {
        if (level.getGameTime() % 20L == 0L) pushOutputs(level);

        EntityType<?> type = mobType();
        if (type == null) {
            status = STATUS_NO_MOB;
            progress = 0;
            return;
        }
        if (level.hasNeighborSignal(worldPosition)) {
            status = STATUS_REDSTONE;
            return;
        }
        if (!hasFreeOutputSlot()) {
            status = STATUS_FULL;
            return;
        }
        status = STATUS_RUNNING;
        if (++progress >= maxProgress()) {
            progress = 0;
            produceLoot(level, type);
        }
    }

    private boolean hasFreeOutputSlot() {
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    /** Rolls the mob's loot table as a player kill (so blaze rods, wither skulls etc. can drop). */
    private void produceLoot(ServerLevel level, EntityType<?> type) {
        if (lootEntity == null || lootEntity.getType() != type) {
            Entity created = type.create(level);
            if (!(created instanceof LivingEntity living)) return;
            lootEntity = living;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        lootEntity.setPos(center.x, center.y, center.z);

        FakePlayer killer = FakePlayerFactory.getMinecraft(level);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, lootEntity)
                .withParameter(LootContextParams.ORIGIN, center)
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        LootTable table = level.getServer().getLootData().getLootTable(lootEntity.getLootTable());
        for (ItemStack stack : table.getRandomItems(params)) {
            ItemStack rest = insertOutput(stack);
            if (!rest.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, center.x, worldPosition.getY() + 1.1D, center.z, rest));
            }
        }
        setChanged();
    }

    /** Adds to the output slots directly (they refuse normal insertion). Returns what didn't fit. */
    private ItemStack insertOutput(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && !rest.isEmpty(); i++) {
            ItemStack slot = items.getStackInSlot(i);
            if (!slot.isEmpty() && ItemHandlerHelper.canItemStacksStack(slot, rest)) {
                int move = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (move > 0) {
                    items.setStackInSlot(i, ItemHandlerHelper.copyStackWithSize(slot, slot.getCount() + move));
                    rest.shrink(move);
                }
            }
        }
        for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT && !rest.isEmpty(); i++) {
            if (items.getStackInSlot(i).isEmpty()) {
                items.setStackInSlot(i, rest);
                rest = ItemStack.EMPTY;
            }
        }
        return rest;
    }

    /** Moves loot into any neighbouring container (chest, barrel, hopper...). */
    private void pushOutputs(ServerLevel level) {
        for (Direction dir : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbour == null || neighbour instanceof MobFarmBlockEntity) continue;
            neighbour.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).ifPresent(target -> {
                for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_COUNT; i++) {
                    ItemStack stack = items.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
                    if (rest.getCount() != stack.getCount()) items.setStackInSlot(i, rest);
                }
            });
        }
    }

    // ---------------------------------------------------------------- saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        progress = tag.getInt("Progress");
    }

    /** Clients only need the lasso, to draw the tiny mob in the cage. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Lasso", getLasso().save(new CompoundTag()));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        items.setStackInSlot(LASSO_SLOT, ItemStack.of(tag.getCompound("Lasso")));
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return outputCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        outputCap.invalidate();
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MobFarmMenu(id, inventory, this);
    }
}
