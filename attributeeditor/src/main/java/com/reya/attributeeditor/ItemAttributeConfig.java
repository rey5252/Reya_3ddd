package com.reya.attributeeditor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Per-item attribute overrides from {@code config/attributeeditor.json}.
 * Items that were edited with /itemattr keep their own values and ignore this file.
 */
public final class ItemAttributeConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_CONFIG = """
            {
              "_help": "Key = item id (minecraft:diamond_sword) or item tag (#forge:tools). attack_damage and attack_speed are the numbers shown in the tooltip; other attributes (armor, armor_toughness, max_health, movement_speed, knockback_resistance, luck, forge:entity_reach, forge:block_reach...) are added to the player. Optional slot: mainhand, offhand, head, chest, legs, feet. Put your rules in items, then run /itemattr reload.",
              "_example": {
                "minecraft:wooden_sword": { "attack_damage": 6, "attack_speed": 1.2 },
                "minecraft:diamond_chestplate": { "armor": 10, "max_health": 4 },
                "minecraft:stick": { "attack_damage": 3, "movement_speed": 0.02, "slot": "offhand" }
              },
              "items": {
              }
            }
            """;

    private record Entry(Attribute attribute, double value) {
    }

    private record Rule(@Nullable Item item, @Nullable TagKey<Item> tag, @Nullable EquipmentSlot slot, List<Entry> entries) {
        boolean matches(ItemStack stack) {
            return item != null ? stack.is(item) : stack.is(tag);
        }
    }

    private static volatile List<Rule> rules = List.of();

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(AttributeEditor.MOD_ID + ".json");
    }

    /** (Re)reads the config file. Returns the number of rules loaded, or -1 on error. */
    public static int load() {
        Path path = path();
        try {
            if (!Files.exists(path)) {
                Files.writeString(path, DEFAULT_CONFIG, StandardCharsets.UTF_8);
            }
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = GSON.fromJson(reader, JsonObject.class);
            }
            List<Rule> loaded = new ArrayList<>();
            if (root != null && root.has("items")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("items").entrySet()) {
                    Rule rule = parseRule(e.getKey(), e.getValue().getAsJsonObject());
                    if (rule != null) loaded.add(rule);
                }
            }
            rules = List.copyOf(loaded);
            LOGGER.info("Loaded {} item attribute rules from {}", loaded.size(), path);
            return loaded.size();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load {}", path, e);
            return -1;
        }
    }

    @Nullable
    private static Rule parseRule(String key, JsonObject json) {
        Item item = null;
        TagKey<Item> tag = null;
        if (key.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(key.substring(1));
            if (id == null) {
                LOGGER.warn("Bad item tag '{}'", key);
                return null;
            }
            tag = TagKey.create(Registries.ITEM, id);
        } else {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                LOGGER.warn("Unknown item '{}'", key);
                return null;
            }
            item = ForgeRegistries.ITEMS.getValue(id);
        }

        EquipmentSlot slot = null;
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (e.getKey().equals("slot")) {
                slot = AttributeValues.parseSlot(e.getValue().getAsString());
                if (slot == null) LOGGER.warn("Unknown slot '{}' for '{}'", e.getValue().getAsString(), key);
                continue;
            }
            Attribute attribute = AttributeValues.parse(e.getKey());
            if (attribute == null) {
                LOGGER.warn("Unknown attribute '{}' for '{}'", e.getKey(), key);
                continue;
            }
            entries.add(new Entry(attribute, e.getValue().getAsDouble()));
        }
        return new Rule(item, tag, slot, List.copyOf(entries));
    }

    public static void apply(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.hasTag() && stack.getTag().contains("AttributeModifiers", Tag.TAG_LIST)) return;

        for (Rule rule : rules) {
            if (!rule.matches(stack)) continue;
            EquipmentSlot slot = rule.slot() != null ? rule.slot() : AttributeValues.defaultSlot(stack);
            if (event.getSlotType() != slot) continue;
            for (Entry entry : rule.entries()) {
                event.removeAttribute(entry.attribute());
                event.addModifier(entry.attribute(), AttributeValues.modifierFor(entry.attribute(), slot, entry.value()));
            }
        }
    }

    private ItemAttributeConfig() {
    }
}
