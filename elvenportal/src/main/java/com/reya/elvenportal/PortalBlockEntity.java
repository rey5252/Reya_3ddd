package com.reya.elvenportal;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;

/**
 * The portal: every few ticks it looks for an elven trade the items in its nine input slots allow, and if
 * the outputs fit in its nine output slots and it has the mana, the elves take the items and send back
 * the trade. Mana comes from mana bursts (it is a mana receiver, like a pool) and from the mana pools on
 * its six sides; items come by hand, by hoppers and pipes, or thrown on the portal.
 * <p>
 * The client copy knows the mana (for the Wand of the Forest's HUD) and the last trade's items, which the
 * renderer shows going into the portal and coming out.
 */
public class PortalBlockEntity extends BlockEntity implements MenuProvider, ManaReceiver {
    public static final int INPUTS = 9, OUTPUTS = 9;
    public static final int INPUT_START = 0, OUTPUT_START = INPUTS, SLOTS = INPUTS + OUTPUTS;
    public static final String TAG_MANA = "Mana";
    /** Block events: a trade was made (the parameter is the raw id of the item that went in / came out). */
    public static final int EVENT_TRADE_IN = 1, EVENT_TRADE_OUT = 2;
    /** What the menu's data holds (see {@link #data}). */
    public static final int DATA_COUNT = 15;
    /**
     * The traded items' flights in the world (ticks): the item that went in flies into the portal for
     * FLY_IN_TICKS; what came back flies out of it FLY_OUT_DELAY ticks after, for FLY_OUT_TICKS.
     */
    public static final int FLY_IN_TICKS = 8, FLY_OUT_DELAY = 6, FLY_OUT_TICKS = 14;
    /** How long the portal stays open after it can no longer trade, so it doesn't flicker. */
    private static final int OPEN_LINGER = 40;

    public enum Status { TRADING, IDLE, NO_MANA, OUTPUT_FULL, REDSTONE }

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
    private Status status = Status.IDLE;
    private RedstoneMode redstone = RedstoneMode.IGNORE;
    private int cooldown;
    /** Trades made (wraps round): the screen animates each new one. */
    private int trades;
    /** The last trade: raw ids of the item that went in and the first that came out (+1, 0 = none), and its count. */
    private int lastIn, lastOut, lastOutCount;
    private int openTicks, soundTicks;
    private boolean hasPool;
    private int offeredTrades;
    private int lastSignal = -1, syncedMana = -1;

