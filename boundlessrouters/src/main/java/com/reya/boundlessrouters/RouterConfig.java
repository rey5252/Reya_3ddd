package com.reya.boundlessrouters;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/boundlessrouters-common.toml. Out of the box nothing is limited; a server can set limits here.
 */
public final class RouterConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue BASE_TICKS, SPEED_STEP, MIN_TICKS;
    public static final ForgeConfigSpec.IntValue BASE_RANGE, RANGE_STEP;
    public static final ForgeConfigSpec.IntValue MAX_RADIUS;
    public static final ForgeConfigSpec.BooleanValue CROSS_DIMENSION, LOAD_TARGETS, BREAK_ANY_TOOL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("router");
        BASE_TICKS = b.comment("Ticks between a router's runs without speed upgrades.")
                .defineInRange("baseTicks", 20, 1, 1200);
        SPEED_STEP = b.comment("Ticks each speed upgrade takes off.")
                .defineInRange("speedStep", 2, 0, 1200);
        MIN_TICKS = b.comment("The fastest a router runs, in ticks between runs (1 = every tick).")
                .defineInRange("minTicks", 1, 1, 1200);
        b.pop();
        b.push("modules");
        BASE_RANGE = b.comment("How far, in blocks, a sender looks along its direction for an inventory, and how far an extruder reaches,",
                        "without range upgrades.")
                .defineInRange("baseRange", 16, 1, 1024);
        RANGE_STEP = b.comment("Blocks each range upgrade adds.")
                .defineInRange("rangeStep", 4, 0, 1024);
        MAX_RADIUS = b.comment("The largest radius a vacuum module can be set to.")
                .defineInRange("maxVacuumRadius", 64, 1, 256);
        CROSS_DIMENSION = b.comment("Whether modules bound to a place reach it in another dimension.")
                .define("crossDimension", true);
        LOAD_TARGETS = b.comment("Whether modules bound to a place load its chunk when it isn't loaded (it stays loaded",
                        "a few seconds after each use).")
                .define("loadTargetChunks", true);
        BREAK_ANY_TOOL = b.comment("Whether the breaker breaks blocks that need a tool better than netherite (other mods' blocks).",
                        "Unbreakable blocks (bedrock, barriers) are never broken.")
                .define("breakAnything", true);
        b.pop();
        SPEC = b.build();
    }

    private RouterConfig() {
    }
}
