package com.reya.bossdamage;

import com.reya.bossdamage.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(BossDamage.MOD_ID)
public class BossDamage {
    public static final String MOD_ID = "bossdamage";

    public BossDamage() {
        ModNetwork.register();
        MinecraftForge.EVENT_BUS.register(new BossDamageTracker());
    }
}
