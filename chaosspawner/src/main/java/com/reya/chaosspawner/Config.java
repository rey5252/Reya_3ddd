package com.reya.chaosspawner;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue CYCLE_TICKS = B
            .comment("Ticks between two rounds without speed upgrades.")
            .defineInRange("cycleTicks", 200, 10, 72000);
    public static final ForgeConfigSpec.IntValue MAX_KILLS = B
            .comment("Most mobs one round can count, over all soul slots together.")
            .defineInRange("maxKillsPerRound", 512, 1, 100000);
    public static final ForgeConfigSpec.IntValue MAX_XP = B
            .comment("Experience points the spawner can hold before it stops storing more.")
            .defineInRange("maxStoredExperience", 1_000_000, 100, 1_000_000_000);
    public static final ForgeConfigSpec SPEC = B.build();
}
