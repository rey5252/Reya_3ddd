package com.reya.cursedseed;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue SEED_DROP_CHANCE = BUILDER
            .comment("Chance for a wither skeleton killed by a player to drop a Cursed Seed (0.025 = 2.5%)")
            .defineInRange("seedDropChance", 0.025D, 0.0D, 1.0D);

    public static final ForgeConfigSpec.DoubleValue SEED_DROP_LOOTING_BONUS = BUILDER
            .comment("Extra drop chance per level of Looting")
            .defineInRange("seedDropLootingBonus", 0.01D, 0.0D, 1.0D);

    public static final ForgeConfigSpec.DoubleValue SPAWN_CHANCE = BUILDER
            .comment("Chance per random tick that a Cursed Earth block spawns a monster on top of itself")
            .defineInRange("spawnChance", 0.25D, 0.0D, 1.0D);

    public static final ForgeConfigSpec.IntValue MAX_NEARBY_MONSTERS = BUILDER
            .comment("Cursed Earth stops spawning when this many monsters are within 8 blocks")
            .defineInRange("maxNearbyMonsters", 8, 0, 256);

    public static final ForgeConfigSpec.IntValue PLAYER_RANGE = BUILDER
            .comment("Cursed Earth only spawns monsters when a player is within this many blocks")
            .defineInRange("playerRange", 48, 1, 256);

    public static final ForgeConfigSpec.BooleanValue SPREAD = BUILDER
            .comment("Whether Cursed Earth slowly spreads to nearby grass and dirt")
            .define("spread", true);

    public static final ForgeConfigSpec.DoubleValue HEALTH_BONUS = BUILDER
            .comment("Extra max health for monsters spawned on Cursed Earth (0.5 = +50%)")
            .defineInRange("healthBonus", 0.5D, 0.0D, 10.0D);

    public static final ForgeConfigSpec.DoubleValue DAMAGE_BONUS = BUILDER
            .comment("Extra attack damage for monsters spawned on Cursed Earth (0.5 = +50%)")
            .defineInRange("damageBonus", 0.5D, 0.0D, 10.0D);

    public static final ForgeConfigSpec.DoubleValue SPEED_BONUS = BUILDER
            .comment("Extra movement speed for monsters spawned on Cursed Earth (0.15 = +15%)")
            .defineInRange("speedBonus", 0.15D, 0.0D, 2.0D);

    public static final ForgeConfigSpec.DoubleValue ARMOR_BONUS = BUILDER
            .comment("Extra armor points for monsters spawned on Cursed Earth")
            .defineInRange("armorBonus", 4.0D, 0.0D, 30.0D);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
