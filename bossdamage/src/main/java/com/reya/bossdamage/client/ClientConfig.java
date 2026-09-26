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

    public static final ForgeConfigSpec.DoubleValue PANEL_X = BUILDER
            .comment("Horizontal centre of the panel, as a fraction of the screen width (0 = left, 0.5 = middle, 1 = right)")
            .defineInRange("panelX", 0.5D, 0.0D, 1.0D);

    public static final ForgeConfigSpec.DoubleValue PANEL_Y = BUILDER
            .comment("Top edge of the panel, as a fraction of the screen height (0 = top, 1 = bottom)")
            .defineInRange("panelY", 0.02D, 0.0D, 1.0D);

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

    public static double panelX() {
        try {
            return PANEL_X.get();
        } catch (IllegalStateException e) {
            return 0.5D;
        }
    }

    public static double panelY() {
        try {
            return PANEL_Y.get();
        } catch (IllegalStateException e) {
            return 0.02D;
        }
    }

    public static void setPanelPosition(double x, double y) {
        PANEL_X.set(Math.max(0.0D, Math.min(1.0D, x)));
        PANEL_Y.set(Math.max(0.0D, Math.min(1.0D, y)));
        PANEL_X.save();
        PANEL_Y.save();
    }

    private ClientConfig() {
    }
}
