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

    public static final ForgeConfigSpec.IntValue FILM_QUALITY = B
            .comment("How smooth the films look: 2 draws them at twice the screen's resolution and shrinks them down,",
                    "which takes the stair-steps off every edge; 1 draws them straight, for weak graphics cards.")
            .defineInRange("filmQuality", 2, 1, 2);

    public static final ForgeConfigSpec.BooleanValue GLOW = B
            .comment("Bloom: a soft glow around bright light in the films and around the strikes in the world.",
                    "Turn it off for weak graphics cards.")
            .define("glow", true);

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

    static int filmQuality() {
        try {
            return FILM_QUALITY.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    static boolean glow() {
        try {
            return GLOW.get();
        } catch (IllegalStateException e) {
            return true;
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
