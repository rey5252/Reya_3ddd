package com.enchantsiphon;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue COST = B
            .comment("XP levels charged for moving ONE enchantment into a book (0 = free, creative is always free).")
            .defineInRange("levelCostPerEnchant", 2, 0, 100);
    public static final ForgeConfigSpec.BooleanValue ALLOW_CURSES = B
            .comment("Allow moving curses (Curse of Vanishing / Binding) too.")
            .define("allowCurses", true);
    public static final ForgeConfigSpec SPEC = B.build();
}
