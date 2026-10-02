package com.reya.singularityfusion.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.reya.singularityfusion.FusionConfig;
import com.reya.singularityfusion.SingularityFusion;
import com.reya.singularityfusion.menu.FusionCoreMenu;
import com.reya.singularityfusion.recipe.FusionRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * The fusion core: the heart of the structure. It stands on a 3x3 of void casing with the space over it clear, finds
 * the graviton pylons round it, and holds energy (Forge Energy: RF, and Draconic Evolution's OP). Over it the
 * singularity forms, as big as the core is full. With a catalyst in it and the pylons' items making a recipe, and the
 * energy the recipe takes, it fuses them (started from its screen or by a redstone pulse): the pylons pour their items
 * into the singularity, and the result comes out into the core's output; the energy is spent.
 */
public class FusionCoreBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CATALYST = 0, OUTPUT = 1;
    /** How far over the core the singularity's middle hangs, in blocks (from the core's top). */
    public static final double HOLE_HEIGHT = 5.0D;
    /** The space over the core that must be clear, from one block up to this many. */
    public static final int CLEAR_HEIGHT = 7;
    /** What a core must stand on: the void casings (data/singularityfusion/tags/blocks/foundation.json). */
    public static final TagKey<Block> FOUNDATION = BlockTags.create(new ResourceLocation(SingularityFusion.MODID, "foundation"));
    /** Block events the clients see (BlockEntity.triggerEvent). */
    public static final int EVENT_START = 1, EVENT_DONE = 2, EVENT_ABORT = 3;

    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            recheck = true;
            syncNeeded = true;
        }
    };
    private final LazyOptional<IItemHandler> automation = LazyOptional.of(() -> new Automation());
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> new Energy());

    private long energy;
    private int takenThisTick;
    private final List<BlockPos> pylons = new ArrayList<>();
    private boolean foundation, clear;
    private FusionStatus status = FusionStatus.NO_FOUNDATION;
    @Nullable
    private FusionRecipe recipe;
    private ItemStack preview = ItemStack.EMPTY;
    private long cost;
    private int craftTicks = -1, craftTime = 1;
    private boolean powered;
    private int age;
    private boolean recheck = true, syncNeeded = true;
    private long syncedEnergy = -1;
    private int syncWait;
    /** The capacity as the server has it (the client's config may differ). */
    private long capacity = -1;

    // the client's own: when it last saw a fusion start and end, how far its picture of the charge has caught up, and
    // how far the singularity's disk has turned
    public long startedAt = Long.MIN_VALUE, doneAt = Long.MIN_VALUE, abortedAt = Long.MIN_VALUE;
    public float shownCharge, spin;
    public long lastFrameNanos;

    public FusionCoreBlockEntity(BlockPos pos, BlockState state) {
        super(SingularityFusion.FUSION_CORE_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------ what the screen and the renderers read

    public ItemStackHandler items() {
        return items;
    }

    public long energy() {
        return energy;
    }

    /** For the game tests and creative fiddling: sets the energy (within the capacity). */
    public void setEnergy(long energy) {
        this.energy = Mth.clamp(energy, 0L, capacity());
        setChanged();
        recheck = true;
    }

    public long capacity() {
        return level != null && level.isClientSide && capacity > 0 ? capacity : FusionConfig.CAPACITY.get();
    }

    /** How full the core is (0 to 1), and so how big its singularity: nothing while the structure isn't whole. */
    public float charge() {
        if (!status.formed()) return 0.0F;
        return (float) Mth.clamp(energy / (double) capacity(), 0.0D, 1.0D);
    }

    public FusionStatus status() {
        return status;
    }

    public List<BlockPos> pylons() {
        return pylons;
    }

    public boolean fusing() {
        return craftTicks >= 0;
    }

    /** How far the fusion has come (0 to 1). */
    public float progress(float partialTick) {
        if (craftTicks < 0) return 0.0F;
        return Mth.clamp((craftTicks + partialTick) / Math.max(1, craftTime), 0.0F, 1.0F);
    }

    public int craftTime() {
        return craftTime;
    }

    /** What the catalyst and the pylons make, and the energy it takes (nothing when they make nothing). */
    public ItemStack preview() {
        return preview;
    }

    public long cost() {
        return cost;
    }

    /** Where the singularity's middle is. */
    public double holeX() {
        return worldPosition.getX() + 0.5D;
    }

    public double holeY() {
        return worldPosition.getY() + 1.0D + HOLE_HEIGHT;
    }

    public double holeZ() {
        return worldPosition.getZ() + 0.5D;
    }

    // ------------------------------------------------------------------ working

    public static void serverTick(Level level, BlockPos pos, BlockState state, FusionCoreBlockEntity core) {
        if (level instanceof ServerLevel server) core.tick(server, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FusionCoreBlockEntity core) {
        if (core.craftTicks >= 0 && core.craftTicks < core.craftTime) core.craftTicks++;
        core.particles(level);
    }

    /**
     * The client's particles: once the core is more than half full, matter is drawn into the singularity from round
     * it; during a fusion, glyphs stream from the pylons' tips into it.
     */
    private void particles(Level level) {
        float c = charge();
        if (c <= 0.0F) return;
        RandomSource random = level.random;
        double hx = holeX(), hy = holeY(), hz = holeZ();
        float r = FusionGeometry.HOLE_RADIUS * c;
        float pull = Mth.clamp((c - 0.55F) / 0.45F, 0.0F, 1.0F) + (fusing() ? 0.6F : 0.0F);
        int count = (int) (pull * 2.5F + random.nextFloat());
        for (int i = 0; i < count; i++) {
            // a portal particle comes in from its offset (and a block over it) to where it was made
            Vec3 d = direction(random).scale(r * (2.4D + 2.2D * random.nextDouble()));
            level.addParticle(ParticleTypes.PORTAL, hx, hy - 0.5D, hz, d.x, d.y, d.z);
        }
        if (!fusing()) return;
        float p = progress(0.0F);
        if (p < FusionGeometry.BEAMS_IN || p > FusionGeometry.COLLAPSE) return;
        for (BlockPos at : pylons) {
            if (random.nextFloat() > 0.45F) continue;
            // an enchanting glyph flies from its offset to where it was made, dropping 1.2 at the end
            Vec3 tip = FusionGeometry.tip(at, worldPosition);
            level.addParticle(ParticleTypes.ENCHANT, hx, hy + 1.2D, hz, tip.x - hx, tip.y - hy - 1.2D, tip.z - hz);
        }
    }

    /** The burst when a fusion ends: light flung out of the singularity. */
    private void burst(Level level) {
        RandomSource random = level.random;
        float r = FusionGeometry.HOLE_RADIUS * Math.max(0.3F, charge());
        for (int i = 0; i < 56; i++) {
            Vec3 d = direction(random);
            double speed = 0.25D + 0.3D * random.nextDouble();
            level.addParticle(ParticleTypes.END_ROD, holeX() + d.x * r, holeY() + d.y * r, holeZ() + d.z * r, d.x * speed, d.y * speed,
                    d.z * speed);
        }
        for (int i = 0; i < 40; i++) {
            Vec3 d = direction(random);
            level.addParticle(ParticleTypes.REVERSE_PORTAL, holeX() + d.x * r, holeY() + d.y * r, holeZ() + d.z * r, d.x * 0.6D, d.y * 0.6D,
                    d.z * 0.6D);
        }
    }

    private static Vec3 direction(RandomSource random) {
        Vec3 d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        double length = d.length();
        return length < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : d.scale(1.0D / length);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        age++;
        takenThisTick = 0;
        if (age % 40 == 1) scan(level, pos);
        if (craftTicks >= 0) {
            if (recipe == null || age % 10 == 0) {
                scan(level, pos);
                if (recipe == null) evaluate(level);
                if (!holds(level)) abort(level, pos);
            }
            if (craftTicks >= 0) {
                craftTicks++;
                if (craftTicks >= craftTime) finish(level, pos);
                else if (craftTicks % 5 == 0) syncNeeded = true;
            }
        } else if (recheck || age % 10 == 0) {
            recheck = false;
            evaluate(level);
        }
        // a redstone pulse starts a fusion
        boolean now = level.hasNeighborSignal(pos);
        if (now && !powered) start();
        powered = now;
        // lit while the singularity is there; it hums
        boolean lit = status.formed() && energy > 0;
        if (state.hasProperty(FusionCoreBlock.LIT) && state.getValue(FusionCoreBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(FusionCoreBlock.LIT, lit), Block.UPDATE_ALL);
        }
        if (lit && age % 80 == 0) {
            float c = charge();
            level.playSound(null, holeX(), holeY(), holeZ(), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.4F + 0.8F * c, 0.5F + 0.3F * c);
        }
        if (--syncWait <= 0 && (syncNeeded || Math.abs(energy - syncedEnergy) > capacity() / 1000 || (energy == 0) != (syncedEnergy == 0))) {
            send(level);
            syncWait = 4;
            syncNeeded = false;
            syncedEnergy = energy;
        }
    }

    /** Whether the structure is whole: the foundation under it, the space over it clear, pylons round it. */
    private void scan(ServerLevel level, BlockPos pos) {
        boolean wasFoundation = foundation, wasClear = clear;
        foundation = !FusionConfig.NEEDS_FOUNDATION.get() || foundationAt(level, pos);
        clear = true;
        for (int k = 1; k <= CLEAR_HEIGHT; k++) {
            BlockState above = level.getBlockState(pos.above(k));
            if (!above.isAir() && !above.canBeReplaced()) {
                clear = false;
                break;
            }
        }
        // the pylons in reach (another core's stay its own), closest first, then in order round the core
        int r = FusionConfig.PYLON_RADIUS.get();
        List<GravitonPylonBlockEntity> found = new ArrayList<>();
        for (int cx = (pos.getX() - r) >> 4; cx <= (pos.getX() + r) >> 4; cx++) {
            for (int cz = (pos.getZ() - r) >> 4; cz <= (pos.getZ() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof GravitonPylonBlockEntity pylon) || pylon.isRemoved()) continue;
                    BlockPos p = pylon.getBlockPos();
                    int dx = p.getX() - pos.getX(), dy = p.getY() - pos.getY(), dz = p.getZ() - pos.getZ();
                    if (Math.abs(dx) > r || Math.abs(dz) > r || dy < -2 || dy > 4) continue;
                    BlockPos owner = pylon.core();
                    if (owner != null && !owner.equals(pos) && level.getBlockEntity(owner) instanceof FusionCoreBlockEntity) continue;
                    found.add(pylon);
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.getBlockPos().distSqr(pos)));
        if (found.size() > FusionConfig.MAX_PYLONS.get()) found = new ArrayList<>(found.subList(0, FusionConfig.MAX_PYLONS.get()));
        found.sort(Comparator.comparingDouble(p -> Math.atan2(p.getBlockPos().getZ() - pos.getZ(), p.getBlockPos().getX() - pos.getX())));
        List<BlockPos> now = new ArrayList<>();
        for (GravitonPylonBlockEntity p : found) now.add(p.getBlockPos().immutable());
        for (BlockPos old : pylons) {
            if (!now.contains(old) && level.getBlockEntity(old) instanceof GravitonPylonBlockEntity p && pos.equals(p.core())) p.link(null);
        }
        for (GravitonPylonBlockEntity p : found) p.link(pos);
        if (!now.equals(pylons) || wasFoundation != foundation || wasClear != clear) {
            pylons.clear();
            pylons.addAll(now);
            syncNeeded = true;
            recheck = true;
        }
    }

    private static boolean foundationAt(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getBlockState(pos.offset(dx, -1, dz)).is(FOUNDATION)) return false;
            }
        }
        return true;
    }

    /** The items on the pylons (the empty ones left out). */
    private List<ItemStack> pylonItems(Level level) {
        List<ItemStack> out = new ArrayList<>();
        for (BlockPos p : pylons) {
            if (level.getBlockEntity(p) instanceof GravitonPylonBlockEntity pylon && !pylon.item().isEmpty()) out.add(pylon.item());
        }
        return out;
    }

    /** Finds what the catalyst and the pylons make, and what the core waits for. */
    private void evaluate(Level level) {
        FusionStatus before = status;
        ItemStack previewBefore = preview;
        long costBefore = cost;
        recipe = null;
        preview = ItemStack.EMPTY;
        cost = 0;
        if (!foundation) status = FusionStatus.NO_FOUNDATION;
        else if (!clear) status = FusionStatus.BLOCKED;
        else if (pylons.isEmpty()) status = FusionStatus.NO_PYLONS;
        else {
            List<ItemStack> held = pylonItems(level);
            ItemStack catalyst = items.getStackInSlot(CATALYST);
            for (FusionRecipe r : level.getRecipeManager().getAllRecipesFor(SingularityFusion.FUSION_TYPE.get())) {
                if (r.matches(catalyst, held)) {
                    recipe = r;
                    break;
                }
            }
            if (recipe == null) status = FusionStatus.NO_RECIPE;
            else {
                preview = recipe.result();
                cost = recipe.energy();
                if (!fits(recipe.result())) status = FusionStatus.OUTPUT_FULL;
                else if (energy < cost) status = FusionStatus.NO_ENERGY;
                else status = FusionStatus.READY;
            }
        }
        if (craftTicks >= 0) status = FusionStatus.FUSING;
        if (status != before || cost != costBefore || !ItemStack.matches(preview, previewBefore)) syncNeeded = true;
    }

    private boolean fits(ItemStack result) {
        return items.insertItem(OUTPUT, result.copy(), true).isEmpty();
    }

    /** Whether the fusion under way may go on: the structure whole, the same recipe still made, the energy there. */
    private boolean holds(Level level) {
        return recipe != null && foundation && clear && recipe.matches(items.getStackInSlot(CATALYST), pylonItems(level)) && energy >= recipe.energy()
                && fits(recipe.result());
    }

    /** Starts a fusion if everything is in place (from the screen's button, or a redstone pulse). */
    public boolean start() {
        if (craftTicks >= 0 || !(level instanceof ServerLevel server)) return false;
        scan(server, worldPosition);
        evaluate(server);
        if (status != FusionStatus.READY || recipe == null) return false;
        craftTicks = 0;
        craftTime = Math.max(20, (int) Math.round(recipe.time() * FusionConfig.TIME_MULTIPLIER.get()));
        status = FusionStatus.FUSING;
        server.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_START, 0);
        server.playSound(null, holeX(), holeY(), holeZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.0F, 0.6F);
        syncNeeded = true;
        setChanged();
        return true;
    }

    private void abort(ServerLevel level, BlockPos pos) {
        craftTicks = -1;
        level.blockEvent(pos, getBlockState().getBlock(), EVENT_ABORT, 0);
        level.playSound(null, holeX(), holeY(), holeZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.7F);
        evaluate(level);
        syncNeeded = true;
        setChanged();
    }

    /** The fusion's end: the energy spent, the catalyst and the pylons' items taken (what they leave stays), the result out. */
    private void finish(ServerLevel level, BlockPos pos) {
        FusionRecipe done = recipe;
        if (done == null || !holds(level)) {
            abort(level, pos);
            return;
        }
        craftTicks = -1;
        energy -= done.energy();
        items.extractItem(CATALYST, 1, false);
        for (BlockPos p : pylons) {
            if (level.getBlockEntity(p) instanceof GravitonPylonBlockEntity pylon && !pylon.item().isEmpty()) {
                pylon.setItem(pylon.item().getCraftingRemainingItem());
            }
        }
        items.insertItem(OUTPUT, done.result().copy(), false);
        level.blockEvent(pos, getBlockState().getBlock(), EVENT_DONE, 0);
        level.playSound(null, holeX(), holeY(), holeZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.6F);
        level.playSound(null, holeX(), holeY(), holeZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.6F, 1.8F);
        evaluate(level);
        syncNeeded = true;
        setChanged();
    }

    /** The core is gone: its pylons are free again. */
    public void release() {
        if (level == null || level.isClientSide) return;
        for (BlockPos p : pylons) {
            if (level.getBlockEntity(p) instanceof GravitonPylonBlockEntity pylon && worldPosition.equals(pylon.core())) pylon.link(null);
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != EVENT_START && id != EVENT_DONE && id != EVENT_ABORT) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            long now = level.getGameTime();
            if (id == EVENT_START) {
                startedAt = now;
                craftTicks = 0;
            } else if (id == EVENT_DONE) {
                doneAt = now;
                craftTicks = -1;
                burst(level);
            } else {
                abortedAt = now;
                craftTicks = -1;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the clients' copy

    /** Sends the clients near it what changed, without the block update (that would redraw the chunk). */
    private void send(ServerLevel level) {
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(worldPosition), false)) {
            player.connection.send(packet);
        }
    }

    private void writeShared(CompoundTag tag) {
        tag.putLong("Energy", energy);
        tag.putLong("Cap", capacity());
        tag.putByte("Status", (byte) status.ordinal());
        long[] at = new long[pylons.size()];
        for (int i = 0; i < at.length; i++) at[i] = pylons.get(i).asLong();
        tag.putLongArray("Pylons", at);
        tag.putInt("Craft", craftTicks);
        tag.putInt("CraftTime", craftTime);
        tag.putLong("Cost", cost);
        tag.put("Preview", preview.save(new CompoundTag()));
        tag.put("Catalyst", items.getStackInSlot(CATALYST).save(new CompoundTag()));
        tag.put("Output", items.getStackInSlot(OUTPUT).save(new CompoundTag()));
    }

    private void readShared(CompoundTag tag) {
        energy = tag.getLong("Energy");
        capacity = tag.getLong("Cap");
        status = FusionStatus.byId(tag.getByte("Status"));
        pylons.clear();
        for (long at : tag.getLongArray("Pylons")) pylons.add(BlockPos.of(at));
        craftTicks = tag.getInt("Craft");
        craftTime = Math.max(1, tag.getInt("CraftTime"));
        cost = tag.getLong("Cost");
        preview = ItemStack.of(tag.getCompound("Preview"));
        items.setStackInSlot(CATALYST, ItemStack.of(tag.getCompound("Catalyst")));
        items.setStackInSlot(OUTPUT, ItemStack.of(tag.getCompound("Output")));
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
        if (tag != null) readShared(tag);
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putLong("Energy", energy);
        tag.putInt("Craft", craftTicks);
        tag.putInt("CraftTime", craftTime);
        tag.putBoolean("Powered", powered);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        energy = Math.max(0L, tag.getLong("Energy"));
        craftTicks = tag.contains("Craft") ? tag.getInt("Craft") : -1;
        craftTime = Math.max(1, tag.getInt("CraftTime"));
        powered = tag.getBoolean("Powered");
    }

    // ------------------------------------------------------------------ the screen, pipes and cables

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.singularityfusion.fusion_core");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FusionCoreMenu(id, inventory, this);
    }

    /** The singularity hangs well over the core and spreads wide; its jets reach further up. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(8.0D, 1.0D, 8.0D).expandTowards(0.0D, HOLE_HEIGHT + 12.0D, 0.0D);
    }

    public int comparatorSignal() {
        return Math.round(charge() * 15.0F);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return automation.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        automation.invalidate();
    }

    /** Energy in from any side, as much as it has room for (up to the config's limit a tick); none out. */
    private class Energy implements IEnergyStorage {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            if (max <= 0) return 0;
            long room = capacity() - energy;
            long limit = (long) FusionConfig.MAX_INPUT.get() - takenThisTick;
            int take = (int) Math.max(0L, Math.min(Math.min(max, limit), room));
            if (!simulate && take > 0) {
                energy += take;
                takenThisTick += take;
                setChanged();
            }
            return take;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(energy, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(capacity(), Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    /** Pipes put catalysts in and take results out. */
    private class Automation implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        @Nonnull
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return slot == CATALYST ? items.insertItem(CATALYST, stack, simulate) : stack;
        }

        @Override
        @Nonnull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT ? items.extractItem(OUTPUT, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot == CATALYST;
        }
    }
}
