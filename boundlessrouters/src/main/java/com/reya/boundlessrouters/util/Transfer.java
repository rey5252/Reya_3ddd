package com.reya.boundlessrouters.util;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Moving items from one inventory to another. */
public final class Transfer {
    /**
     * Moves up to max items that pass the filter from one inventory into another, stacking them onto what is
     * there first, and returns how many moved. Nothing is taken that the other side can't hold.
     */
    public static int move(IItemHandler from, IItemHandler to, int max, Predicate<ItemStack> filter) {
        int moved = 0;
        for (int i = 0; i < from.getSlots() && moved < max; i++) {
            ItemStack there = from.getStackInSlot(i);
            if (there.isEmpty() || !filter.test(there)) continue;
            ItemStack offered = from.extractItem(i, max - moved, true);
            if (offered.isEmpty()) continue;
            ItemStack refused = ItemHandlerHelper.insertItemStacked(to, offered, true);
            int fits = offered.getCount() - refused.getCount();
            if (fits <= 0) continue;
            ItemStack taken = from.extractItem(i, fits, false);
            ItemStack left = ItemHandlerHelper.insertItemStacked(to, taken, false);
            moved += taken.getCount() - left.getCount();
            if (!left.isEmpty()) from.insertItem(i, left, false);
        }
        return moved;
    }

    /** Puts a stack into an inventory as far as it goes; returns what didn't fit. */
    public static ItemStack insert(IItemHandler to, ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(to, stack, false);
    }

    /** Whether an inventory takes at least one of a stack. */
    public static boolean accepts(IItemHandler to, ItemStack stack) {
        return !stack.isEmpty() && ItemHandlerHelper.insertItemStacked(to, stack, true).getCount() < stack.getCount();
    }

    private Transfer() {
    }
}
