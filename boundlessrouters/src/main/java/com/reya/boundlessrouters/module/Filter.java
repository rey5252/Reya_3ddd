package com.reya.boundlessrouters.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Which items a module works with: those its filter lists (a whitelist) or all but those (a blacklist), an item
 * matching a listed one by its kind, and as asked, also by damage and by NBT, or by any item tag they share, or
 * by the mod they come from.
 */
public final class Filter implements Predicate<ItemStack> {
    private final List<ItemStack> listed = new ArrayList<>();
    private final boolean blacklist, damage, nbt, tags, mod;

    public Filter(List<ItemStack> items, boolean blacklist, boolean damage, boolean nbt, boolean tags, boolean mod) {
        for (ItemStack item : items) {
            if (!item.isEmpty()) listed.add(item);
        }
        this.blacklist = blacklist;
        this.damage = damage;
        this.nbt = nbt;
        this.tags = tags;
        this.mod = mod;
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean found = false;
        for (ItemStack item : listed) {
            if (matches(item, stack)) {
                found = true;
                break;
            }
        }
        return found != blacklist;
    }

    private boolean matches(ItemStack listed, ItemStack stack) {
        if (mod) return namespace(listed).equals(namespace(stack));
        if (listed.getItem() != stack.getItem()) {
            return tags && listed.getTags().anyMatch(stack::is);
        }
        if (damage && listed.getDamageValue() != stack.getDamageValue()) return false;
        return !nbt || sameNbt(listed.getTag(), stack.getTag());
    }

    private static String namespace(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    /** Whether two items' NBT is the same, damage aside (the damage setting looks at that). */
    private static boolean sameNbt(@Nullable CompoundTag a, @Nullable CompoundTag b) {
        CompoundTag x = a == null ? new CompoundTag() : a.copy();
        CompoundTag y = b == null ? new CompoundTag() : b.copy();
        x.remove("Damage");
        y.remove("Damage");
        return x.equals(y);
    }
}
