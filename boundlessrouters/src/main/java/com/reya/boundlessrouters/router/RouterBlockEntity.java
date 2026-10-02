package com.reya.boundlessrouters.router;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.reya.boundlessrouters.BoundlessRouters;
import com.reya.boundlessrouters.RouterConfig;
import com.reya.boundlessrouters.module.ModuleBehaviours;
import com.reya.boundlessrouters.module.ModuleContext;
import com.reya.boundlessrouters.module.ModuleItem;
import com.reya.boundlessrouters.module.ModuleSettings;
import com.reya.boundlessrouters.upgrade.UpgradeItem;
import com.reya.boundlessrouters.upgrade.UpgradeKind;
import com.reya.boundlessrouters.network.Net;
import com.reya.boundlessrouters.network.TransferFxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Item Router: a one-item buffer, nine module slots and five upgrade slots. Every so often (twenty ticks,
 * less with speed upgrades, down to every tick) it runs its modules in order, each moving items to or from the
 * buffer its own way. Hoppers and pipes reach the buffer from any side. A comparator reads how full the buffer
 * is; detector modules give out redstone from the router's sides.
 */
public class RouterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MODULE_SLOTS = 9, UPGRADE_SLOTS = 5;
    /** The menu's numbers: interval, items per run, range, redstone mode, tick counter, the modules that last did
     * something, ticks since, powered, then each upgrade's count. */
    public static final int DATA_COUNT = 8 + UpgradeKind.values().length;

    private final ItemStackHandler buffer = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler modules = new ItemStackHandler(MODULE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof ModuleItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler upgrades = new ItemStackHandler(UPGRADE_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof UpgradeItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            countUpgrades();
        }
    };
    private final LazyOptional<IItemHandler> bufferCap = LazyOptional.of(() -> buffer);

    private RedstoneMode redstone = RedstoneMode.ALWAYS;
    private final int[] upgradeCounts = new int[UpgradeKind.values().length];
    private int counter;
    private boolean powered, pulse;
    private int[] weak = new int[6], strong = new int[6];
    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** Which modules did something the last time any did (a bit each), how long ago, and how long to glow. */
    private int lastRun, sinceRun = 1000, activeFor;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> interval();
                case 1 -> itemsPerRun();
                case 2 -> Math.min(range(), Short.MAX_VALUE);
                case 3 -> redstone.ordinal();
                case 4 -> Math.min(counter, Short.MAX_VALUE);
                case 5 -> lastRun;
                case 6 -> Math.min(sinceRun, 1000);
                case 7 -> powered ? 1 : 0;
                default -> {
                    int k = index - 8;
                    yield k >= 0 && k < upgradeCounts.length ? Math.min(upgradeCounts[k], Short.MAX_VALUE) : 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(BoundlessRouters.ROUTER_BE.get(), pos, state);
    }

    public ItemStackHandler buffer() {
        return buffer;
    }

    public ItemStackHandler modules() {
        return modules;
    }

    public ItemStackHandler upgrades() {
        return upgrades;
    }

    public ContainerData data() {
        return data;
    }

    public RedstoneMode redstoneMode() {
        return redstone;
    }

    public void cycleRedstone(boolean back) {
        redstone = redstone.next(back);
        pulse = false;
        setChanged();
    }

    public void setOwner(GameProfile profile) {
        owner = profile.getId();
        ownerName = profile.getName() == null ? "" : profile.getName();
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    // ------------------------------------------------------------------ upgrades

    private void countUpgrades() {
        Arrays.fill(upgradeCounts, 0);
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.getItem() instanceof UpgradeItem upgrade) upgradeCounts[upgrade.kind().ordinal()] += stack.getCount();
        }
    }

    public int upgradeCount(UpgradeKind kind) {
        return upgradeCounts[kind.ordinal()];
    }

    /** Ticks between runs: twenty, two fewer for each speed upgrade, down to one. */
    public int interval() {
        long ticks = RouterConfig.BASE_TICKS.get() - (long) RouterConfig.SPEED_STEP.get() * upgradeCount(UpgradeKind.SPEED);
        return (int) Math.max(RouterConfig.MIN_TICKS.get(), ticks);
    }

    /** Items each module moves a run: one, doubled by each stack upgrade, up to a full stack. */
    public int itemsPerRun() {
        int stacks = upgradeCount(UpgradeKind.STACK);
        return stacks >= 6 ? 64 : 1 << stacks;
    }

    /** A range with no limit (an infinite range upgrade). */
    public static final int INFINITE = -1;

    /**
     * How far the modules reach, in blocks: eight, or as far as the best range upgrade in the router takes them (they
     * don't add up); INFINITE with an infinite range upgrade.
     */
    public int range() {
        int best = RouterConfig.REACH.get();
        for (UpgradeKind kind : UpgradeKind.values()) {
            if (!kind.isRange() || upgradeCount(kind) == 0) continue;
            if (kind.reach() < 0) return INFINITE;
            best = Math.max(best, kind.reach());
        }
        return best;
    }

    // ------------------------------------------------------------------ running

    public static void serverTick(Level level, BlockPos pos, BlockState state, RouterBlockEntity router) {
        if (level instanceof ServerLevel server) router.tick(server, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
        if (sinceRun < 1000) sinceRun++;
        if (activeFor > 0 && --activeFor == 0) setActive(level, pos, false);
        boolean run;
        if (redstone == RedstoneMode.PULSE) {
            run = pulse;
            pulse = false;
            counter = 0;
        } else {
            if (++counter < interval()) return;
            counter = 0;
            run = switch (redstone) {
                case ALWAYS -> true;
                case HIGH -> powered;
                case LOW -> !powered;
                default -> false;
            };
        }
        if (run) runModules(level, pos);
    }

    /** Runs the modules once, in order (now, whatever the time and redstone say: the game tests use it too). */
    public void runModules(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Direction facing = state.hasProperty(RouterBlock.FACING) ? state.getValue(RouterBlock.FACING) : Direction.NORTH;
        int[] newWeak = new int[6], newStrong = new int[6];
        int perRun = itemsPerRun(), range = range();
        boolean quiet = upgradeCount(UpgradeKind.MUFFLER) > 0;
        List<TransferFxPacket.Flight> flights = new ArrayList<>();
        int ran = 0;
        for (int i = 0; i < MODULE_SLOTS; i++) {
            ItemStack stack = modules.getStackInSlot(i);
            if (!(stack.getItem() instanceof ModuleItem)) continue;
            ModuleSettings settings = new ModuleSettings(stack);
            if (!settings.redstone().allows(powered)) continue;
            ModuleContext ctx = new ModuleContext(this, level, pos, facing, i, stack, settings, perRun, range, powered, quiet, newWeak, newStrong,
                    flights);
            boolean did;
            try {
                did = ModuleBehaviours.run(ctx);
            } catch (RuntimeException e) {
                BoundlessRouters.LOGGER.error("A {} module in the router at {} failed", settings.kind().id(), pos, e);
                did = false;
            }
            if (did) {
                ran |= 1 << i;
                if (settings.terminates()) break;
            }
        }
        setSignals(level, pos, newWeak, newStrong);
        // the players near it see the items go: a line to where each went, the item flying along it
        if (!flights.isEmpty()) {
            Net.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), new TransferFxPacket(flights));
        }
        if (ran != 0) {
            lastRun = ran;
            sinceRun = 0;
            activeFor = Math.max(10, interval() + 2);
            setActive(level, pos, true);
        }
    }

    private void setActive(Level level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(RouterBlock.ACTIVE) && state.getValue(RouterBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(RouterBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    /** A module's stack changed its own state (an extruder's reach, a distributor's turn): saved with the router. */
    public void moduleChanged(int slot) {
        setChanged();
    }

    // ------------------------------------------------------------------ redstone

    public void neighborChanged() {
        if (level == null || level.isClientSide) return;
        boolean now = signalIn(level, worldPosition);
        if (now && !powered && redstone == RedstoneMode.PULSE) pulse = true;
        powered = now;
    }

    /**
     * Whether a redstone signal comes into the router. A side a detector signals out of isn't read: what comes
     * back there (through dust, or a block it powers) is the router's own signal, and it mustn't power itself.
     */
    private boolean signalIn(Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (weak[side.get3DDataValue()] > 0) continue;
            if (level.getSignal(pos.relative(side), side) > 0) return true;
        }
        return false;
    }

    public boolean powered() {
        return powered;
    }

    /** The signal a detector gives out of one of the router's sides (strong: through the block there). */
    public int signal(Direction side, boolean strongOnly) {
        int i = side.get3DDataValue();
        return strongOnly ? strong[i] : weak[i];
    }

    private void setSignals(Level level, BlockPos pos, int[] newWeak, int[] newStrong) {
        if (Arrays.equals(weak, newWeak) && Arrays.equals(strong, newStrong)) return;
        boolean[] strongChanged = new boolean[6];
        for (int i = 0; i < 6; i++) strongChanged[i] = strong[i] != newStrong[i];
        weak = newWeak;
        strong = newStrong;
        setChanged();
        Block block = getBlockState().getBlock();
        level.updateNeighborsAt(pos, block);
        for (Direction side : Direction.values()) {
            if (strongChanged[side.get3DDataValue()]) level.updateNeighborsAt(pos.relative(side), block);
        }
        // the sides it reads changed with the sides it signals out of
        neighborChanged();
    }

    /** 0 empty, up to 15 a full stack in the buffer. */
    public int comparatorSignal() {
        ItemStack stack = buffer.getStackInSlot(0);
        if (stack.isEmpty()) return 0;
        return 1 + (int) (14.0F * stack.getCount() / Math.max(1, Math.min(stack.getMaxStackSize(), buffer.getSlotLimit(0))));
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onLoad() {
        super.onLoad();
        countUpgrades();
        if (level != null && !level.isClientSide) powered = signalIn(level, worldPosition);
    }

    /** The buffer spills when the router goes; its modules and upgrades stay in the router item it drops. */
    public void dropBuffer(Level level, BlockPos pos) {
        ItemStack stack = buffer.getStackInSlot(0);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
            buffer.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    public boolean hasModulesOrUpgrades() {
        for (int i = 0; i < modules.getSlots(); i++) if (!modules.getStackInSlot(i).isEmpty()) return true;
        for (int i = 0; i < upgrades.getSlots(); i++) if (!upgrades.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    /** What the router item keeps of it: modules, upgrades, redstone mode. */
    public CompoundTag saveForItem() {
        CompoundTag tag = new CompoundTag();
        tag.put("Modules", modules.serializeNBT());
        tag.put("Upgrades", upgrades.serializeNBT());
        tag.putByte("Redstone", (byte) redstone.ordinal());
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Buffer", buffer.serializeNBT());
        tag.put("Modules", modules.serializeNBT());
        tag.put("Upgrades", upgrades.serializeNBT());
        tag.putByte("Redstone", (byte) redstone.ordinal());
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.putIntArray("Weak", weak);
        tag.putIntArray("Strong", strong);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadHandler(tag, "Buffer", buffer);
        loadHandler(tag, "Modules", modules);
        loadHandler(tag, "Upgrades", upgrades);
        redstone = RedstoneMode.byId(tag.getByte("Redstone"));
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
            ownerName = tag.getString("OwnerName");
        }
        int[] w = tag.getIntArray("Weak"), s = tag.getIntArray("Strong");
        if (w.length == 6) weak = w;
        if (s.length == 6) strong = s;
        countUpgrades();
    }

    /** Reads a handler's items, keeping its size (a saved size is no business of the save's). */
    private static void loadHandler(CompoundTag tag, String key, ItemStackHandler handler) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) return;
        CompoundTag saved = tag.getCompound(key).copy();
        saved.putInt("Size", handler.getSlots());
        handler.deserializeNBT(saved);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return bufferCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        bufferCap.invalidate();
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block." + BoundlessRouters.MODID + ".router");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RouterMenu(id, inventory, this);
    }
}
