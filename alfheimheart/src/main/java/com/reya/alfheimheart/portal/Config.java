package com.reya.alfheimheart.portal;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    static {
        B.comment("Elven Portal").push("portal");
    }

    public static final ForgeConfigSpec.IntValue MANA_PER_TRADE = B
            .comment("Mana one trade costs (Botania's own gateway takes 500 a trade).")
            .defineInRange("manaPerTrade", 500, 0, 1_000_000);
    public static final ForgeConfigSpec.IntValue CAPACITY = B
            .comment("Mana the portal holds (a mana pool holds 1000000).")
            .defineInRange("capacity", 100_000, 1_000, 1_000_000_000);
    public static final ForgeConfigSpec.IntValue TRADE_TICKS = B
            .comment("Ticks between two trades (20 ticks = 1 second).")
            .defineInRange("tradeTicks", 5, 1, 1200);
    public static final ForgeConfigSpec.BooleanValue DRAW_FROM_POOLS = B
            .comment("Whether the portal takes mana from the mana pools beside it (as Botania's gateway does through its pylons).")
            .define("drawFromPools", true);
    public static final ForgeConfigSpec.IntValue DRAW_PER_TICK = B
            .comment("Most mana per tick the portal takes from the pools beside it.")
            .defineInRange("drawPerTick", 5_000, 1, 1_000_000_000);
    public static final ForgeConfigSpec.BooleanValue ACCEPT_THROWN_ITEMS = B
            .comment("Whether items thrown on the portal go in, as they would into Botania's gateway.")
            .define("acceptThrownItems", true);

    static {
        B.pop();
    }

    public static final ForgeConfigSpec SPEC = B.build();

    private Config() {
    }
}
