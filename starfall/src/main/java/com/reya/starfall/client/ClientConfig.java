package com.reya.starfall.client;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue FILMS = B
            .comment("Play the film when you fire the remote. J skips a film, or turns films off and on.")
            .define("films", true);
    public static final ForgeConfigSpec.DoubleValue FLASH = B
            .comment("Strength of the full-screen impact flashes (photosensitivity). 0 turns them off;",
                    "below 0.5 the strobing frames are replaced by one soft fade.")
            .defineInRange("flashStrength", 1.0D, 0.0D, 1.0D);
    public static final ForgeConfigSpec.BooleanValue SHAKE = B
            .comment("Shake the camera when something lands near you.")
            .define("screenShake", true);

    public static final ForgeConfigSpec SPEC = B.build();

    static boolean films() {
        try {
            return FILMS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    static void setFilms(boolean on) {
        try {
            FILMS.set(on);
            FILMS.save();
        } catch (IllegalStateException ignored) {
            // config not loaded yet
        }
    }

    static float flash() {
        try {
            return FLASH.get().floatValue();
        } catch (IllegalStateException e) {
            return 1.0F;
        }
    }

    static boolean shake() {
        try {
            return SHAKE.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    private ClientConfig() {
    }
}
