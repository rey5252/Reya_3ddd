package com.reya.goldenquarry;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue BASE_RADIUS = B
            .comment("Blocks the dig area reaches out from the quarry on each side without range upgrades (4 = 9x9).")
            .defineInRange("baseRadius", 4, 1, 32);
    public static final ForgeConfigSpec.IntValue RADIUS_PER_UPGRADE = B
            .comment("Extra blocks on each side per range upgrade.")
            .defineInRange("radiusPerRangeUpgrade", 2, 1, 16);
    public static final ForgeConfigSpec.IntValue TICKS_PER_BLOCK = B
            .comment("Ticks to dig one block without speed upgrades.")
            .defineInRange("ticksPerBlock", 20, 1, 1200);
    public static final ForgeConfigSpec.IntValue ENERGY_CAPACITY = B
            .comment("Energy (FE) the quarry can hold.")
            .defineInRange("energyCapacity", 1_000_000, 1000, 1_000_000_000);
    public static final ForgeConfigSpec.IntValue ENERGY_PER_BLOCK = B
            .comment("Energy (FE) one dug block costs before upgrade surcharges.")
            .defineInRange("energyPerBlock", 100, 0, 1_000_000);
    public static final ForgeConfigSpec.IntValue PASSIVE_GENERATION = B
            .comment("Energy (FE) per tick the quarry makes on its own, so it works without any power mod. 0 = needs external power (any Forge Energy cable).")
            .defineInRange("passiveGeneration", 40, 0, 1_000_000);
    public static final ForgeConfigSpec.IntValue MAX_RECEIVE = B
            .comment("Energy (FE) per tick the quarry accepts from cables.")
            .defineInRange("maxReceive", 100_000, 1, 1_000_000_000);
    public static final ForgeConfigSpec.BooleanValue SKIP_BLOCK_ENTITIES = B
            .comment("Leave chests, spawners and other blocks with contents alone.")
            .define("skipBlockEntities", true);
    public static final ForgeConfigSpec SPEC = B.build();
}
