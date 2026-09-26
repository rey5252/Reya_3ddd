package com.reya.bossdamage;

import com.reya.bossdamage.client.ClientConfig;
import com.reya.bossdamage.client.ClientEvents;
import com.reya.bossdamage.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(BossDamage.MOD_ID)
public class BossDamage {
    public static final String MOD_ID = "bossdamage";

    public BossDamage() {
        ModNetwork.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientEvents::registerConfigScreen);
        MinecraftForge.EVENT_BUS.register(new BossDamageTracker());
    }
}
