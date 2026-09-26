package com.overenchant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;

public final class EnchantUpgrade {
    private EnchantUpgrade() {
    }

    /** The item's enchantment list (stored enchantments for books), or null. */
    public static ListTag getList(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        String key = stack.getItem() instanceof EnchantedBookItem ? "StoredEnchantments" : "Enchantments";
        return tag.contains(key, Tag.TAG_LIST) ? tag.getList(key, Tag.TAG_COMPOUND) : null;
    }
}
