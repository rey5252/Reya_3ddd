package com.reya.boundlessrouters.client;

import com.reya.boundlessrouters.BoundlessRouters;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The client's side of things: the screens of the router's and the modules' menus. */
@Mod.EventBusSubscriber(modid = BoundlessRouters.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(BoundlessRouters.ROUTER_MENU.get(), RouterScreen::new);
            MenuScreens.register(BoundlessRouters.MODULE_MENU.get(), ModuleScreen::new);
        });
    }

    private ClientSetup() {
    }
}