    // client side
    private int clientMana, clientCapacity = 1;
    private long clientAge;
    private float openness, prevOpenness;
    private ItemStack flyIn = ItemStack.EMPTY, flyOut = ItemStack.EMPTY;
    private long flyInAge = -1000L, flyOutAge = -1000L;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot < OUTPUT_START && Trades.accepts(level, stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            PortalBlockEntity.this.setChanged();
        }
    };

    /** Hoppers and pipes may put into the input slots what the elves take, and take from the output slots. */
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
            return slot < OUTPUT_START ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot >= OUTPUT_START ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
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

    private final LazyOptional<ManaReceiver> manaCap = LazyOptional.of(() -> this);

    /** The menu's numbers, each 16 bits (the game sends them as shorts): big ones in two halves. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int cost = Config.MANA_PER_TRADE.get(), capacity = capacity();
            return switch (index) {
                case 0 -> mana & 0xFFFF;
                case 1 -> mana >>> 16 & 0xFFFF;
                case 2 -> capacity & 0xFFFF;
                case 3 -> capacity >>> 16 & 0xFFFF;
                case 4 -> cost & 0xFFFF;
                case 5 -> cost >>> 16 & 0xFFFF;
                case 6 -> flags();
                case 7 -> trades;
                case 8 -> lastIn & 0xFFFF;
                case 9 -> lastIn >>> 16 & 0xFFFF;
                case 10 -> lastOut & 0xFFFF;
                case 11 -> lastOut >>> 16 & 0xFFFF;
                case 12 -> lastOutCount;
                case 13 -> Config.TRADE_TICKS.get();
                case 14 -> offeredTrades;
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

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(ElvenPortal.PORTAL_BE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public static int capacity() {
        return Config.CAPACITY.get();
    }

    // ------------------------------------------------------------------ server

    public static void serverTick(Level level, BlockPos pos, BlockState state, PortalBlockEntity be) {
        int capacity = capacity();
        if (be.mana > capacity) be.mana = capacity;
        long time = level.getGameTime();
        if (time % 20L == 0L) be.lookAround(level, pos);
        if (Config.DRAW_FROM_POOLS.get() && be.mana < capacity) be.drawFromPools(level, pos);
        if (Config.ACCEPT_THROWN_ITEMS.get() && time % 4L == 0L) be.takeThrownItems(level, pos);

        int cost = Config.MANA_PER_TRADE.get();
        boolean allowed = be.redstone.allows(level.hasNeighborSignal(pos));
        if (!allowed) be.status = Status.REDSTONE;
        else if (be.mana < cost) be.status = Status.NO_MANA;
        else if (be.status == Status.REDSTONE || be.status == Status.NO_MANA) be.status = Status.IDLE;

        if (be.cooldown > 0) be.cooldown--;
        if (allowed && be.mana >= cost && be.cooldown <= 0) {
            be.cooldown = Config.TRADE_TICKS.get();
            be.tryTrade(level, pos, state, cost);
        }
        if (be.soundTicks > 0) be.soundTicks--;

        if (allowed && be.mana >= cost) be.openTicks = OPEN_LINGER;
        else if (be.openTicks > 0) be.openTicks--;
        boolean open = be.openTicks > 0;
        if (state.getValue(PortalBlock.OPEN) != open) {
            level.setBlock(pos, state.setValue(PortalBlock.OPEN, open), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, open ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.4F, 1.7F);
        }
        int signal = be.comparatorSignal();
        if (signal != be.lastSignal) {
            be.lastSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        if (time % 20L == 0L && be.mana != be.syncedMana) be.syncToClients();
    }

    /** Finds a trade the inputs allow and makes it, if the outputs fit. */
    private void tryTrade(Level level, BlockPos pos, BlockState state, int cost) {
        Trades.Match match = Trades.find(level, items, INPUT_START, OUTPUT_START);
        if (match == null) {
            status = Status.IDLE;
            return;
        }
        if (!Trades.insertAll(items, OUTPUT_START, SLOTS, match.outputs(), true)) {
            status = Status.OUTPUT_FULL;
            return;
        }
        for (int i = 0; i < INPUTS; i++) {
            if (match.taken()[i] > 0) items.extractItem(INPUT_START + i, match.taken()[i], false);
        }
        Trades.insertAll(items, OUTPUT_START, SLOTS, match.outputs(), false);
        mana -= cost;
        status = Status.TRADING;
        trades = trades + 1 & 0x7FFF;
        ItemStack out = match.outputs().get(0);
        lastIn = Item.getId(match.firstInput().getItem()) + 1;
        lastOut = Item.getId(out.getItem()) + 1;
        lastOutCount = Math.min(out.getCount(), 0x7FFF);
        level.blockEvent(pos, state.getBlock(), EVENT_TRADE_IN, lastIn - 1);
        level.blockEvent(pos, state.getBlock(), EVENT_TRADE_OUT, lastOut - 1);
        if (soundTicks <= 0) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.25F + level.random.nextFloat() * 0.4F);
            soundTicks = 16;
        }
        setChanged();
    }

    /** Takes mana from the mana pools on its six sides, as the gateway does through its pylons. */
    private void drawFromPools(Level level, BlockPos pos) {
        int budget = Math.min(Config.DRAW_PER_TICK.get(), capacity() - mana);
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

    @Nullable
    private static ManaPool poolAt(Level level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be instanceof PortalBlockEntity) return null;
        ManaReceiver receiver = be.getCapability(BotaniaForgeCapabilities.MANA_RECEIVER, side).resolve().orElse(null);
        return receiver instanceof ManaPool pool ? pool : null;
    }

    /** Once a second: is there a pool beside it, and how many trades do the elves offer (for the GUI). */
    private void lookAround(Level level, BlockPos pos) {
        boolean pool = false;
        for (Direction dir : Direction.values()) {
            if (poolAt(level, pos.relative(dir), dir.getOpposite()) != null) {
                pool = true;
                break;
            }
        }
        hasPool = pool;
        int n = 0;
        for (var recipe : Trades.recipes(level)) {
            if (!Trades.isReturn(recipe)) n++;
        }
        offeredTrades = Math.min(n, 0x7FFF);
    }

    /** Items thrown on the portal (or dropped into its ring) go in, if the elves take them. */
    private void takeThrownItems(Level level, BlockPos pos) {
        AABB box = new AABB(pos).expandTowards(0.0D, 0.4D, 0.0D);
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty());
        boolean took = false;
        for (ItemEntity entity : entities) {
            ItemStack stack = entity.getItem();
            if (!Trades.accepts(level, stack)) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(items, stack.copy(), false);
            if (rest.getCount() == stack.getCount()) continue;
            took = true;
            if (rest.isEmpty()) entity.discard();
            else entity.setItem(rest);
        }
        if (took) level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F, 0.8F);
    }

    public int comparatorSignal() {
        int capacity = capacity();
        if (mana <= 0 || capacity <= 0) return 0;
        return 1 + (int) (14L * Math.min(mana, capacity) / capacity);
    }

    private int flags() {
        int f = status.ordinal();
        f |= redstone.ordinal() << 3;
        if (getBlockState().getValue(PortalBlock.OPEN)) f |= 1 << 5;
        if (hasPool) f |= 1 << 6;
        return f;
    }

    public void cycleRedstone() {
        redstone = redstone.next();
        setChanged();
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
        if (id == EVENT_TRADE_IN || id == EVENT_TRADE_OUT) {
            if (level != null && level.isClientSide) {
                ItemStack stack = new ItemStack(Item.byId(param));
                // trades come faster than their flights: a flight under way plays out, the trades meanwhile aren't shown
                if (id == EVENT_TRADE_IN && clientAge - flyInAge >= FLY_OUT_DELAY + FLY_OUT_TICKS) {
                    flyIn = stack;
                    flyInAge = clientAge;
                } else if (id == EVENT_TRADE_OUT && flyInAge == clientAge) {
                    flyOut = stack;
                    flyOutAge = clientAge + FLY_OUT_DELAY;
                }
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, PortalBlockEntity be) {
        be.clientAge++;
        be.prevOpenness = be.openness;
        boolean open = state.getValue(PortalBlock.OPEN);
        be.openness = open ? Math.min(1.0F, be.openness + 0.06F) : Math.max(0.0F, be.openness - 0.08F);
        // sparkles burst round the ring as the portal opens, and from its middle when a traded item reaches it
        if (be.prevOpenness < 0.5F && be.openness >= 0.5F) be.sparkle(level, pos, state, 14, 0.42D);
        if (be.clientAge == be.flyInAge + FLY_IN_TICKS) be.sparkle(level, pos, state, 8, 0.2D);
    }

    private void sparkle(Level level, BlockPos pos, BlockState state, int n, double radius) {
        Direction facing = state.getValue(PortalBlock.FACING);
        double ax = Math.abs(facing.getStepZ()), az = Math.abs(facing.getStepX());      // across the ring
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double r = radius * (0.7D + level.random.nextDouble() * 0.3D);
            double across = Math.cos(a) * r, up = 9.0D / 16.0D + Math.sin(a) * r;
            boolean gold = level.random.nextInt(3) == 0;
            BotaniaAPI.instance().sparkleFX(level, pos.getX() + 0.5D + ax * across, pos.getY() + up, pos.getZ() + 0.5D + az * across,
                    gold ? 1.0F : 0.4F, gold ? 0.85F : 1.0F, gold ? 0.35F : 0.5F, 0.6F + level.random.nextFloat() * 0.5F,
                    5 + level.random.nextInt(4));
        }
    }

    public float openness(float partialTick) {
        return Mth.lerp(partialTick, prevOpenness, openness);
    }

    public long clientAge() {
        return clientAge;
    }

    public ItemStack flyIn() {
        return flyIn;
    }

    public ItemStack flyOut() {
        return flyOut;
    }

    public long flyInAge() {
        return flyInAge;
    }

    public long flyOutAge() {
        return flyOutAge;
    }

    public int clientMana() {
        return clientMana;
    }

    public int clientCapacity() {
        return Math.max(1, clientCapacity);
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.elvenportal.elven_portal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortalMenu(id, inventory, this, data);
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            syncedMana = mana;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        if (mana > 0) tag.putInt(TAG_MANA, mana);
        tag.putByte("Redstone", (byte) redstone.ordinal());
        tag.putInt("Trades", trades);
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
        redstone = RedstoneMode.byId(tag.getByte("Redstone"));
        trades = tag.getInt("Trades") & 0x7FFF;
    }

    /** What the client needs: the mana, for the Wand of the Forest's HUD. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_MANA, mana);
        tag.putInt("Capacity", capacity());
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
        clientMana = tag.getInt(TAG_MANA);
        clientCapacity = Math.max(1, tag.getInt("Capacity"));
    }

    /** The traded items fly a little in front of the gate, and the motes circle just outside it. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.5D);
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
