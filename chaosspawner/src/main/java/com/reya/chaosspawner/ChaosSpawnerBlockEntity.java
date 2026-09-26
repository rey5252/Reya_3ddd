package com.reya.chaosspawner;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
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

/**
 * Every round it counts one kill per soul in the soul slots (times the quantity upgrades), rolls
 * each mob's loot table as a player kill into the output slots and stores the experience.
 * Stops while the outputs are full or a redstone signal reaches it; pushes loot into containers
 * next to it.
 */
public class ChaosSpawnerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SOULS = 11, OUTPUTS = 21, UPGRADES = 9;
    public static final int SOUL_START = 0, OUTPUT_START = SOULS, UPGRADE_START = SOULS + OUTPUTS;
    public static final int SLOTS = SOULS + OUTPUTS + UPGRADES;

    private int progress;
    private int maxProgress = 200;
    private int xp;
    private boolean running;
    private final Map<EntityType<?>, LivingEntity> lootEntities = new LinkedHashMap<>();

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot < OUTPUT_START) return SoulCrystalItem.isSoul(stack);
            if (slot < UPGRADE_START) return false;
            return stack.getItem() instanceof SpawnerUpgradeItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // the cage on the client shows the first soul's mob
            if (slot < OUTPUT_START && level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    /** Hoppers and pipes may take loot out; nothing goes in from outside. */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return OUTPUTS;
        }

        @Override
        public @Nonnull ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(OUTPUT_START + slot);
        }

        @Override
        public @Nonnull ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @Nonnull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return items.extractItem(OUTPUT_START + slot, amount, simulate);
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

    /** progress, max progress, stored xp in two 15-bit halves (container data syncs as shorts), running. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> xp & 0x7FFF;
                case 3 -> (xp >>> 15) & 0x7FFF;
                default -> running ? 1 : 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    public ChaosSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ChaosSpawner.SPAWNER_BE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public int upgrades(SpawnerUpgradeItem.Kind kind) {
        int n = 0;
        for (int i = UPGRADE_START; i < SLOTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.getItem() instanceof SpawnerUpgradeItem u && u.kind == kind) n += s.getCount();
        }
        return n;
    }

    public int roundTicks() {
        return Math.max(10, (int) (Config.CYCLE_TICKS.get() * Math.pow(0.85D, Math.min(24, upgrades(SpawnerUpgradeItem.Kind.SPEED)))));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChaosSpawnerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (level.getGameTime() % 20L == 0L) be.pushOutputs(server);
        be.maxProgress = be.roundTicks();
        boolean hasSouls = false;
        for (int i = SOUL_START; i < OUTPUT_START; i++) hasSouls |= SoulCrystalItem.isSoul(be.items.getStackInSlot(i));
        be.running = hasSouls && !level.hasNeighborSignal(pos) && be.hasFreeOutput();
        if (!be.running) return;
        if (++be.progress < be.maxProgress) return;
        be.progress = 0;
        be.round(server);
    }

    private boolean hasFreeOutput() {
        for (int i = OUTPUT_START; i < UPGRADE_START; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    private void round(ServerLevel level) {
        int perSoul = 1 + upgrades(SpawnerUpgradeItem.Kind.QUANTITY);
        int budget = Config.MAX_KILLS.get();
        Map<EntityType<?>, Integer> kills = new LinkedHashMap<>();
        for (int i = SOUL_START; i < OUTPUT_START && budget > 0; i++) {
            ItemStack s = items.getStackInSlot(i);
            EntityType<?> type = SoulCrystalItem.soulType(s);
            if (type == null) continue;
            int n = Math.min(budget, s.getCount() * perSoul);
            kills.merge(type, n, Integer::sum);
            budget -= n;
        }
        int looting = Math.min(10, upgrades(SpawnerUpgradeItem.Kind.LOOTING));
        float xpBoost = 1.0F + 0.5F * upgrades(SpawnerUpgradeItem.Kind.EXPERIENCE);
        Vec3 center = Vec3.atCenterOf(worldPosition);
        FakePlayer killer = FakePlayerFactory.getMinecraft(level);
        ItemStack weapon = new ItemStack(Items.NETHERITE_SWORD);
        if (looting > 0) weapon.enchant(Enchantments.MOB_LOOTING, looting);
        killer.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        try {
            for (Map.Entry<EntityType<?>, Integer> e : kills.entrySet()) {
                LivingEntity mob = lootEntity(level, e.getKey());
                if (mob == null) continue;
                mob.setPos(center.x, center.y, center.z);
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, mob)
                        .withParameter(LootContextParams.ORIGIN, center)
                        .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                        .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                        .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                        .create(LootContextParamSets.ENTITY);
                LootTable table = level.getServer().getLootData().getLootTable(mob.getLootTable());
                int xpEach = mob instanceof Enemy ? 5 : mob instanceof Animal ? 2 : 1;
                for (int k = 0; k < e.getValue(); k++) {
                    for (ItemStack stack : table.getRandomItems(params)) insertOutput(stack);
                }
                long gained = (long) Math.round(xpEach * e.getValue() * xpBoost);
                xp = (int) Math.min(Config.MAX_XP.get(), (long) xp + gained);
            }
        } finally {
            killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Nullable
    private LivingEntity lootEntity(ServerLevel level, EntityType<?> type) {
        LivingEntity cached = lootEntities.get(type);
        if (cached != null) return cached;
        Entity created;
        try {
            created = type.create(level);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(created instanceof LivingEntity living)) return null;
        lootEntities.put(type, living);
        return living;
    }

    /** Loot that doesn't fit is lost, like drops nobody picks up. */
    private void insertOutput(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = OUTPUT_START; i < UPGRADE_START && !rest.isEmpty(); i++) {
            ItemStack slot = items.getStackInSlot(i);
            if (!slot.isEmpty() && ItemHandlerHelper.canItemStacksStack(slot, rest)) {
                int move = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (move > 0) {
                    items.setStackInSlot(i, ItemHandlerHelper.copyStackWithSize(slot, slot.getCount() + move));
                    rest.shrink(move);
                }
            }
        }
        for (int i = OUTPUT_START; i < UPGRADE_START && !rest.isEmpty(); i++) {
            if (items.getStackInSlot(i).isEmpty()) {
                items.setStackInSlot(i, rest);
                rest = ItemStack.EMPTY;
            }
        }
    }

    private void pushOutputs(ServerLevel level) {
        for (Direction dir : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbour == null || neighbour instanceof ChaosSpawnerBlockEntity) continue;
            neighbour.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).ifPresent(target -> {
                for (int i = OUTPUT_START; i < UPGRADE_START; i++) {
                    ItemStack stack = items.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
                    if (rest.getCount() != stack.getCount()) items.setStackInSlot(i, rest);
                }
            });
        }
    }

    /** "Collect experience": hands all the stored points to the player. */
    public void giveExperience(Player player) {
        if (xp <= 0) return;
        player.giveExperiencePoints(xp);
        xp = 0;
        setChanged();
    }

    /** First soul's mob, for the spinning figure in the cage. */
    @Nullable
    public EntityType<?> displayType() {
        for (int i = SOUL_START; i < OUTPUT_START; i++) {
            EntityType<?> t = SoulCrystalItem.soulType(items.getStackInSlot(i));
            if (t != null) return t;
        }
        return null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.chaosspawner.spawner");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ChaosSpawnerMenu(id, inventory, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Progress", progress);
        tag.putInt("Xp", xp);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag saved = tag.getCompound("Items");
        saved.putInt("Size", SLOTS);
        items.deserializeNBT(saved);
        progress = tag.getInt("Progress");
        xp = tag.getInt("Xp");
    }

    // the client only needs the souls, for the figure in the cage
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        ItemStackHandler souls = new ItemStackHandler(SLOTS);
        for (int i = SOUL_START; i < OUTPUT_START; i++) souls.setStackInSlot(i, items.getStackInSlot(i).copy());
        tag.put("Items", souls.serializeNBT());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return automationCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        automationCap.invalidate();
    }
}
