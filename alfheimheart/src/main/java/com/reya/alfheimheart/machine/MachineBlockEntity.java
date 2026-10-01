package com.reya.alfheimheart.machine;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;

/**
 * A mana machine: nine input slots, nine output slots and a few special ones (a reagent, a catalyst...), a
 * store of mana filled by mana spreaders (it is a mana receiver, like a pool) and from the mana pools on its six
 * sides, a redstone setting.
 * <p>
 * It works one craft at a time: when its inputs make a recipe ({@link #findJob}) and the outputs would fit, it
 * charges the craft with mana from its store, a little each tick ({@link #chargeRate}), for at least the craft's
 * shortest time; then it checks the recipe once more, takes the inputs and puts out the result. If the inputs
 * change under it, the craft stops and its mana goes back into the store.
 * <p>
 * The client copy knows the items and the craft's progress (the renderers show them) and hears each finished
 * craft as a block event.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider, ManaReceiver {
    public static final int INPUTS = 9, OUTPUTS = 9;
    public static final int INPUT_START = 0, OUTPUT_START = INPUTS, SPECIAL_START = INPUTS + OUTPUTS;
    /** The menu's numbers shared by every machine (see {@link #data}); a machine's own follow them. */
    public static final int BASE_DATA = 14;
    /** Block event: a craft finished (the parameter is the raw id of its first output). */
    public static final int EVENT_CRAFTED = 1;
    public static final String TAG_MANA = "Mana";

    private final int specialSlots;
    protected final ItemStackHandler items;
    protected int mana;
    protected MachineStatus status = MachineStatus.IDLE;
    protected RedstoneMode redstone = RedstoneMode.IGNORE;
    /** The craft being charged: its recipe, its mana, the mana in it so far, the ticks it has taken. */
    @Nullable
    protected ResourceLocation jobId;
    protected int jobCost, jobCharged, jobTicks, jobMinTicks;
    /** Crafts made (wraps round), and the raw id (+1) of the last one's first output. */
    protected int crafts, lastOutput;
    private boolean hasPool, clientDirty;
    private int lastSignal = -1, syncCooldown;

    // client side
    private long clientAge;
    private float activity, prevActivity;
    private int clientMana, clientCapacity = 1, clientCost, clientCharged, clientTicks, clientMinTicks;
    private float progress, prevProgress;
    private boolean clientWorking;
    private long craftedAt = -1000L;
    private ItemStack crafted = ItemStack.EMPTY;
    private final NonNullList<ItemStack> clientItems;
    private float phase, prevPhase;
    @Nullable
    private Job clientJob;
    private boolean clientJobKnown;

    /** Hoppers and pipes put into the input and special slots what the machine takes, and take from the outputs. */
    private final LazyOptional<IItemHandler> itemCap;
    private final LazyOptional<ManaReceiver> manaCap = LazyOptional.of(() -> this);

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int specialSlots) {
        super(type, pos, state);
        this.specialSlots = specialSlots;
        this.clientItems = NonNullList.withSize(SPECIAL_START + specialSlots, ItemStack.EMPTY);
        this.items = new ItemStackHandler(SPECIAL_START + specialSlots) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                if (slot < OUTPUT_START) return acceptsInput(stack);
                if (slot < SPECIAL_START) return false;
                return acceptsSpecial(slot - SPECIAL_START, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return slot >= SPECIAL_START ? specialLimit(slot - SPECIAL_START) : 64;
            }

            @Override
            protected void onContentsChanged(int slot) {
                if (level != null && level.isClientSide) return;     // the client's copy, filled from the server's
                MachineBlockEntity.this.setChanged();
                clientDirty = true;
            }
        };
        this.itemCap = LazyOptional.of(() -> new IItemHandler() {
            @Override
            public int getSlots() {
                return items.getSlots();
            }

            @Override
            public @Nonnull ItemStack getStackInSlot(int slot) {
                return items.getStackInSlot(slot);
            }

            @Override
            public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                return slot >= OUTPUT_START && slot < SPECIAL_START ? stack : items.insertItem(slot, stack, simulate);
            }

            @Override
            public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot >= OUTPUT_START && slot < SPECIAL_START ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return items.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return items.isItemValid(slot, stack);
            }
        });
    }

    // ------------------------------------------------------------------ what each machine says

    /** The most mana it holds. */
    public abstract int capacity();

    /** The most mana a tick puts into a craft. */
    protected abstract int chargeRate();

    /** Whether it takes mana from the pools beside it, and how much a tick. */
    protected abstract int drawPerTick();

    /** Whether an item may go into the input slots (some recipe of the machine's takes it). */
    protected abstract boolean acceptsInput(ItemStack stack);

    /** Whether an item may go into special slot `index` (the reagent, the catalyst...). */
    protected boolean acceptsSpecial(int index, ItemStack stack) {
        return false;
    }

    protected int specialLimit(int index) {
        return 64;
    }

    /** The craft the items allow now, or null. */
    @Nullable
    protected abstract Job findJob(Level level);

    /** Sounds and such when a craft finishes (server side). */
    protected void onCrafted(Level level, BlockPos pos, Job job) {
    }

    /** A craft started (server side): a sound, say. */
    protected void onJobStarted(Level level, BlockPos pos, Job job) {
    }

    /**
     * A craft: its recipe, how many items to take from each slot (every slot, special ones too), what comes out,
     * its mana, its shortest time in ticks, and the single items that make it (the screens show them).
     */
    public record Job(ResourceLocation id, int[] taken, List<ItemStack> outputs, int mana, int minTicks, List<ItemStack> units) {
        public Job(ResourceLocation id, int[] taken, List<ItemStack> outputs, int mana, int minTicks) {
            this(id, taken, outputs, mana, minTicks, List.of());
        }
    }

    /** An array for Job.taken, one entry for every slot. */
    protected int[] takenArray() {
        return new int[SPECIAL_START + specialSlots];
    }

    public ItemStackHandler items() {
        return items;
    }

    public int specialSlots() {
        return specialSlots;
    }

    // ------------------------------------------------------------------ server

    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        be.tickServer(level, pos, state);
    }

    protected void tickServer(Level level, BlockPos pos, BlockState state) {
        int capacity = capacity();
        if (mana > capacity) mana = capacity;
        long time = level.getGameTime();
        if (time % 20L == 0L) hasPool = lookForPool(level, pos);
        if (mana < capacity && drawPerTick() > 0) drawFromPools(level, pos);
        if (redstone.allows(level.hasNeighborSignal(pos))) work(level, pos, state, time);
        else status = MachineStatus.REDSTONE;

        boolean active = status == MachineStatus.WORKING;
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(MachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
        int signal = comparatorSignal();
        if (signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        if (syncCooldown > 0) syncCooldown--;
        if (syncCooldown <= 0 && (clientDirty || jobId != null && time % 10L == 0L || time % 40L == 0L)) {
            clientDirty = false;
            syncCooldown = 4;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void work(Level level, BlockPos pos, BlockState state, long time) {
        if (jobId == null) {
            if (time % 5L != 0L) return;                 // look for work four times a second
            Job job = findJob(level);
            if (job == null) {
                status = MachineStatus.IDLE;
                return;
            }
            if (!Units.insertAll(items, OUTPUT_START, SPECIAL_START, job.outputs(), true)) {
                status = MachineStatus.OUTPUT_FULL;
                return;
            }
            jobId = job.id();
            jobCost = Math.max(0, job.mana());
            jobCharged = 0;
            jobTicks = 0;
            jobMinTicks = Math.max(1, job.minTicks());
            clientDirty = true;
            onJobStarted(level, pos, job);
        }
        // now and then: are the inputs still there?
        if (jobTicks % 10 == 9) {
            if (!same(findJob(level))) {
                cancel();
                return;
            }
        }
        int take = Math.min(Math.min(chargeRate(), mana), jobCost - jobCharged);
        if (take > 0) {
            mana -= take;
            jobCharged += take;
            setChanged();
        }
        if (jobCharged < jobCost && take <= 0) {
            status = MachineStatus.NO_MANA;
            return;
        }
        status = MachineStatus.WORKING;
        jobTicks++;
        if (jobCharged >= jobCost && jobTicks >= jobMinTicks) finish(level, pos, state);
    }

    private void finish(Level level, BlockPos pos, BlockState state) {
        Job job = findJob(level);
        if (!same(job)) {
            cancel();
            return;
        }
        if (!Units.insertAll(items, OUTPUT_START, SPECIAL_START, job.outputs(), true)) {
            status = MachineStatus.OUTPUT_FULL;           // keeps its mana, and waits
            return;
        }
        int[] taken = job.taken();
        for (int i = 0; i < taken.length && i < items.getSlots(); i++) {
            if (taken[i] > 0) items.extractItem(i, taken[i], false);
        }
        Units.insertAll(items, OUTPUT_START, SPECIAL_START, job.outputs(), false);
        crafts = crafts + 1 & 0x7FFF;
        ItemStack first = job.outputs().isEmpty() ? ItemStack.EMPTY : job.outputs().get(0);
        lastOutput = first.isEmpty() ? 0 : Item.getId(first.getItem()) + 1;
        level.blockEvent(pos, state.getBlock(), EVENT_CRAFTED, Math.max(0, lastOutput - 1));
        onCrafted(level, pos, job);
        jobId = null;
        jobCost = jobCharged = jobTicks = 0;
        clientDirty = true;
        setChanged();
    }

    /** Whether the items still make the craft being charged (the same recipe, for the same mana: as many items). */
    private boolean same(@Nullable Job job) {
        return job != null && job.id().equals(jobId) && Math.max(0, job.mana()) == jobCost;
    }

    /** The craft stops (its inputs went): its mana goes back into the store. */
    private void cancel() {
        mana = Math.min(capacity(), mana + jobCharged);
        jobId = null;
        jobCost = jobCharged = jobTicks = 0;
        status = MachineStatus.IDLE;
        clientDirty = true;
        setChanged();
    }

    private void drawFromPools(Level level, BlockPos pos) {
        int budget = Math.min(drawPerTick(), capacity() - mana);
        for (Direction dir : Direction.values()) {
            if (budget <= 0) return;
            ManaPool pool = poolAt(level, pos.relative(dir), dir.getOpposite());
            if (pool == null) continue;
            int take = Math.min(budget, pool.getCurrentMana());
            if (take <= 0) continue;
            pool.receiveMana(-take);
            mana += take;
            budget -= take;
            setChanged();
        }
    }

    private static boolean lookForPool(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (poolAt(level, pos.relative(dir), dir.getOpposite()) != null) return true;
        }
        return false;
    }

    @Nullable
    private static ManaPool poolAt(Level level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be instanceof MachineBlockEntity) return null;
        ManaReceiver receiver = be.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, side).resolve().orElse(null);
        return receiver instanceof ManaPool pool ? pool : null;
    }

    public int comparatorSignal() {
        int capacity = capacity();
        if (mana <= 0 || capacity <= 0) return 0;
        return 1 + (int) (14L * Math.min(mana, capacity) / capacity);
    }

    public void cycleRedstone() {
        redstone = redstone.next();
        setChanged();
    }

    // ------------------------------------------------------------------ the menu's numbers

    /** Numbers of the machine's own, after the shared ones. */
    protected int extraData(int index) {
        return 0;
    }

    public int extraDataCount() {
        return 0;
    }

    public final int dataCount() {
        return BASE_DATA + extraDataCount();
    }

    private int flags() {
        int f = status.ordinal() | redstone.ordinal() << 3;
        if (hasPool) f |= 1 << 5;
        if (jobId != null) f |= 1 << 6;
        return f;
    }

    /** Each number is sent as 16 bits: big ones in two halves. */
    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int capacity = capacity();
            return switch (index) {
                case 0 -> mana & 0xFFFF;
                case 1 -> mana >>> 16 & 0xFFFF;
                case 2 -> capacity & 0xFFFF;
                case 3 -> capacity >>> 16 & 0xFFFF;
                case 4 -> jobCost & 0xFFFF;
                case 5 -> jobCost >>> 16 & 0xFFFF;
                case 6 -> jobCharged & 0xFFFF;
                case 7 -> jobCharged >>> 16 & 0xFFFF;
                case 8 -> flags();
                case 9 -> crafts;
                case 10 -> lastOutput & 0xFFFF;
                case 11 -> lastOutput >>> 16 & 0xFFFF;
                case 12 -> jobId != null ? Math.min(jobTicks, 0x7FFF) : 0;
                case 13 -> jobId != null ? Math.min(jobMinTicks, 0x7FFF) : 0;
                default -> extraData(index - BASE_DATA) & 0xFFFF;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return dataCount();
        }
    };

    public ContainerData data() {
        return data;
    }

    // ------------------------------------------------------------------ mana receiver

    @Override
    public Level getManaReceiverLevel() {
        return level;
    }

    @Override
    public BlockPos getManaReceiverPos() {
        return worldPosition;
    }

    @Override
    public int getCurrentMana() {
        return mana;
    }

    @Override
    public boolean isFull() {
        return mana >= capacity();
    }

    @Override
    public void receiveMana(int amount) {
        int before = mana;
        mana = Mth.clamp(mana + amount, 0, capacity());
        if (mana != before) setChanged();
    }

    @Override
    public boolean canReceiveManaFromBursts() {
        return true;
    }

    // ------------------------------------------------------------------ client

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_CRAFTED) {
            if (level != null && level.isClientSide) {
                craftedAt = clientAge;
                crafted = new ItemStack(Item.byId(param));
                onClientCrafted();
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Particles and such when a craft finishes (client side). */
    protected void onClientCrafted() {
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        be.clientAge++;
        be.prevActivity = be.activity;
        be.activity = be.clientWorking ? Math.min(1.0F, be.activity + 0.07F) : Math.max(0.0F, be.activity - 0.05F);
        be.prevProgress = be.progress;
        // between the server's updates the craft's time runs on here
        if (be.clientWorking && be.clientTicks < be.clientMinTicks) be.clientTicks++;
        float target = be.craftProgress();
        be.progress = target < be.progress - 0.2F ? target : be.progress + (target - be.progress) * 0.25F;
        be.prevPhase = be.phase;
        be.phase += 1.0F + be.activity * (2.5F + 5.0F * be.progress);
        be.tickClient(level, pos, state);
    }

    protected void tickClient(Level level, BlockPos pos, BlockState state) {
    }

    public long clientAge() {
        return clientAge;
    }

    /** How far a craft is (client side): as far as its mana and its time allow, whichever is behind. */
    private float craftProgress() {
        if (clientMinTicks <= 0 && clientCost <= 0) return 0.0F;
        float time = clientMinTicks > 0 ? Math.min(1.0F, clientTicks / (float) clientMinTicks) : 1.0F;
        float mana = clientCost > 0 ? Math.min(1.0F, clientCharged / (float) clientCost) : 1.0F;
        return Math.min(time, mana);
    }

    /** 0 (idle) to 1 (working), eased. */
    public float activity(float partialTick) {
        return Mth.lerp(partialTick, prevActivity, activity);
    }

    /** How far the craft is, 0 to 1, eased. */
    public float progress(float partialTick) {
        return Mth.lerp(partialTick, prevProgress, progress);
    }

    /** Turns as the machine works: slowly at rest, faster and faster as a craft charges (the renderers' orbits). */
    public float phase(float partialTick) {
        return Mth.lerp(partialTick, prevPhase, phase);
    }

    /** The craft the client's copy of the items makes (the renderers show its items and what it makes), or null. */
    @Nullable
    public Job clientJob() {
        if (!clientJobKnown && level != null) {
            clientJobKnown = true;
            clientJob = findJob(level);
        }
        return clientJob;
    }

    public boolean clientWorking() {
        return clientWorking;
    }

    /** Ticks since the last craft finished (large when long ago). */
    public float sinceCrafted(float partialTick) {
        return clientAge - craftedAt + partialTick;
    }

    public ItemStack crafted() {
        return crafted;
    }

    public NonNullList<ItemStack> clientItems() {
        return clientItems;
    }

    public int clientMana() {
        return clientMana;
    }

    public int clientCapacity() {
        return Math.max(1, clientCapacity);
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        if (mana > 0) tag.putInt(TAG_MANA, mana);
        tag.putByte("Redstone", (byte) redstone.ordinal());
        if (jobId != null) {
            tag.putString("Job", jobId.toString());
            tag.putInt("JobCost", jobCost);
            tag.putInt("JobCharged", jobCharged);
            tag.putInt("JobTicks", jobTicks);
            tag.putInt("JobMinTicks", jobMinTicks);
        }
        tag.putInt("Crafts", crafts);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items", Tag.TAG_COMPOUND)) {
            CompoundTag saved = tag.getCompound("Items");
            saved.putInt("Size", items.getSlots());
            items.deserializeNBT(saved);
        }
        mana = Math.max(0, tag.getInt(TAG_MANA));
        redstone = RedstoneMode.byId(tag.getByte("Redstone"));
        jobId = tag.contains("Job", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("Job")) : null;
        jobCost = tag.getInt("JobCost");
        jobCharged = tag.getInt("JobCharged");
        jobTicks = tag.getInt("JobTicks");
        jobMinTicks = tag.getInt("JobMinTicks");
        crafts = tag.getInt("Crafts") & 0x7FFF;
    }

    /** What the client needs: the items (the renderer shows them), the mana, the craft's progress. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        NonNullList<ItemStack> list = NonNullList.withSize(items.getSlots(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) list.set(i, items.getStackInSlot(i));
        ContainerHelper.saveAllItems(tag, list, true);
        tag.putInt(TAG_MANA, mana);
        tag.putInt("Capacity", capacity());
        tag.putInt("JobCost", jobId != null ? jobCost : 0);
        tag.putInt("JobCharged", jobId != null ? jobCharged : 0);
        tag.putInt("JobTicks", jobId != null ? jobTicks : 0);
        tag.putInt("JobMinTicks", jobId != null ? jobMinTicks : 0);
        tag.putBoolean("Working", status == MachineStatus.WORKING);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readClient(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) readClient(tag);
    }

    private void readClient(CompoundTag tag) {
        for (int i = 0; i < clientItems.size(); i++) clientItems.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, clientItems);
        // the client's own handler mirrors them, so the machine's recipe search works on the client too
        for (int i = 0; i < clientItems.size(); i++) items.setStackInSlot(i, clientItems.get(i).copy());
        clientJobKnown = false;
        clientMana = tag.getInt(TAG_MANA);
        clientCapacity = Math.max(1, tag.getInt("Capacity"));
        clientCost = tag.getInt("JobCost");
        clientCharged = tag.getInt("JobCharged");
        clientTicks = tag.getInt("JobTicks");
        clientMinTicks = tag.getInt("JobMinTicks");
        clientWorking = tag.getBoolean("Working");
    }

    /** The renderers draw a little round the block (floating items, glows). */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0D);
    }

    @Override
    public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        if (cap == BotaniaForgeCapabilities.MANA_RECEIVER) return manaCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
        manaCap.invalidate();
    }
}
