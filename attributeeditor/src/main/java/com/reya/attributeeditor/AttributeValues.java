package com.reya.attributeeditor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Converts between "the number shown in the tooltip" and the modifier stored on the item. */
public final class AttributeValues {
    // Same UUIDs vanilla weapons use, so the tooltip shows the value in green ("6 Attack Damage").
    public static final UUID BASE_ATTACK_DAMAGE = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    public static final UUID BASE_ATTACK_SPEED = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    // Player base values the tooltip adds on top of the modifier.
    private static final double PLAYER_ATTACK_DAMAGE = 1.0D;
    private static final double PLAYER_ATTACK_SPEED = 4.0D;

    /** Modifier that makes the tooltip show exactly {@code value}. */
    public static AttributeModifier modifierFor(Attribute attribute, EquipmentSlot slot, double value) {
        if (attribute == Attributes.ATTACK_DAMAGE) {
            return new AttributeModifier(BASE_ATTACK_DAMAGE, "Weapon modifier", value - PLAYER_ATTACK_DAMAGE,
                    AttributeModifier.Operation.ADDITION);
        }
        if (attribute == Attributes.ATTACK_SPEED) {
            return new AttributeModifier(BASE_ATTACK_SPEED, "Weapon modifier", value - PLAYER_ATTACK_SPEED,
                    AttributeModifier.Operation.ADDITION);
        }
        UUID id = UUID.nameUUIDFromBytes((AttributeEditor.MOD_ID + ":" + key(attribute) + "/" + slot.getName())
                .getBytes(StandardCharsets.UTF_8));
        return new AttributeModifier(id, "Attribute editor", value, AttributeModifier.Operation.ADDITION);
    }

    /** The number the tooltip shows for a modifier. */
    public static double displayed(Attribute attribute, AttributeModifier modifier) {
        if (modifier.getId().equals(BASE_ATTACK_DAMAGE)) return modifier.getAmount() + PLAYER_ATTACK_DAMAGE;
        if (modifier.getId().equals(BASE_ATTACK_SPEED)) return modifier.getAmount() + PLAYER_ATTACK_SPEED;
        return modifier.getAmount();
    }

    /** Armor goes to its armor slot, shields to the offhand, everything else to the main hand. */
    public static EquipmentSlot defaultSlot(ItemStack stack) {
        if (stack.getItem() instanceof Equipable equipable) {
            return equipable.getEquipmentSlot();
        }
        return EquipmentSlot.MAINHAND;
    }

    public static String key(Attribute attribute) {
        ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        return id == null ? "unknown" : id.toString();
    }

    /** Accepts full ids ("minecraft:generic.armor", "forge:entity_reach") and short ones ("armor", "attack_damage"). */
    @Nullable
    public static Attribute parse(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id != null && ForgeRegistries.ATTRIBUTES.containsKey(id)) return ForgeRegistries.ATTRIBUTES.getValue(id);
        for (String prefix : new String[]{"minecraft:generic.", "minecraft:player.", "minecraft:zombie.", "minecraft:horse.", "forge:"}) {
            ResourceLocation guess = ResourceLocation.tryParse(prefix + name);
            if (guess != null && ForgeRegistries.ATTRIBUTES.containsKey(guess)) return ForgeRegistries.ATTRIBUTES.getValue(guess);
        }
        return null;
    }

    @Nullable
    public static EquipmentSlot parseSlot(String name) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getName().equalsIgnoreCase(name)) return slot;
        }
        return null;
    }

    private AttributeValues() {
    }
}
