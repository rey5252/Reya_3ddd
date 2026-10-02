package com.reya.boundlessrouters;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/boundlessrouters-common.toml: how fast routers run, how far modules reach (and how far each range upgrade
 * takes them), and a few rules a server may want otherwise.
 */
public final class RouterConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue BASE_TICKS, SPEED_STEP, MIN_TICKS;
    public static final ForgeConfigSpec.IntValue REACH, RANGE_1, RANGE_2, RANGE_3, SCAN_LIMIT;
    public static final ForgeConfigSpec.IntValue MAX_RADIUS;
    public static final ForgeConfigSpec.BooleanValue INFINITE_OTHER_DIMENSIONS, LOAD_TARGETS, BREAK_ANY_TOOL;

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
        b.push("range");
        REACH = b.comment("How far, in blocks, a router's modules reach without a range upgrade: the places they are bound to,",
                        "the player a player module is bound to, how far a sender looks and an extruder builds, a vacuum's radius.")
                .defineInRange("reach", 8, 1, 4096);
        RANGE_1 = b.comment("How far modules reach with a Range Upgrade I (range upgrades don't add up: the best one counts).")
                .defineInRange("rangeUpgrade1", 16, 1, 4096);
        RANGE_2 = b.comment("With a Range Upgrade II.")
                .defineInRange("rangeUpgrade2", 32, 1, 4096);
        RANGE_3 = b.comment("With a Range Upgrade III.")
                .defineInRange("rangeUpgrade3", 64, 1, 4096);
        SCAN_LIMIT = b.comment("The farthest a sender looks along its direction and an extruder builds, even with an infinite range upgrade.")
                .defineInRange("scanLimit", 64, 1, 4096);
        INFINITE_OTHER_DIMENSIONS = b.comment("Whether a router with an Infinite Range Upgrade reaches places and players in other dimensions.")
                .define("infiniteReachesOtherDimensions", true);
        b.pop();
        b.push("modules");
        MAX_RADIUS = b.comment("The largest radius a vacuum module can be set to (it never reaches past its router's range).")
                .defineInRange("maxVacuumRadius", 64, 1, 256);
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
