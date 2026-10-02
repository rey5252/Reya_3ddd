package com.reya.singularityfusion;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/singularityfusion-common.toml. */
public final class FusionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue CAPACITY, MAX_INPUT, PYLON_RADIUS, MAX_PYLONS;
    public static final ForgeConfigSpec.DoubleValue TIME_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue NEEDS_FOUNDATION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("core");
        CAPACITY = b.comment("How much energy (FE, which is RF and Draconic Evolution's OP) a fusion core holds. The black hole over it",
                        "grows with how full it is: none while empty, half-grown at half, full size when full.")
                .defineInRange("capacity", 2_000_000_000, 1_000, Integer.MAX_VALUE);
        MAX_INPUT = b.comment("The most energy a core takes in a tick (from all sides together).")
                .defineInRange("maxInputPerTick", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
        PYLON_RADIUS = b.comment("How far from the core (in blocks, sideways) its graviton pylons may stand; they may be up to 2 blocks below",
                        "it and 4 above.")
                .defineInRange("pylonRadius", 6, 2, 12);
        MAX_PYLONS = b.comment("The most pylons a core works with (and so the most ingredients a recipe may have).")
                .defineInRange("maxPylons", 12, 1, 24);
        NEEDS_FOUNDATION = b.comment("Whether a core must stand on a 3x3 of void casing to work.")
                .define("needsFoundation", true);
        TIME_MULTIPLIER = b.comment("Every fusion takes this many times its recipe's time.")
                .defineInRange("timeMultiplier", 1.0D, 0.05D, 20.0D);
        b.pop();
        SPEC = b.build();
    }

    private FusionConfig() {
    }
}
