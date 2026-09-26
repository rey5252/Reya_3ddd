package com.reya.bossdamage.client;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.EnumValue<PanelStyle> STYLE = BUILDER
            .comment("Look of the mob/boss panel: NIGHT_SKY, ROYAL_GOLD, INFERNO, FROST, SAKURA, FOREST, COSMIC, OCEAN, PIRATE, STEEL, RAINBOW, MINIMAL.",
                    "Can also be switched in game with the \"Change panel style\" key (K by default).")
            .defineEnum("style", PanelStyle.NIGHT_SKY);

    public static final ForgeConfigSpec.BooleanValue DAMAGE_NUMBERS = BUILDER
            .comment("Show floating damage numbers when entities are hit")
            .define("damageNumbers", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static PanelStyle style() {
        try {
            return STYLE.get();
        } catch (IllegalStateException e) {
            return PanelStyle.NIGHT_SKY; // config not loaded yet
        }
    }

    public static boolean damageNumbers() {
        try {
            return DAMAGE_NUMBERS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private ClientConfig() {
    }
}
