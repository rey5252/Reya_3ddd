package com.reya.domgun;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.DoubleValue DAMAGE = B
            .comment("Damage of one bullet at close range (2 = one heart).")
            .defineInRange("damage", 6.0, 0.5, 1000.0);
    public static final ForgeConfigSpec.DoubleValue HEADSHOT_MULTIPLIER = B
            .comment("Damage multiplier for a headshot.")
            .defineInRange("headshotMultiplier", 2.0, 1.0, 20.0);
    public static final ForgeConfigSpec.DoubleValue RANGE = B
            .comment("Maximum bullet range in blocks.")
            .defineInRange("range", 96.0, 8.0, 256.0);
    public static final ForgeConfigSpec.DoubleValue FALLOFF_START = B
            .comment("Distance in blocks where the damage starts to fall off. At full range a bullet does half damage.")
            .defineInRange("falloffStart", 40.0, 0.0, 256.0);
    public static final ForgeConfigSpec.IntValue MAGAZINE_SIZE = B
            .comment("Rounds in one magazine.")
            .defineInRange("magazineSize", 30, 1, 500);
    public static final ForgeConfigSpec.IntValue FIRE_DELAY = B
            .comment("Ticks between two shots. 2 = 600 rounds per minute.")
            .defineInRange("fireDelayTicks", 2, 1, 40);
    public static final ForgeConfigSpec.IntValue RELOAD_TICKS = B
            .comment("Ticks a reload takes.")
            .defineInRange("reloadTicks", 50, 5, 400);
    public static final ForgeConfigSpec.DoubleValue HIP_SPREAD = B
            .comment("Bullet spread in degrees when firing from the hip. Sprinting and jumping make it worse.")
            .defineInRange("hipSpread", 2.5, 0.0, 30.0);
    public static final ForgeConfigSpec.DoubleValue ADS_SPREAD = B
            .comment("Bullet spread in degrees while aiming down sights (hold sneak).")
            .defineInRange("adsSpread", 0.35, 0.0, 30.0);
    public static final ForgeConfigSpec.DoubleValue RECOIL = B
            .comment("Camera kick in degrees per shot. Aiming down sights reduces it by 40%.")
            .defineInRange("recoil", 1.1, 0.0, 10.0);
    public static final ForgeConfigSpec SPEC = B.build();
}
