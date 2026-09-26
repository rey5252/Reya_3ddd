package com.reya.advancedenchanting;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.DoubleValue COST_MULTIPLIER = B
            .comment("Multiplies every experience point cost in the advanced table.")
            .defineInRange("costMultiplier", 1.0D, 0.0D, 100.0D);
    public static final ForgeConfigSpec.BooleanValue CURSES_ANYTIME = B
            .comment("Allow applying curses at any time instead of only at midnight under a full moon.")
            .define("cursesAnytime", false);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_ENCHANTMENTS = B
            .comment("Enchantments the advanced table never offers, e.g. [\"minecraft:mending\"].")
            .defineListAllowEmpty("blacklistedEnchantments", List.of(), o -> o instanceof String);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_ITEMS = B
            .comment("Items that can't be put into the advanced table, e.g. [\"minecraft:elytra\"].")
            .defineListAllowEmpty("blacklistedItems", List.of(), o -> o instanceof String);
    public static final ForgeConfigSpec SPEC = B.build();
}
