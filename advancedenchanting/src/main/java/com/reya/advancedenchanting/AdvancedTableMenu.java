package com.reya.advancedenchanting;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * One slot for the item near the top left of the ornate panel, player inventory centred below.
 * The screen sends the wanted level of every listed enchantment; {@link #enchant} checks and pays.
 */
public class AdvancedTableMenu extends AbstractContainerMenu {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 236;
    public static final int ITEM_SLOT_X = 44;
    public static final int ITEM_SLOT_Y = 24;
    public static final int INV_X = 47;
    public static final int INV_Y = 154;

    private final SimpleContainer container = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;

    public AdvancedTableMenu(int id, Inventory inv) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public AdvancedTableMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(AdvancedEnchanting.TABLE_MENU.get(), id);
        this.access = access;
        addSlot(new Slot(container, 0, ITEM_SLOT_X, ITEM_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EnchantRules.canUse(stack);
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

    public ItemStack item() {
        return container.getItem(0);
    }

    public void enchant(ServerPlayer player, Map<Enchantment, Integer> wanted) {
        if (!stillValid(player)) return;
        ItemStack stack = container.getItem(0);
        if (!EnchantRules.canUse(stack)) return;
        Map<Enchantment, Integer> current = EnchantmentHelper.getEnchantments(stack);
        Map<Enchantment, Integer> options = EnchantRules.options(stack);

        Map<Enchantment, Integer> targets = new LinkedHashMap<>(current);
        for (Map.Entry<Enchantment, Integer> w : wanted.entrySet()) {
            Enchantment e = w.getKey();
            if (!options.containsKey(e)) return;
            int cur = current.getOrDefault(e, 0);
            int lvl = w.getValue();
            if (lvl < 0 || lvl > EnchantRules.maxLevel(e, cur)) return;
            targets.put(e, lvl);
        }
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> t : targets.entrySet()) {
            int cur = current.getOrDefault(t.getKey(), 0);
            if (t.getValue() == cur) continue;
            changed = true;
            if (!EnchantRules.available(t.getKey(), cur, targets, player.level())) return;
        }
        if (!changed) return;

        long cost = EnchantRules.totalCost(current, targets);
        boolean free = player.getAbilities().instabuild;
        long have = EnchantRules.points(player);
        if (!free && have < cost) return;

        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        targets.forEach((e, l) -> {
            if (l > 0) result.put(e, l);
        });
        ItemStack out = stack;
        if (stack.is(Items.BOOK) && !result.isEmpty()) {
            out = new ItemStack(Items.ENCHANTED_BOOK);
        } else if (stack.is(Items.ENCHANTED_BOOK) && result.isEmpty()) {
            out = new ItemStack(Items.BOOK);
        }
        if (out.is(Items.ENCHANTED_BOOK)) out.removeTagKey("StoredEnchantments");
        EnchantmentHelper.setEnchantments(result, out);
        container.setItem(0, out);

        if (!free && cost > 0) {
            long left = have - cost;
            player.experienceLevel = 0;
            player.experienceProgress = 0.0F;
            player.totalExperience = 0;
            player.giveExperiencePoints((int) Math.min(Integer.MAX_VALUE, left));
        }
        broadcastChanges();
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F,
                level.random.nextFloat() * 0.1F + 0.9F));
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
            } else {
                if (!slots.get(0).mayPlace(stack) || slots.get(0).hasItem()) return ItemStack.EMPTY;
                ItemStack one = stack.split(1);
                slots.get(0).set(one);
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
        return stillValid(access, player, AdvancedEnchanting.TABLE.get());
    }
}
