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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * The fortune converter: turns ores into what they give when mined with Fortune, one ore per
 * operation (or everything in its input at once with the stack upgrade). Ores go into the upper
 * storage (by hand, or from pipes and hoppers, best from above); the results go into the lower
 * storage and on into any container touching its sides or bottom. Upgrades: a fortune module
 * (level 2, 5 or 10: that level of Fortune), autosmelt (the results come out smelted), stack (the
 * whole input in one operation) and the infinite engine (no energy needed). Needs energy otherwise
 * (makes a little on its own, takes more from any Forge Energy cable); stops while a redstone
 * signal reaches it.
 */
public class QuarryBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STORAGE = 27, UPGRADES = 4;
    /** The upgrade slot left of the bars: only the fortune modules go there, the other upgrades go right. */
    public static final int FORTUNE_SLOT = 3;

    public static final int STATUS_WORKING = 0, STATUS_IDLE = 1, STATUS_NO_ENERGY = 2, STATUS_FULL = 3, STATUS_REDSTONE = 5;

    private int status = STATUS_IDLE;
    private int progress;
    private int maxProgress = 20;
    private long converted;

    private final ItemStackHandler input = new ItemStackHandler(STORAGE) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isOre(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(STORAGE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler upgrades = new ItemStackHandler(UPGRADES) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return fitsUpgradeSlot(slot, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sync();
        }
    };

    public static boolean fitsUpgradeSlot(int slot, ItemStack stack) {
        return stack.getItem() instanceof QuarryUpgradeItem up && (up.kind == QuarryUpgradeItem.Kind.FORTUNE) == (slot == FORTUNE_SLOT);
    }

    /** What the converter takes: anything tagged as an ore. */
    public static boolean isOre(ItemStack stack) {
        return stack.is(Tags.Items.ORES);
    }

    // client side: the spinning drill
    private float drillAngle;
    private float prevDrillAngle;
    private float drillSpeed;

    private final Energy energy = new Energy();

    /**
     * Pipes and hoppers see the input first (ores go in, nothing comes out of it), then the output
     * (things come out, nothing goes in).
     */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return STORAGE * 2;
        }

        @Override
        public @Nonnull ItemStack getStackInSlot(int slot) {
            return slot < STORAGE ? input.getStackInSlot(slot) : output.getStackInSlot(slot - STORAGE);
        }

        @Override
        public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return slot < STORAGE ? input.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot < STORAGE ? ItemStack.EMPTY : output.extractItem(slot - STORAGE, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot < STORAGE && isOre(stack);
        }
    };
    private final LazyOptional<IItemHandler> automationCap = LazyOptional.of(() -> automation);
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    /** energy and capacity in 15-bit halves (container data syncs as shorts), progress, max, ores waiting, status, fortune. */
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
                case 6 -> Math.min(0x7FFF, oresWaiting());
                case 7 -> status;
                default -> fortuneLevel();
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 9;
        }
    };

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(GoldenQuarry.QUARRY_BE.get(), pos, state);
    }

    public ItemStackHandler input() {
        return input;
    }

    public ItemStackHandler output() {
        return output;
    }

    public ItemStackHandler upgrades() {
        return upgrades;
    }

    /** Client side: the state comes with the block update. */
    public boolean isWorking() {
        return status == STATUS_WORKING;
    }

    public boolean has(QuarryUpgradeItem.Kind kind) {
        for (int i = 0; i < UPGRADES; i++) {
            if (upgrades.getStackInSlot(i).getItem() instanceof QuarryUpgradeItem u && u.kind == kind) return true;
        }
        return false;
    }

    /** The fortune module's level (0 without one). */
    public int fortuneLevel() {
        return upgrades.getStackInSlot(FORTUNE_SLOT).getItem() instanceof QuarryUpgradeItem u && u.kind == QuarryUpgradeItem.Kind.FORTUNE ? u.level : 0;
    }

    /** Energy one ore costs: more with fortune and autosmelt, nothing with the infinite engine. */
    public int energyPerOre() {
        if (has(QuarryUpgradeItem.Kind.INFINITE)) return 0;
        double k = 1.0D + 0.25D * fortuneLevel();
        if (has(QuarryUpgradeItem.Kind.SMELTING)) k += 0.5D;
        return (int) Math.round(Config.ENERGY_PER_ORE.get() * k);
    }

    private int oresWaiting() {
        int n = 0;
        for (int i = 0; i < STORAGE; i++) n += input.getStackInSlot(i).getCount();
        return n;
    }

    // ------------------------------------------------------------------ converting

    public static void serverTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.tick(server);
    }

    /** The drill turns slowly while the converter works and comes to rest square to the chest when it stops. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity be) {
        float target = be.isWorking() ? 2.5F : 0.0F;   // slow and steady: one turn in about 7 seconds
        be.drillSpeed += (target - be.drillSpeed) * 0.15F;
        if (Math.abs(be.drillSpeed) < 0.01F) be.drillSpeed = 0.0F;
        be.prevDrillAngle = be.drillAngle;
        be.drillAngle += be.drillSpeed;
        if (target == 0.0F && Math.abs(be.drillSpeed) < 0.5F) {
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
        maxProgress = Config.TICKS_PER_OPERATION.get();
        int oldStatus = status;
        status = work(level);
        if (status != oldStatus) sync();
    }

    private int work(ServerLevel level) {
        if (level.hasNeighborSignal(worldPosition)) return STATUS_REDSTONE;
        if (oresWaiting() == 0) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            return STATUS_IDLE;
        }
        int cost = energyPerOre();
        if (energy.getEnergyStored() < cost) return STATUS_NO_ENERGY;
        if (progress < maxProgress) {
            progress++;
            setChanged();
            if (progress < maxProgress) return STATUS_WORKING;
        }
        // one ore, or with the stack upgrade every ore in the input, as far as energy and room allow
        boolean all = has(QuarryUpgradeItem.Kind.STACK);
        int done = 0;
        for (int slot = 0; slot < STORAGE; slot++) {
            while (!input.getStackInSlot(slot).isEmpty()) {
                if (energy.getEnergyStored() < cost) break;
                List<ItemStack> results = convert(level, input.getStackInSlot(slot));
                if (!fits(output, results)) break;
                input.extractItem(slot, 1, false);
                for (ItemStack r : results) ItemHandlerHelper.insertItemStacked(output, r, false);
                energy.use(cost);
                done++;
                if (!all) break;
            }
            if (done > 0 && !all) break;
        }
        progress = 0;
        setChanged();
        if (done == 0) return energy.getEnergyStored() < cost ? STATUS_NO_ENERGY : STATUS_FULL;
        converted += done;
        return STATUS_WORKING;
    }

    /** What one ore gives when mined with a pickaxe with the module's Fortune, smelted with autosmelt. */
    private List<ItemStack> convert(ServerLevel level, ItemStack ore) {
        List<ItemStack> out = new ArrayList<>();
        List<ItemStack> drops;
        if (ore.getItem() instanceof BlockItem bi) {
            drops = Block.getDrops(bi.getBlock().defaultBlockState(), level, worldPosition, null, null, tool());
        } else {
            drops = List.of(ore.copyWithCount(1));
        }
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) out.add(smelt(level, drop));
        }
        return out;
    }

    private ItemStack tool() {
        ItemStack pick = new ItemStack(Items.NETHERITE_PICKAXE);
        int fortune = fortuneLevel();
        if (fortune > 0) pick.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        return pick;
    }

    /** With autosmelt, ores and raw metals come out as ingots, gems and the like. */
    private ItemStack smelt(ServerLevel level, ItemStack stack) {
        if (!has(QuarryUpgradeItem.Kind.SMELTING)) return stack;
        if (!stack.is(Tags.Items.RAW_MATERIALS) && !stack.is(Tags.Items.ORES)) return stack;
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level)
                .map(recipe -> {
                    ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
                    result.setCount(result.getCount() * stack.getCount());
                    return result.isEmpty() ? stack : result;
                })
                .orElse(stack);
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

    /** Hands the results to containers touching the sides or the bottom (above is where ores come from). */
    private void pushOutputs(ServerLevel level) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbour == null || neighbour instanceof QuarryBlockEntity) continue;
            neighbour.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).ifPresent(target -> {
                for (int i = 0; i < output.getSlots(); i++) {
                    ItemStack stack = output.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
                    if (rest.getCount() != stack.getCount()) output.setStackInSlot(i, rest);
                }
            });
        }
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
        tag.put("Input", input.serializeNBT());
        tag.put("Output", output.serializeNBT());
        tag.put("Upgrades", upgrades.serializeNBT());
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
        tag.putLong("Converted", converted);
        tag.putInt("Status", status);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Input")) input.deserializeNBT(sized(tag.getCompound("Input"), STORAGE));
        if (tag.contains("Output")) output.deserializeNBT(sized(tag.getCompound("Output"), STORAGE));
        if (tag.contains("Upgrades")) upgrades.deserializeNBT(sized(tag.getCompound("Upgrades"), UPGRADES));
        if (tag.contains("Energy")) energy.set(tag.getInt("Energy"));
        progress = tag.getInt("Progress");
        converted = tag.getLong("Converted");
        status = tag.getInt("Status");
    }

    private static CompoundTag sized(CompoundTag tag, int size) {
        tag.putInt("Size", size);
        return tag;
    }

    /** The client only needs whether the drill turns. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Status", status);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition);
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
