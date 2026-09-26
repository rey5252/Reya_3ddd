package com.overenchant;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue MAX_LEVEL = B
            .comment("Maximum enchantment level the upgrader can raise to.")
            .defineInRange("maxLevel", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue XP_LEVELS_PER_UPGRADE = B
            .comment("XP levels spent for every +1 level on one enchantment (0 = free). Creative mode is always free.")
            .defineInRange("xpLevelsPerUpgrade", 1, 0, 1000);
    public static final ForgeConfigSpec SPEC = B.build();
}
