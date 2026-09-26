package com.reya.cursedseed;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CursedSeed.MOD_ID)
public class CursedSeed {
    public static final String MOD_ID = "cursedseed";

    public CursedSeed() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModRegistry.BLOCKS.register(modBus);
        ModRegistry.ITEMS.register(modBus);
        modBus.addListener(this::addCreative);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(new ModEvents());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModRegistry.CURSED_EARTH_ITEM);
            event.accept(ModRegistry.CURSED_SEED);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModRegistry.CURSED_SEED);
        }
    }
}
