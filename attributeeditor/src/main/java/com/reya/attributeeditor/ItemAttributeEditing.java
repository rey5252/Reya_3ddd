package com.reya.attributeeditor;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.Multimap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Edits the attribute modifiers stored on an item stack. Shared by /itemattr and the Attribute Table. */
public final class ItemAttributeEditing {
    public static final String NBT_KEY = "AttributeModifiers";

    /** The value the tooltip shows for this attribute in this slot. */
    public static double value(ItemStack stack, Attribute attribute, EquipmentSlot slot) {
        double value = attribute == Attributes.ATTACK_DAMAGE ? 1.0D : attribute == Attributes.ATTACK_SPEED ? 4.0D : 0.0D;
        for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(attribute)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                value += modifier.getAmount();
            }
        }
        return value;
    }

    /** Replaces every modifier of {@code attribute} in {@code slot} with one that shows {@code value}. */
    public static void set(ItemStack stack, Attribute attribute, EquipmentSlot slot, double value) {
        copyDefaultsToNbt(stack);
        removeFromNbt(stack, attribute, slot);
        stack.addAttributeModifier(attribute, AttributeValues.modifierFor(attribute, slot, value), slot);
    }

    public static int remove(ItemStack stack, Attribute attribute, @Nullable EquipmentSlot slot) {
        copyDefaultsToNbt(stack);
        return removeFromNbt(stack, attribute, slot);
    }

    public static void reset(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(NBT_KEY);
            if (tag.isEmpty()) stack.setTag(null);
        }
    }

    /** Returns true if the item is unbreakable afterwards. */
    public static boolean toggleUnbreakable(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        boolean now = !tag.getBoolean("Unbreakable");
        if (now) {
            tag.putBoolean("Unbreakable", true);
            stack.setDamageValue(0);
        } else {
            tag.remove("Unbreakable");
        }
        return now;
    }

    /**
     * An item with an AttributeModifiers tag loses all of its default attributes, so before the
     * first edit copy the current ones in; otherwise setting damage would wipe attack speed.
     */
    private static void copyDefaultsToNbt(ItemStack stack) {
        if (stack.getTag() != null && stack.getTag().contains(NBT_KEY, Tag.TAG_LIST)) return;

        Map<EquipmentSlot, Multimap<Attribute, AttributeModifier>> current = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            current.put(slot, stack.getAttributeModifiers(slot));
        }
        stack.getOrCreateTag().put(NBT_KEY, new ListTag());
        current.forEach((slot, modifiers) ->
                modifiers.forEach((attribute, modifier) -> stack.addAttributeModifier(attribute, modifier, slot)));
    }

    private static int removeFromNbt(ItemStack stack, Attribute attribute, @Nullable EquipmentSlot slot) {
        if (stack.getTag() == null) return 0;
        ListTag list = stack.getTag().getList(NBT_KEY, Tag.TAG_COMPOUND);
        String id = AttributeValues.key(attribute);
        int before = list.size();
        list.removeIf(t -> t instanceof CompoundTag entry
                && id.equals(entry.getString("AttributeName"))
                && (slot == null || slot.getName().equals(entry.getString("Slot"))));
        return before - list.size();
    }

    private ItemAttributeEditing() {
    }
}
