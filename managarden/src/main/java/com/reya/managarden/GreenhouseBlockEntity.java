package com.reya.managarden;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.block.WandBindable;
import vazkii.botania.api.mana.ManaCollector;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;

/**
 * The greenhouse: every cycle its flowers add their mana (rate × cycle, raised by the abundance
 * upgrades and by harmony, the number of different flowers), sometimes more on a lucky cycle. It
 * stops when the store is full, has no flowers or its redstone setting says so. Mana goes on to the
 * mana receiver bound with the Wand of the Forest and to the ones next to it (pools, spreaders,
 * altars...), and into the mana item in the charge slot.
 * <p>
 * The client copy knows the flowers (drawn inside), the mana (for the Wand of the Forest's HUD) and
 * how many players look inside (the bud at the top blooms open for them).
 */
public class GreenhouseBlockEntity extends BlockEntity implements MenuProvider, WandBindable {
    public static final int FLOWERS = 8, UPGRADES = 4;
    public static final int FLOWER_START = 0, UPGRADE_START = FLOWERS, CHARGE_SLOT = FLOWERS + UPGRADES;
    public static final int SLOTS = CHARGE_SLOT + 1;
    public static final String TAG_MANA = "Mana";
    /** Block events: how many players look inside; a cycle finished (param 1 if it was lucky). */
    public static final int EVENT_OPENERS = 1, EVENT_CYCLE = 2;
    /** What the menu's data holds (see {@link #data}). */
    public static final int DATA_COUNT = 18;
    /** Mana a tick for receivers that don't tell how much they still take (altars, plates...). */
    private static final int SMALL_GIFT = 250;

    /** Where the flowers stand inside: a ring round the middle (blocks), clockwise from the north like the GUI's. */
    public static final double FLOWER_RING = 0.285D;

    public static double flowerAngle(int i) {
        return i * Math.PI / 4.0D - Math.PI / 2.0D;
    }

    public enum Status { RUNNING, NO_FLOWERS, FULL, REDSTONE }

    public enum RedstoneMode {
        /** Works whatever the redstone. */
        IGNORE,
        /** Works only while powered. */
        HIGH,
        /** Works only while not powered. */
        LOW;

        public boolean allows(boolean powered) {
            return this == IGNORE || (this == HIGH) == powered;
        }

