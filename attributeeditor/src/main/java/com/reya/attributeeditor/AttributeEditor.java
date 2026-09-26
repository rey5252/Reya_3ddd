package com.reya.attributeeditor;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AttributeEditor.MOD_ID)
public class AttributeEditor {
    public static final String MOD_ID = "attributeeditor";

    public AttributeEditor() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onItemAttributes);
    }

    private void onSetup(FMLCommonSetupEvent event) {
        ItemAttributeConfig.load();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ItemAttrCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemAttributeConfig.apply(event);
    }
}
