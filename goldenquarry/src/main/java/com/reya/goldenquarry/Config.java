package com.reya.goldenquarry;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue TICKS_PER_OPERATION = B
            .comment("Ticks one operation takes (one ore, or the whole input with the stack upgrade).")
            .defineInRange("ticksPerOperation", 20, 1, 1200);
    public static final ForgeConfigSpec.IntValue ENERGY_CAPACITY = B
            .comment("Energy (FE) the converter can hold.")
            .defineInRange("energyCapacity", 1_000_000, 1000, 1_000_000_000);
    public static final ForgeConfigSpec.IntValue ENERGY_PER_ORE = B
            .comment("Energy (FE) one ore costs before the fortune and autosmelt surcharges.")
            .defineInRange("energyPerOre", 200, 0, 1_000_000);
    public static final ForgeConfigSpec.IntValue PASSIVE_GENERATION = B
            .comment("Energy (FE) per tick the converter makes on its own, so it works without any power mod. 0 = needs external power (any Forge Energy cable).")
            .defineInRange("passiveGeneration", 20, 0, 1_000_000);
    public static final ForgeConfigSpec.IntValue MAX_RECEIVE = B
            .comment("Energy (FE) per tick the converter accepts from cables.")
            .defineInRange("maxReceive", 100_000, 1, 1_000_000_000);
    public static final ForgeConfigSpec SPEC = B.build();
}