        public RedstoneMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static RedstoneMode byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : IGNORE;
        }
    }

    private int mana;
    private int progress;
    private Status status = Status.NO_FLOWERS;
    private RedstoneMode redstone = RedstoneMode.IGNORE;
    private boolean output = true;
    @Nullable
    private BlockPos binding;
    private int cycles, luckyCycles, lastGain;
    /** Ticks it still shows as lit after it stops, so the light doesn't flicker. */
    private int litTicks;
    private boolean hasTarget;
    private int lastSignal = -1;
    private int syncedMana = -1;

    private boolean dirty = true;
    private int cycleTicks = 100, manaPerCycle, capacity = 1, luckPermille, kinds;
    private int speedUps, capacityUps, luckUps, yieldUps;

    // client side
    private int openers;
    private float openness, prevOpenness;
    private int clientMana, clientCapacity = 1;
    private final ItemStack[] clientFlowers = new ItemStack[FLOWERS];
    private long clientAge;
    /** Client ticks at which the last cycle ended, and the last lucky one (for the renderer's flashes). */
    private long lastCycleAge = -1000L, lastLuckyAge = -1000L;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot < UPGRADE_START) return Flowers.isFlower(stack);
            if (slot < CHARGE_SLOT) return stack.getItem() instanceof UpgradeItem;
            return isManaItem(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= UPGRADE_START && slot < CHARGE_SLOT ? Config.MAX_UPGRADES.get() : 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            GreenhouseBlockEntity.this.setChanged();
            if (slot < CHARGE_SLOT) dirty = true;
            if (slot < UPGRADE_START) syncToClients();
        }
    };

    /** Hoppers and pipes may put flowers, upgrades and mana items in, and take charged items out. */
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public @Nonnull ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != CHARGE_SLOT) return ItemStack.EMPTY;
            ItemStack stack = items.getStackInSlot(slot);
            ManaItem mana = manaItem(stack);
            // only full items leave by themselves
            if (mana != null && mana.getMana() < mana.getMaxMana()) return ItemStack.EMPTY;
            return items.extractItem(slot, amount, simulate);
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

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos, SoundEvents.BIG_DRIPLEAF_TILT_UP, SoundSource.BLOCKS, 0.6F, 1.25F);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.4F);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, SoundSource.BLOCKS, 0.6F, 1.25F);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int now) {
            level.blockEvent(pos, state.getBlock(), EVENT_OPENERS, now);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof GreenhouseMenu menu && menu.greenhouse() == GreenhouseBlockEntity.this;
        }
    };

    /** The menu's numbers, each 16 bits (the game sends them as shorts): big ones in two halves. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> mana & 0xFFFF;
                case 1 -> mana >>> 16 & 0xFFFF;
                case 2 -> capacity & 0xFFFF;
                case 3 -> capacity >>> 16 & 0xFFFF;
                case 4 -> progress;
                case 5 -> cycleTicks;
                case 6 -> manaPerCycle & 0xFFFF;
                case 7 -> manaPerCycle >>> 16 & 0xFFFF;
                case 8 -> luckPermille;
                case 9 -> flags();
                case 10 -> cycles;
                case 11 -> luckyCycles;
                case 12 -> lastGain & 0xFFFF;
                case 13 -> lastGain >>> 16 & 0xFFFF;
                case 14 -> kinds;
                case 15 -> Math.min(255, speedUps) | Math.min(255, capacityUps) << 8;
                case 16 -> (int) Math.min(30000L, Math.round(Config.LUCK_BONUS.get() * 100.0D));
                case 17 -> Math.min(255, luckUps) | Math.min(255, yieldUps) << 8;
                default -> 0;
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

    public GreenhouseBlockEntity(BlockPos pos, BlockState state) {
        super(ManaGarden.GREENHOUSE_BE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    // ------------------------------------------------------------------ server

    public static void serverTick(Level level, BlockPos pos, BlockState state, GreenhouseBlockEntity be) {
        // now and then too, so a changed config takes hold without touching the greenhouse
        if (be.dirty || level.getGameTime() % 100L == 0L) be.recompute();
        boolean powered = level.hasNeighborSignal(pos);
        if (be.manaPerCycle <= 0) be.status = Status.NO_FLOWERS;
        else if (!be.redstone.allows(powered)) be.status = Status.REDSTONE;
        else if (be.mana >= be.capacity) be.status = Status.FULL;
        else be.status = Status.RUNNING;

        if (be.status == Status.RUNNING) {
            be.litTicks = 40;
            if (++be.progress >= be.cycleTicks) {
                be.progress = 0;
                be.finishCycle(level, pos, state);
            }
        } else {
            if (be.litTicks > 0) be.litTicks--;
            if (be.status == Status.NO_FLOWERS) be.progress = 0;
        }

        if (be.mana > 0) {
            be.charge();
            if (be.output) be.pushMana(level, pos);
        }
        if (level.getGameTime() % 20L == 0L) be.hasTarget = be.findsTarget(level, pos);

        boolean lit = be.litTicks > 0;
        if (state.getValue(GreenhouseBlock.ACTIVE) != lit) {
            level.setBlock(pos, state.setValue(GreenhouseBlock.ACTIVE, lit), Block.UPDATE_CLIENTS);
        }
        int signal = be.comparatorSignal();
        if (signal != be.lastSignal) {
            be.lastSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        if (level.getGameTime() % 20L == 0L && be.mana != be.syncedMana) be.syncToClients();
    }

    /** Works out the cycle, the mana per cycle, the store and the luck from the flowers and upgrades. */
    private void recompute() {
        dirty = false;
        int max = Config.MAX_UPGRADES.get();
        speedUps = Math.min(max, count(UpgradeKind.SPEED));
        capacityUps = Math.min(max, count(UpgradeKind.CAPACITY));
        luckUps = Math.min(max, count(UpgradeKind.LUCK));
        yieldUps = Math.min(max, count(UpgradeKind.YIELD));

        long perSecond = 0L;
        Set<ResourceLocation> seen = new HashSet<>();
        for (int i = FLOWER_START; i < UPGRADE_START; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!Flowers.isFlower(stack)) continue;
            perSecond += Flowers.rate(stack);
            seen.add(Flowers.kind(stack));
        }
        kinds = seen.size();
        int base = Config.CYCLE_TICKS.get();
        cycleTicks = Math.max(1, (int) Math.round(base / (1.0D + Config.SPEED_PER_UPGRADE.get() * speedUps)));
        double multiplier = (1.0D + Config.YIELD_PER_UPGRADE.get() * yieldUps)
                * (1.0D + Config.HARMONY_PER_KIND.get() * Math.max(0, kinds - 1));
        manaPerCycle = (int) Math.min(Integer.MAX_VALUE / 4, Math.round(perSecond * base / 20.0D * multiplier));
        capacity = (int) Math.min(Integer.MAX_VALUE - 1L, Config.BASE_CAPACITY.get() + (long) Config.CAPACITY_PER_UPGRADE.get() * capacityUps);
        luckPermille = (int) Math.round(Math.min(1.0D, Config.LUCK_PER_UPGRADE.get() * luckUps) * 1000.0D);
        if (progress >= cycleTicks) progress = cycleTicks - 1;
    }

    private int count(UpgradeKind kind) {
        int n = 0;
        for (int i = UPGRADE_START; i < CHARGE_SLOT; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (stack.getItem() instanceof UpgradeItem u && u.kind == kind) n += stack.getCount();
        }
        return n;
    }

    private void finishCycle(Level level, BlockPos pos, BlockState state) {
        int gain = manaPerCycle;
        boolean lucky = luckPermille > 0 && level.random.nextInt(1000) < luckPermille;
        if (lucky) gain = (int) Math.min(Integer.MAX_VALUE / 4, gain + Math.round(gain * Config.LUCK_BONUS.get()));
        mana = (int) Math.min(capacity, (long) mana + gain);
        lastGain = gain;
        cycles = cycles + 1 & 0x7FFF;
        if (lucky) luckyCycles = luckyCycles + 1 & 0x7FFF;
        level.blockEvent(pos, state.getBlock(), EVENT_CYCLE, lucky ? 1 : 0);
        setChanged();
    }

    /** Pours mana into the item in the charge slot, as fast as a mana pool does. */
    private void charge() {
        ItemStack stack = items.getStackInSlot(CHARGE_SLOT);
        ManaItem item = manaItem(stack);
        if (item == null || !item.canReceiveManaFromPool(this)) return;
        int give = Math.min(Math.min(mana, Config.CHARGE_RATE.get()), item.getMaxMana() - item.getMana());
        if (give <= 0) return;
        item.addMana(give);
        mana -= give;
        setChanged();
    }

    /** Hands mana to the bound receiver first, then to the receivers on its six sides. */
    private void pushMana(Level level, BlockPos pos) {
        int budget = Math.min(mana, Config.TRANSFER_RATE.get());
        BlockPos bound = getBinding();
        if (bound != null) budget -= give(level, bound, null, budget);
        for (Direction dir : Direction.values()) {
            if (budget <= 0) return;
            BlockPos next = pos.relative(dir);
            if (next.equals(bound)) continue;
            budget -= give(level, next, dir.getOpposite(), budget);
        }
    }

    private int give(Level level, BlockPos target, @Nullable Direction side, int budget) {
        ManaReceiver receiver = receiverAt(level, target, side);
        if (budget <= 0 || receiver == null || receiver.isFull()) return 0;
        int room;
        if (receiver instanceof ManaPool pool) room = pool.getMaxMana() - pool.getCurrentMana();
        else if (receiver instanceof ManaCollector collector) room = collector.getMaxMana() - collector.getCurrentMana();
        else room = SMALL_GIFT;
        int give = Math.min(budget, room);
        if (give <= 0) return 0;
        receiver.receiveMana(give);
        mana -= give;
        setChanged();
        return give;
    }

    @Nullable
    private static ManaReceiver receiverAt(Level level, BlockPos pos, @Nullable Direction side) {
        if (!level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be instanceof GreenhouseBlockEntity) return null;
        return be.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, side).resolve().orElse(null);
    }

    private boolean findsTarget(Level level, BlockPos pos) {
        if (getBinding() != null && receiverAt(level, binding, null) != null) return true;
        for (Direction dir : Direction.values()) {
            if (receiverAt(level, pos.relative(dir), dir.getOpposite()) != null) return true;
        }
        return false;
    }

    public int comparatorSignal() {
        if (mana <= 0 || capacity <= 0) return 0;
        return 1 + (int) (14L * Math.min(mana, capacity) / capacity);
    }

    private int flags() {
        int f = status == Status.RUNNING ? 1 : 0;
        f |= status.ordinal() << 1;
        f |= redstone.ordinal() << 3;
        if (output) f |= 1 << 5;
        if (getBinding() != null) f |= 1 << 6;
        if (hasTarget) f |= 1 << 7;
        return f;
    }

    // ------------------------------------------------------------------ menu buttons

    public void cycleRedstone() {
        redstone = redstone.next();
        setChanged();
    }

    public void toggleOutput() {
        output = !output;
        setChanged();
    }

    public void unbind() {
        binding = null;
        setChanged();
        syncToClients();
    }

    // ------------------------------------------------------------------ opening the bud

    public void startOpen(Player player) {
        if (!isRemoved() && !player.isSpectator() && level != null) {
            openersCounter.incrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    public void stopOpen(Player player) {
        if (!isRemoved() && !player.isSpectator() && level != null) {
            openersCounter.decrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    public void recheckOpeners() {
        if (!isRemoved() && level != null) openersCounter.recheckOpeners(level, worldPosition, getBlockState());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_OPENERS) {
            openers = param;
            return true;
        }
        if (id == EVENT_CYCLE) {
            if (level != null && level.isClientSide) cycleEffects(param == 1);
            return true;
        }
        return super.triggerEvent(id, param);
    }

    // ------------------------------------------------------------------ client

    public static void clientTick(Level level, BlockPos pos, BlockState state, GreenhouseBlockEntity be) {
        be.clientAge++;
        be.prevOpenness = be.openness;
        be.openness = be.openers > 0 ? Math.min(1.0F, be.openness + 0.07F) : Math.max(0.0F, be.openness - 0.055F);
    }

    /** A cycle's mana rises from the flowers to the core; a lucky one showers golden sparks. */
    private void cycleEffects(boolean lucky) {
        lastCycleAge = clientAge;
        if (level == null) return;
        RandomSource random = level.random;
        double cx = worldPosition.getX() + 0.5D, cy = worldPosition.getY(), cz = worldPosition.getZ() + 0.5D;
        for (int i = 0; i < FLOWERS; i++) {
            if (clientFlower(i).isEmpty()) continue;
            double a = flowerAngle(i);
            int color = Flowers.color(clientFlower(i));
            BotaniaAPI.instance().sparkleFX(level, cx + Math.cos(a) * FLOWER_RING, cy + 0.45D, cz + Math.sin(a) * FLOWER_RING,
                    (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F, 5);
        }
        BotaniaAPI.instance().sparkleFX(level, cx, cy + 0.75D, cz, 0.4F, 0.9F, 1.0F, 2.0F, 6);
        if (lucky) {
            lastLuckyAge = clientAge;
            for (int i = 0; i < 14; i++) {
                double a = random.nextDouble() * Math.PI * 2.0D, r = 0.2D + random.nextDouble() * 0.45D;
                BotaniaAPI.instance().sparkleFX(level, cx + Math.cos(a) * r, cy + 0.6D + random.nextDouble() * 0.6D, cz + Math.sin(a) * r,
                        1.0F, 0.85F, 0.3F, 1.2F + random.nextFloat(), 8);
            }
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, cx, cy + 1.1D, cz, 0.0D, 0.0D, 0.0D);
            level.playLocalSound(cx, cy + 0.5D, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F,
                    1.6F + random.nextFloat() * 0.3F, false);
        }
    }

    public float openness(float partialTick) {
        return Mth.lerp(partialTick, prevOpenness, openness);
    }

    public long clientAge() {
        return clientAge;
    }

    public long lastCycleAge() {
        return lastCycleAge;
    }

    public long lastLuckyAge() {
        return lastLuckyAge;
    }

    public ItemStack clientFlower(int i) {
        ItemStack stack = clientFlowers[i];
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public int clientMana() {
        return clientMana;
    }

    public int clientCapacity() {
        return Math.max(1, clientCapacity);
    }

    public int randomFlowerColor(RandomSource random) {
        int start = random.nextInt(FLOWERS);
        for (int k = 0; k < FLOWERS; k++) {
            ItemStack stack = clientFlower((start + k) % FLOWERS);
            if (!stack.isEmpty()) return Flowers.color(stack);
        }
        return 0x4CD7FF;
    }

    // ------------------------------------------------------------------ Wand of the Forest

    @Override
    public boolean canSelect(Player player, ItemStack wand, BlockPos pos, Direction side) {
        return true;
    }

    @Override
    public boolean bindTo(Player player, ItemStack wand, BlockPos pos, Direction side) {
        if (level == null || pos.equals(worldPosition) || !inRange(pos)) return false;
        if (!level.isClientSide && receiverAt(level, pos, null) == null) return false;
        binding = pos.immutable();
        setChanged();
        syncToClients();
        return true;
    }

    @Nullable
    @Override
    public BlockPos getBinding() {
        return binding != null && level != null && level.isLoaded(binding) && inRange(binding) ? binding : null;
    }

    private boolean inRange(BlockPos pos) {
        int range = Config.BIND_RANGE.get();
        return pos.distSqr(worldPosition) <= (double) range * range;
    }

    // ------------------------------------------------------------------ helpers

    public static boolean isManaItem(ItemStack stack) {
        return manaItem(stack) != null;
    }

    @Nullable
    private static ManaItem manaItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return stack.getCapability(BotaniaForgeCapabilities.MANA_ITEM).resolve().orElse(null);
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            syncedMana = mana;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.managarden.mana_greenhouse");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new GreenhouseMenu(id, inventory, this, data);
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        if (mana > 0) tag.putInt(TAG_MANA, mana);
        tag.putInt("Progress", progress);
        tag.putByte("Redstone", (byte) redstone.ordinal());
        tag.putBoolean("Output", output);
        if (binding != null) tag.put("Binding", NbtUtils.writeBlockPos(binding));
        tag.putInt("Cycles", cycles);
        tag.putInt("LuckyCycles", luckyCycles);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items", Tag.TAG_COMPOUND)) {
            CompoundTag saved = tag.getCompound("Items");
            saved.putInt("Size", SLOTS);
            items.deserializeNBT(saved);
        }
        mana = Math.max(0, tag.getInt(TAG_MANA));
        progress = Math.max(0, tag.getInt("Progress"));
        redstone = RedstoneMode.byId(tag.getByte("Redstone"));
        output = !tag.contains("Output") || tag.getBoolean("Output");
        binding = tag.contains("Binding", Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound("Binding")) : null;
        cycles = tag.getInt("Cycles") & 0x7FFF;
        luckyCycles = tag.getInt("LuckyCycles") & 0x7FFF;
        dirty = true;
    }

    /** What the client needs: the flowers, the mana, the binding and how many look inside. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ListTag flowers = new ListTag();
        for (int i = 0; i < FLOWERS; i++) {
            ItemStack stack = items.getStackInSlot(FLOWER_START + i);
            if (stack.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putByte("Slot", (byte) i);
            stack.save(t);
            flowers.add(t);
        }
        tag.put("Flowers", flowers);
        tag.putInt(TAG_MANA, mana);
        tag.putInt("Capacity", capacity);
        tag.putInt("Openers", openersCounter.getOpenerCount());
        if (binding != null) tag.put("Binding", NbtUtils.writeBlockPos(binding));
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
        for (int i = 0; i < FLOWERS; i++) clientFlowers[i] = ItemStack.EMPTY;
        ListTag flowers = tag.getList("Flowers", Tag.TAG_COMPOUND);
        for (int i = 0; i < flowers.size(); i++) {
            CompoundTag t = flowers.getCompound(i);
            int slot = t.getByte("Slot");
            if (slot >= 0 && slot < FLOWERS) clientFlowers[slot] = ItemStack.of(t);
        }
        clientMana = tag.getInt(TAG_MANA);
        clientCapacity = Math.max(1, tag.getInt("Capacity"));
        openers = tag.getInt("Openers");
        binding = tag.contains("Binding", Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound("Binding")) : null;
    }

    /** The bud blooms open above the block and its petals lean out past the sides. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.6D, 0.0D, 0.6D).expandTowards(0.0D, 0.8D, 0.0D);
    }

    @Override
    public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }
}
