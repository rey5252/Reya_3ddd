package com.reya.attributeeditor;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AttributeEditor.MOD_ID)
public class AttributeEditor {
    public static final String MOD_ID = "attributeeditor";

    public AttributeEditor() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.BLOCKS.register(modBus);
        ModRegistry.ITEMS.register(modBus);
        ModRegistry.MENUS.register(modBus);
        modBus.addListener(this::onSetup);
        modBus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onItemAttributes);
    }

    private void onSetup(FMLCommonSetupEvent event) {
        ItemAttributeConfig.load();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.ATTRIBUTE_TABLE_ITEM);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ItemAttrCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemAttributeConfig.apply(event);
    }
}
