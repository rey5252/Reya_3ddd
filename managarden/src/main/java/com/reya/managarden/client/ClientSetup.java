package com.reya.managarden.client;

import com.reya.managarden.ManaGarden;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ManaGarden.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ManaGarden.GREENHOUSE_MENU.get(), GreenhouseScreen::new));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ManaGarden.GREENHOUSE_BE.get(), GreenhouseRenderer::new);
    }

    /** The petals and the crystal move, so they are models of their own, not part of the block's. */
    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(GreenhouseRenderer.PETAL_MODEL);
        event.register(GreenhouseRenderer.CORE_MODEL);
    }

    private ClientSetup() {
    }
}
