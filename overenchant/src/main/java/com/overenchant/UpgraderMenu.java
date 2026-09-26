package com.overenchant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One slot for the enchanted item on the left of the ornate panel, player inventory centred below.
 * The screen sends the wanted level increases; {@link #applyUpgrade} checks and pays for them.
 */
public class UpgraderMenu extends AbstractContainerMenu {
    public static final int MAX_ROWS = 32;
    public static final int WIDTH = 256;
    public static final int HEIGHT = 236;
    public static final int ITEM_SLOT_X = 44;
    public static final int ITEM_SLOT_Y = 24;
    public static final int INV_X = 47;
    public static final int INV_Y = 154;

    private final Container container = new SimpleContainer(1);
    private final ContainerLevelAccess access;

    public UpgraderMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public UpgraderMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(OverEnchant.UPGRADER_MENU.get(), id);
        this.access = access;
        addSlot(new Slot(container, 0, ITEM_SLOT_X, ITEM_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                ListTag list = EnchantUpgrade.getList(stack);
                return list != null && !list.isEmpty();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
        }
    }

    public void applyUpgrade(ServerPlayer player, int[] deltas) {
        if (!stillValid(player)) return;
        ItemStack stack = container.getItem(0);
        ListTag list = EnchantUpgrade.getList(stack);
        if (list == null || list.isEmpty()) return;

        int max = Config.MAX_LEVEL.get();
        int costPer = Config.XP_LEVELS_PER_UPGRADE.get();
        int n = Math.min(Math.min(list.size(), deltas.length), MAX_ROWS);
        int[] add = new int[n];
        long total = 0L;
        for (int i = 0; i < n; i++) {
            long cur = list.getCompound(i).getInt("lvl");
            long room = (long) max - cur;
            long d = Math.max(0L, Math.min(Math.max(0, deltas[i]), room));
            add[i] = (int) d;
            total += d;
        }
        if (total == 0L) return;

        long cost = total * costPer;
        boolean free = costPer == 0 || player.getAbilities().instabuild;
        if (!free && player.experienceLevel < cost) return;
        for (int i = 0; i < n; i++) {
            if (add[i] <= 0) continue;
            CompoundTag ench = list.getCompound(i);
            ench.putInt("lvl", ench.getInt("lvl") + add[i]);
        }
        if (!free) player.giveExperienceLevels(-(int) cost);
        container.setChanged();
        broadcastChanges();
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            if (index == 0) {
                if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
            } else if (!slots.get(0).mayPlace(stack) || !moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, container));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, OverEnchant.ENCHANTMENT_UPGRADER.get());
    }
}
