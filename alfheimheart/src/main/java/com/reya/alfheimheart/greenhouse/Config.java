package com.reya.alfheimheart.greenhouse;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    static {
        B.comment("Mana Greenhouse").push("greenhouse");
    }

    public static final ForgeConfigSpec.IntValue CYCLE_TICKS = B
            .comment("Ticks one growth cycle takes without growth upgrades (20 ticks = 1 second).")
            .defineInRange("cycleTicks", 100, 10, 24000);
    public static final ForgeConfigSpec.IntValue BASE_CAPACITY = B
            .comment("Mana the greenhouse holds without reservoir upgrades (a mana pool holds 1000000).")
            .defineInRange("baseCapacity", 200_000, 1_000, 1_000_000_000);
    public static final ForgeConfigSpec.IntValue MAX_UPGRADES = B
            .comment("Most upgrades of one kind that count, and most one upgrade slot takes.")
            .defineInRange("maxUpgradesPerKind", 8, 1, 64);
    public static final ForgeConfigSpec.DoubleValue SPEED_PER_UPGRADE = B
            .comment("Each growth upgrade makes cycles this much faster (0.25 = +25% cycles per second).")
            .defineInRange("speedPerUpgrade", 0.25D, 0.0D, 10.0D);
    public static final ForgeConfigSpec.IntValue CAPACITY_PER_UPGRADE = B
            .comment("Mana each reservoir upgrade adds to the store.")
            .defineInRange("capacityPerUpgrade", 350_000, 0, 100_000_000);
    public static final ForgeConfigSpec.DoubleValue LUCK_PER_UPGRADE = B
            .comment("Chance each fortune upgrade adds for a cycle to be lucky.")
            .defineInRange("luckPerUpgrade", 0.10D, 0.0D, 1.0D);
    public static final ForgeConfigSpec.DoubleValue LUCK_BONUS = B
            .comment("How much more mana a lucky cycle gives (0.5 = +50%).")
            .defineInRange("luckBonus", 0.5D, 0.0D, 100.0D);
    public static final ForgeConfigSpec.DoubleValue YIELD_PER_UPGRADE = B
            .comment("Each abundance upgrade raises the mana of every cycle by this much (0.15 = +15%).")
            .defineInRange("yieldPerUpgrade", 0.15D, 0.0D, 10.0D);
    public static final ForgeConfigSpec.DoubleValue HARMONY_PER_KIND = B
            .comment("Harmony: every different kind of flower after the first raises the mana by this much.")
            .defineInRange("harmonyPerKind", 0.05D, 0.0D, 10.0D);
    public static final ForgeConfigSpec.IntValue TRANSFER_RATE = B
            .comment("Most mana per tick the greenhouse hands to pools, spreaders and other mana receivers.")
            .defineInRange("transferPerTick", 5_000, 1, 1_000_000_000);
    public static final ForgeConfigSpec.IntValue CHARGE_RATE = B
            .comment("Most mana per tick it pours into the mana item in its charge slot.")
            .defineInRange("chargePerTick", 1_000, 1, 1_000_000_000);
    public static final ForgeConfigSpec.IntValue BIND_RANGE = B
            .comment("How far away a mana receiver bound with the Wand of the Forest may be.")
            .defineInRange("bindRange", 12, 1, 64);
    public static final ForgeConfigSpec.IntValue DEFAULT_FLOWER_RATE = B
            .comment("Mana per second of a generating flower that is not in flowerRates.")
            .defineInRange("defaultFlowerRate", 20, 0, 1_000_000);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLOWER_RATES = B
            .comment("Mana per second each flower makes inside the greenhouse, as \"namespace:item=rate\".",
                    "A floating flower makes as much as its ground version unless it has its own line.",
                    "Other flowers can be let in through the item tag alfheimheart:greenhouse_flowers.")
            .defineListAllowEmpty(List.of("flowerRates"), () -> List.of(
                    "botania:hydroangeas=10",
                    "botania:endoflame=24",
                    "botania:rosa_arcana=45",
                    "botania:thermalily=60",
                    "botania:munchdew=80",
                    "botania:gourmaryllis=100",
                    "botania:narslimmus=110",
                    "botania:kekimurus=130",
                    "botania:spectrolus=150",
                    "botania:entropinnyum=180",
                    "botania:rafflowsia=200",
                    "botania:dandelifeon=260",
                    "botania:shulk_me_not=400"),
                    o -> o instanceof String s && s.indexOf('=') > 0);

    static {
        B.pop();
    }

    public static final ForgeConfigSpec SPEC = B.build();

    private Config() {
    }
}
