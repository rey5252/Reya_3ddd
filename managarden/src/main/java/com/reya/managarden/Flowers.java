package com.reya.managarden;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Which flowers grow in the greenhouse and how much mana each makes there. Botania's generating
 * flowers (and their floating versions) are let in through the item tag managarden:greenhouse_flowers;
 * their rates come from the config, a floating flower taking its ground version's.
 */
public final class Flowers {
    public static final TagKey<Item> GREENHOUSE_FLOWERS = TagKey.create(Registries.ITEM, new ResourceLocation(ManaGarden.MODID, "greenhouse_flowers"));
    private static final String FLOATING = "floating_";

    /** The colours Botania gives the flowers' mana (their HUD and sparkles), by ground flower. */
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("botania:hydroangeas", 0x532FE0),
            Map.entry("botania:endoflame", 0x785000),
            Map.entry("botania:thermalily", 0xD03C00),
            Map.entry("botania:rosa_arcana", 0xFF8EF8),
            Map.entry("botania:munchdew", 0x79C42F),
            Map.entry("botania:entropinnyum", 0xCB0000),
            Map.entry("botania:kekimurus", 0x935D28),
            Map.entry("botania:gourmaryllis", 0xD3D604),
            Map.entry("botania:narslimmus", 0x71C373),
            Map.entry("botania:spectrolus", 0xFFFFFF),
            Map.entry("botania:dandelifeon", 0x9C0A7E),
            Map.entry("botania:rafflowsia", 0x502C76),
            Map.entry("botania:shulk_me_not", 0x815598));

    /** The config's rate lines as last read, with what they say (read again when the config changes). */
    private record Parsed(List<? extends String> from, Map<ResourceLocation, Integer> rates) {
    }

    @Nullable
    private static volatile Parsed parsed;

    public static boolean isFlower(ItemStack stack) {
        return !stack.isEmpty() && stack.is(GREENHOUSE_FLOWERS);
    }

    /** Mana per second the flower makes in the greenhouse. */
    public static int rate(ItemStack stack) {
        if (!isFlower(stack)) return 0;
        Map<ResourceLocation, Integer> rates = rates();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer own = rates.get(id);
        if (own != null) return own;
        Integer ground = rates.get(groundId(id));
        return ground != null ? ground : Config.DEFAULT_FLOWER_RATE.get();
    }

    /** The flower the item stands for, floating or not: its ground version's id. */
    public static ResourceLocation groundId(ResourceLocation id) {
        return id.getPath().startsWith(FLOATING) ? new ResourceLocation(id.getNamespace(), id.getPath().substring(FLOATING.length())) : id;
    }

    /** Kind of flower for the harmony bonus: a floating flower is the same kind as its ground version. */
    public static ResourceLocation kind(ItemStack stack) {
        return groundId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Colour of the flower's mana, cyan for flowers Botania gives none. */
    public static int color(ItemStack stack) {
        if (stack.isEmpty()) return 0x4CD7FF;
        return COLORS.getOrDefault(kind(stack).toString(), 0x4CD7FF);
    }

    /**
     * The block drawn inside the greenhouse for the flower: the ground version (a floating flower's
     * island would not fit), or nothing if the item places no block.
     */
    public static Block plantBlock(ItemStack stack) {
        ResourceLocation ground = kind(stack);
        Block block = BuiltInRegistries.BLOCK.get(ground);
        if (block != Blocks.AIR) return block;
        return Block.byItem(stack.getItem());
    }

    private static Map<ResourceLocation, Integer> rates() {
        List<? extends String> lines = Config.FLOWER_RATES.get();
        Parsed p = parsed;
        if (p == null || p.from() != lines) {
            Map<ResourceLocation, Integer> rates = new HashMap<>();
            for (String line : lines) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                ResourceLocation id = ResourceLocation.tryParse(line.substring(0, eq).trim());
                if (id == null) continue;
                try {
                    rates.put(id, Math.max(0, Integer.parseInt(line.substring(eq + 1).trim())));
                } catch (NumberFormatException e) {
                    ManaGarden.LOGGER.warn("Mana Garden: bad flower rate '{}' in the config", line);
                }
            }
            p = new Parsed(lines, Map.copyOf(rates));
            parsed = p;
        }
        return p.rates();
    }

    private Flowers() {
    }
}
