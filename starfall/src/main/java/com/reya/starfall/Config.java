package com.reya.starfall;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    static {
        B.push("railgun");
    }
    public static final ForgeConfigSpec.IntValue RAILGUN_RADIUS = B
            .comment("SS-01: radius in blocks of the beam that cuts the whole column of the world away.")
            .defineInRange("radius", 200, 4, 512);
    public static final ForgeConfigSpec.BooleanValue RAILGUN_BEDROCK = B
            .comment("SS-01: also cut through bedrock, all the way down to the void.")
            .define("breakBedrock", true);

    static {
        B.pop().push("gungnir");
    }
    public static final ForgeConfigSpec.IntValue GUNGNIR_RADIUS = B
            .comment("SS-03: radius in blocks the shock ring planes flat into a molten crater.")
            .defineInRange("radius", 200, 4, 512);
    public static final ForgeConfigSpec.IntValue GUNGNIR_DEPTH = B
            .comment("SS-03: how deep the crater bowl is at its centre.")
            .defineInRange("depth", 14, 0, 64);
    public static final ForgeConfigSpec.DoubleValue GUNGNIR_SHOCK = B
            .comment("SS-03: past the crater the shock still throws and hurts, out to this many crater radii.")
            .defineInRange("shockReach", 2.0D, 1.0D, 4.0D);

    static {
        B.pop().push("sevenStars");
    }
    public static final ForgeConfigSpec.IntValue SEVEN_SPAN = B
            .comment("SS-04: how many blocks across the projected Big Dipper is.")
            .defineInRange("span", 1440, 64, 4096);
    public static final ForgeConfigSpec.DoubleValue SEVEN_CRATER_SCALE = B
            .comment("SS-04: multiplier for crater sizes (each crater is as wide as its star is bright).")
            .defineInRange("craterScale", 1.0D, 0.1D, 4.0D);
    public static final ForgeConfigSpec.IntValue SEVEN_TRENCH_WIDTH = B
            .comment("SS-04: width in blocks of the burning trenches along the constellation lines.")
            .defineInRange("trenchWidth", 5, 1, 32);
    public static final ForgeConfigSpec.IntValue SEVEN_TRENCH_DEPTH = B
            .comment("SS-04: depth in blocks of the burning trenches.")
            .defineInRange("trenchDepth", 6, 1, 32);

    static {
        B.pop().push("general");
    }
    public static final ForgeConfigSpec.IntValue COOLDOWN = B
            .comment("Seconds before the remote can be fired again.")
            .defineInRange("cooldownSeconds", 10, 0, 3600);
    public static final ForgeConfigSpec.IntValue RANGE = B
            .comment("How far (blocks) the remote can mark a target. Only loaded chunks can be marked.")
            .defineInRange("targetRange", 512, 16, 2048);
    public static final ForgeConfigSpec.IntValue CARVE_MS = B
            .comment("Milliseconds per server tick spent reshaping the land. Higher = faster craters, lower TPS.")
            .defineInRange("carveMillisPerTick", 20, 1, 45);

    static {
        B.pop();
    }

    public static final ForgeConfigSpec SPEC = B.build();

    private Config() {
    }
}
