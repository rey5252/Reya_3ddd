package com.reya.goldenquarry;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Digs out the square around it, one block at a time, row by row and layer by layer from just
 * below itself down to bedrock. Stone, dirt and other plain blocks go into the upper storage,
 * ores, raw metals, gems and everything smelted into the lower one. Needs energy (makes a bit on
 * its own, takes more from any Forge Energy cable); stops while a redstone signal reaches it.
 */
public class QuarryBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STORAGE = 27, UPGRADES = 3;

    public static final int STATUS_WORKING = 0, STATUS_STOPPED = 1, STATUS_NO_ENERGY = 2, STATUS_FULL = 3,
            STATUS_FINISHED = 4, STATUS_REDSTONE = 5, STATUS_WAITING = 6;
    public static final int FLAG_SHOW_AREA = 2;
    /** How many empty or skipped positions one tick may look through for the next block to dig. */
    private static final int SCAN_BUDGET = 1024;
    private static final int NOT_STARTED = Integer.MIN_VALUE;

    private boolean showArea = true;
    private boolean finished;
    private int status = STATUS_WORKING;
    private int progress;
    private int maxProgress = 20;
    private int cursorY = NOT_STARTED;
    private int cursorIndex;
    private int scanRadius = -1;
    private int radius = -1;
    private long minedBlocks;

    private final ItemStackHandler common = output();
    private final ItemStackHandler valuables = output();
    private final ItemStackHandler upgrades = new ItemStackHandler(UPGRADES) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof QuarryUpgradeItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sync();
        }
    };

    // client side: the spinning drill
    private int clientTicksPerBlock = 20;
    private float drillAngle;
    private float prevDrillAngle;
    private float drillSpeed;

    private final Energy energy = new Energy();

    private ItemStackHandler output() {
        return new ItemStackHandler(STORAGE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    /** Hoppers and pipes may take anything out of both storages; nothing goes in from outside. */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return STORAGE * 2;
        }

        private ItemStackHandler handler(int slot) {
            return slot < STORAGE ? valuables : common;
        }

        @Override
        public @Nonnull ItemStack getStackInSlot(int slot) {
            return handler(slot).getStackInSlot(slot % STORAGE);
        }

        @Override
        public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return handler(slot).extractItem(slot % STORAGE, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return false;
        }
    };
    private final LazyOptional<IItemHandler> automationCap = LazyOptional.of(() -> automation);
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    /** energy and capacity in 15-bit halves (container data syncs as shorts), progress, max, flags, status, layer, radius. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() & 0x7FFF;
                case 1 -> (energy.getEnergyStored() >>> 15) & 0x7FFF;
                case 2 -> energy.getMaxEnergyStored() & 0x7FFF;
                case 3 -> (energy.getMaxEnergyStored() >>> 15) & 0x7FFF;
                case 4 -> progress;
                case 5 -> maxProgress;
                case 6 -> showArea ? FLAG_SHOW_AREA : 0;
                case 7 -> status;
                case 8 -> cursorY == NOT_STARTED ? worldPosition.getY() - 1 : cursorY;
                default -> radius();
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 10;
        }
    };

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(GoldenQuarry.QUARRY_BE.get(), pos, state);
    }

    public ItemStackHandler common() {
        return common;
    }

    public ItemStackHandler valuables() {
        return valuables;
    }

    public ItemStackHandler upgrades() {
        return upgrades;
    }

    public boolean showArea() {
        return showArea;
    }

    /** Client side: the radius and state come with the block update. */
    public boolean isWorking() {
        return status == STATUS_WORKING;
    }

    public int upgrades(QuarryUpgradeItem.Kind kind) {
        int n = 0;
        for (int i = 0; i < UPGRADES; i++) {
            ItemStack s = upgrades.getStackInSlot(i);
            if (s.getItem() instanceof QuarryUpgradeItem u && u.kind == kind) n += s.getCount();
        }
        return Math.min(kind.max, n);
    }

    /** Highest fortune among the fortune upgrades in the slots. */
    public int fortuneLevel() {
        int best = 0;
        for (int i = 0; i < UPGRADES; i++) {
            if (upgrades.getStackInSlot(i).getItem() instanceof QuarryUpgradeItem u && u.kind == QuarryUpgradeItem.Kind.FORTUNE) {
                best = Math.max(best, u.level);
            }
        }
        return best;
    }

    /** Blocks the dig area reaches out on each side. */
    public int radius() {
        if (level != null && level.isClientSide && radius >= 0) return radius;
        return Config.BASE_RADIUS.get() + Config.RADIUS_PER_UPGRADE.get() * upgrades(QuarryUpgradeItem.Kind.RANGE);
    }

    public int ticksPerBlock() {
        return Math.max(1, (int) Math.round(Config.TICKS_PER_BLOCK.get() * Math.pow(0.65D, upgrades(QuarryUpgradeItem.Kind.SPEED))));
    }

    /** Fortune, silk touch and smelting each make a block dearer. */
    public int energyPerBlock() {
        double k = 1.0D + 0.25D * fortuneLevel() + 0.2D * upgrades(QuarryUpgradeItem.Kind.SPEED);
        if (upgrades(QuarryUpgradeItem.Kind.SILK_TOUCH) > 0) k += 1.0D;
        if (upgrades(QuarryUpgradeItem.Kind.SMELTING) > 0) k += 0.5D;
        return (int) Math.round(Config.ENERGY_PER_BLOCK.get() * k);
    }

    // ------------------------------------------------------------------ digging

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.tick(server);
    }

    /** The drill speeds up while the quarry digs (faster with speed upgrades) and runs down when it stops. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity be) {
        float target = be.isWorking() ? 360.0F / Math.max(4, be.clientTicksPerBlock) : 0.0F;
        be.drillSpeed += (target - be.drillSpeed) * 0.15F;
        if (Math.abs(be.drillSpeed) < 0.01F) be.drillSpeed = 0.0F;
        be.prevDrillAngle = be.drillAngle;
        be.drillAngle += be.drillSpeed;
        if (target == 0.0F && Math.abs(be.drillSpeed) < 0.5F) {
            // coming to rest square to the chest, the way the drill hangs when the quarry is idle
            float rest = Math.round(be.drillAngle / 90.0F) * 90.0F;
            be.drillAngle += (rest - be.drillAngle) * 0.2F;
        }
        if (be.drillAngle >= 360.0F) {
            be.drillAngle -= 360.0F;
            be.prevDrillAngle -= 360.0F;
        }
    }

    public float drillAngle(float partialTick) {
        return prevDrillAngle + (drillAngle - prevDrillAngle) * partialTick;
    }

    /** 0 when the drill stands still, 1 at full speed. */
    public float drillSpin() {
        return Math.min(1.0F, Math.abs(drillSpeed) / 18.0F);
    }

    private void tick(ServerLevel level) {
        if (level.getGameTime() % 20L == 0L) pushOutputs(level);
        energy.generate(Config.PASSIVE_GENERATION.get());
        int r = radius();
        if (r != scanRadius) {
            // a new area size: start again from the top, already dug layers are skipped quickly
            scanRadius = r;
            restart();
            sync();
        }
        maxProgress = ticksPerBlock();
        int oldStatus = status;
        status = work(level);
        if (status != oldStatus) sync();
    }

    private int work(ServerLevel level) {
        if (finished) return STATUS_FINISHED;
        if (level.hasNeighborSignal(worldPosition)) return STATUS_REDSTONE;
        if (cursorY == NOT_STARTED) cursorY = worldPosition.getY() - 1;
        BlockPos target = null;
        for (int i = 0; i < SCAN_BUDGET && target == null; i++) {
            if (cursorY < level.getMinBuildHeight()) {
                finished = true;
                progress = 0;
                setChanged();
                return STATUS_FINISHED;
            }
            BlockPos p = cursorPos();
            if (!level.isLoaded(p)) return STATUS_WAITING;
            if (canDig(level, p)) {
                target = p;
            } else {
                advance();
            }
        }
        if (target == null) return STATUS_WORKING;
        int cost = energyPerBlock();
        if (energy.getEnergyStored() < cost) return STATUS_NO_ENERGY;
        if (progress < maxProgress) {
            progress++;
            setChanged();
            if (progress < maxProgress) return STATUS_WORKING;
        }
        BlockState state = level.getBlockState(target);
        FakePlayer digger = FakePlayerFactory.getMinecraft(level);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, target, state, digger);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            // protected by a claim or another mod: leave it
            advance();
            progress = 0;
            return STATUS_WORKING;
        }
        List<ItemStack> commonDrops = new ArrayList<>();
        List<ItemStack> valuableDrops = new ArrayList<>();
        for (ItemStack drop : Block.getDrops(state, level, target, null, digger, tool())) {
            if (drop.isEmpty()) continue;
            ItemStack stack = smelt(level, drop);
            if (isValuable(stack)) {
                valuableDrops.add(stack);
            } else {
                commonDrops.add(stack);
            }
        }
        if (!fits(common, commonDrops) || !fits(valuables, valuableDrops)) return STATUS_FULL;
        level.levelEvent(2001, target, Block.getId(state));
        level.setBlock(target, state.getFluidState().createLegacyBlock(), 3);
        for (ItemStack s : commonDrops) spill(level, ItemHandlerHelper.insertItemStacked(common, s, false));
        for (ItemStack s : valuableDrops) spill(level, ItemHandlerHelper.insertItemStacked(valuables, s, false));
        energy.use(cost);
        minedBlocks++;
        progress = 0;
        advance();
        setChanged();
        return STATUS_WORKING;
    }

    private BlockPos cursorPos() {
        int side = 2 * scanRadius + 1;
        int row = cursorIndex / side, col = cursorIndex % side;
        // snake through the rows so the digging moves on smoothly
        if ((row & 1) == 1) col = side - 1 - col;
        return new BlockPos(worldPosition.getX() - scanRadius + col, cursorY, worldPosition.getZ() - scanRadius + row);
    }

    private void advance() {
        int side = 2 * scanRadius + 1;
        if (++cursorIndex >= side * side) {
            cursorIndex = 0;
            cursorY--;
        }
    }

    private void restart() {
        cursorY = worldPosition.getY() - 1;
        cursorIndex = 0;
        progress = 0;
        finished = false;
        setChanged();
    }

    private boolean canDig(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (state.getDestroySpeed(level, p) < 0.0F) return false;
        return !(Config.SKIP_BLOCK_ENTITIES.get() && state.hasBlockEntity());
    }

    private ItemStack tool() {
        ItemStack pick = new ItemStack(Items.NETHERITE_PICKAXE);
        if (upgrades(QuarryUpgradeItem.Kind.SILK_TOUCH) > 0) {
            pick.enchant(Enchantments.SILK_TOUCH, 1);
        } else {
            int fortune = fortuneLevel();
            if (fortune > 0) pick.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        }
        return pick;
    }

    /** With the smelting upgrade, ores and raw metals come out as ingots, gems and the like. */
    private ItemStack smelt(ServerLevel level, ItemStack stack) {
        if (upgrades(QuarryUpgradeItem.Kind.SMELTING) <= 0) return stack;
        if (!stack.is(Tags.Items.RAW_MATERIALS) && !stack.is(Tags.Items.ORES)) return stack;
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level)
                .map(recipe -> {
                    ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
                    result.setCount(result.getCount() * stack.getCount());
                    return result.isEmpty() ? stack : result;
                })
                .orElse(stack);
    }

    /** Ores, raw metals, gems, ingots and all other non-blocks go to the lower storage. */
    private static boolean isValuable(ItemStack stack) {
        return !(stack.getItem() instanceof BlockItem) || stack.is(Tags.Items.ORES) || stack.is(Tags.Items.STORAGE_BLOCKS);
    }

    private static boolean fits(ItemStackHandler handler, List<ItemStack> stacks) {
        if (stacks.isEmpty()) return true;
        NonNullList<ItemStack> copy = NonNullList.withSize(handler.getSlots(), ItemStack.EMPTY);
        for (int i = 0; i < handler.getSlots(); i++) copy.set(i, handler.getStackInSlot(i).copy());
        ItemStackHandler test = new ItemStackHandler(copy);
        for (ItemStack s : stacks) {
            if (!ItemHandlerHelper.insertItemStacked(test, s.copy(), false).isEmpty()) return false;
        }
        return true;
    }

    private void spill(Level level, ItemStack rest) {
        if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, rest);
    }

    /** Hands the dug items to containers touching the quarry, valuables first. */
    private void pushOutputs(ServerLevel level) {
        for (Direction dir : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbour == null || neighbour instanceof QuarryBlockEntity) continue;
            neighbour.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).ifPresent(target -> {
                for (ItemStackHandler from : new ItemStackHandler[]{valuables, common}) {
                    for (int i = 0; i < from.getSlots(); i++) {
                        ItemStack stack = from.getStackInSlot(i);
                        if (stack.isEmpty()) continue;
                        ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
                        if (rest.getCount() != stack.getCount()) from.setStackInSlot(i, rest);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ buttons

    public void toggleShowArea() {
        showArea = !showArea;
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ------------------------------------------------------------------ menu, saving, sync

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.goldenquarry.quarry");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new QuarryMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Common", common.serializeNBT());
        tag.put("Valuables", valuables.serializeNBT());
        tag.put("Upgrades", upgrades.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.putInt("CursorY", cursorY);
        tag.putInt("CursorIndex", cursorIndex);
        tag.putInt("ScanRadius", scanRadius);
        tag.putLong("Mined", minedBlocks);
        writeState(tag);
    }

    private void writeState(CompoundTag tag) {
        tag.putBoolean("ShowArea", showArea);
        tag.putBoolean("Finished", finished);
        tag.putInt("Status", status);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Common")) common.deserializeNBT(sized(tag.getCompound("Common"), STORAGE));
        if (tag.contains("Valuables")) valuables.deserializeNBT(sized(tag.getCompound("Valuables"), STORAGE));
        if (tag.contains("Upgrades")) upgrades.deserializeNBT(sized(tag.getCompound("Upgrades"), UPGRADES));
        if (tag.contains("Energy")) energy.set(tag.getInt("Energy"));
        if (tag.contains("CursorY")) {
            progress = tag.getInt("Progress");
            cursorY = tag.getInt("CursorY");
            cursorIndex = tag.getInt("CursorIndex");
            scanRadius = tag.getInt("ScanRadius");
            minedBlocks = tag.getLong("Mined");
        }
        if (tag.contains("Radius")) radius = tag.getInt("Radius");
        if (tag.contains("TicksPerBlock")) clientTicksPerBlock = tag.getInt("TicksPerBlock");
        showArea = !tag.contains("ShowArea") || tag.getBoolean("ShowArea");
        finished = tag.getBoolean("Finished");
        status = tag.getInt("Status");
    }

    private static CompoundTag sized(CompoundTag tag, int size) {
        tag.putInt("Size", size);
        return tag;
    }

    /** The client only needs what the glowing border and the drill show. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeState(tag);
        tag.putInt("Radius", radius());
        tag.putInt("TicksPerBlock", ticksPerBlock());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        int r = radius() + 2;
        return new AABB(worldPosition).inflate(r, 1.0D, r);
    }

    @Override
    public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return automationCap.cast();
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        automationCap.invalidate();
        energyCap.invalidate();
    }

    /** Takes energy from cables, never gives any back. */
    private class Energy extends EnergyStorage {
        Energy() {
            super(Config.ENERGY_CAPACITY.get(), Config.MAX_RECEIVE.get(), 0);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int got = super.receiveEnergy(maxReceive, simulate);
            if (got > 0 && !simulate) setChanged();
            return got;
        }

        void generate(int amount) {
            if (amount > 0 && energy < capacity) energy = Math.min(capacity, energy + amount);
        }

        void use(int amount) {
            energy = Math.max(0, energy - amount);
        }

        void set(int value) {
            energy = Math.max(0, Math.min(capacity, value));
        }
    }
}
