package com.reya.mobfarm;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MobFarm.MOD_ID)
public class MobFarm {
    public static final String MOD_ID = "mobfarm";

    public MobFarm() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.BLOCKS.register(modBus);
        ModRegistry.ITEMS.register(modBus);
        ModRegistry.BLOCK_ENTITIES.register(modBus);
        ModRegistry.MENUS.register(modBus);
        ModRegistry.TABS.register(modBus);

        MinecraftForge.EVENT_BUS.register(new LassoEvents());
    }
}
