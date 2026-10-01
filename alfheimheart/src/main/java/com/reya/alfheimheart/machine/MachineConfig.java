package com.reya.alfheimheart.machine;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/alfheimheart-machines.toml: each machine's mana store, how fast it charges a craft, how long a craft takes. */
public final class MachineConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    /** A machine's numbers. */
    public static final class Numbers {
        public final ForgeConfigSpec.IntValue capacity, chargeRate, drawPerTick, craftTicks;

        Numbers(String name, String what, int capacity, int chargeRate, int drawPerTick, int craftTicks) {
            B.comment(what).push(name);
            this.capacity = B.comment("Mana it holds (a mana pool holds 1000000).")
                    .defineInRange("capacity", capacity, 1_000, 1_000_000_000);
            this.chargeRate = B.comment("Most mana a tick puts into a craft (20 ticks = 1 second).")
                    .defineInRange("chargeRate", chargeRate, 1, 1_000_000_000);
            this.drawPerTick = B.comment("Most mana a tick it takes from the mana pools beside it (0: none).")
                    .defineInRange("drawPerTick", drawPerTick, 0, 1_000_000_000);
            this.craftTicks = B.comment("The shortest time a craft takes, in ticks, however fast the mana comes.")
                    .defineInRange("craftTicks", craftTicks, 1, 12_000);
            B.pop();
        }
    }

    public static final Numbers ALTAR = new Numbers("runeAltar", "Runic Altar", 100_000, 600, 2_000, 30);
    public static final Numbers PLATE = new Numbers("terraPlate", "Terrestrial Plate", 1_000_000, 5_000, 10_000, 60);
    public static final Numbers INFUSER = new Numbers("manaInfuser", "Mana Infuser", 200_000, 2_000, 4_000, 20);

    public static final ForgeConfigSpec.IntValue INFUSER_BATCH;

    static {
        B.push("manaInfuser");
        INFUSER_BATCH = B.comment("Most items of a kind the Mana Infuser infuses at once (the mana is per item).")
                .defineInRange("batch", 8, 1, 64);
        B.pop();
    }

    public static final Numbers APOTHECARY = new Numbers("petalApothecary", "Petal Apothecary", 50_000, 250, 1_000, 40);
    public static final ForgeConfigSpec.IntValue APOTHECARY_MANA;

    static {
        B.push("petalApothecary");
        APOTHECARY_MANA = B.comment("Mana one flower takes (Botania's apothecary takes none, but water).")
                .defineInRange("manaPerCraft", 1_000, 0, 1_000_000);
        B.pop();
    }

    public static final Numbers FARM = new Numbers("petalFarm", "Petal Farm", 100_000, 500, 2_000, 100);
    public static final ForgeConfigSpec.IntValue FARM_MANA, FARM_PER_FLOWERS, FARM_MOST;

    static {
        B.push("petalFarm");
        FARM_MANA = B.comment("Mana one petal takes.").defineInRange("manaPerPetal", 100, 0, 1_000_000);
        FARM_PER_FLOWERS = B.comment("A kind of flower gives one petal a cycle, and one more for every this many flowers of it.")
                .defineInRange("flowersPerPetal", 16, 1, 64);
        FARM_MOST = B.comment("Most petals a kind of flower gives a cycle (before bone meal doubles them).")
                .defineInRange("mostPerKind", 4, 1, 64);
        B.pop();
    }

    public static final ForgeConfigSpec.IntValue DAISY_CAPACITY, DAISY_DRAW, DAISY_BOOST, DAISY_BATCH;

    static {
        B.comment("Pure Daisy").push("pureDaisy");
        DAISY_CAPACITY = B.comment("Mana it holds (it needs none, but mana makes it work twice as fast).")
                .defineInRange("capacity", 50_000, 1_000, 1_000_000_000);
        DAISY_DRAW = B.comment("Most mana a tick it takes from the mana pools beside it (0: none).")
                .defineInRange("drawPerTick", 1_000, 0, 1_000_000_000);
        DAISY_BOOST = B.comment("Mana a tick that makes it work twice as fast (0: never hurried).")
                .defineInRange("boostPerTick", 10, 0, 1_000_000);
        DAISY_BATCH = B.comment("Blocks it purifies at once (the Pure Daisy has eight round it).")
                .defineInRange("batch", 8, 1, 64);
        B.pop();
    }

    public static final ForgeConfigSpec SPEC = B.build();

    private MachineConfig() {
    }
}
