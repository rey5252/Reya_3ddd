package com.reya.multibrewer;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue BREW_TICKS = B
            .comment("Ticks one brew takes without speed upgrades (vanilla brewing stand: 400).")
            .defineInRange("brewTicks", 400, 20, 72000);
    public static final ForgeConfigSpec.IntValue MAX_LEVEL = B
            .comment("Highest effect level glowstone can push a potion to (vanilla potions stop at II).")
            .defineInRange("maxLevel", 10, 1, 127);
    public static final ForgeConfigSpec.IntValue MAX_MINUTES = B
            .comment("Longest effect duration redstone and mixing can reach, in minutes.")
            .defineInRange("maxMinutes", 120, 1, 1440);
    public static final ForgeConfigSpec.IntValue FUEL_PER_BLAZE_POWDER = B
            .comment("Brews one blaze powder lasts.")
            .defineInRange("fuelPerBlazePowder", 20, 1, 1000);
    public static final ForgeConfigSpec SPEC = B.build();
}
