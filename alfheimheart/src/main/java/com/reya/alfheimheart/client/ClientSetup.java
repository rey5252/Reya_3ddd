package com.reya.alfheimheart.client;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.client.GreenhouseRenderer;
import com.reya.alfheimheart.greenhouse.client.GreenhouseScreen;
import com.reya.alfheimheart.portal.client.PortalRenderer;
import com.reya.alfheimheart.portal.client.PortalScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AlfheimHeart.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(AlfheimHeart.GREENHOUSE_MENU.get(), GreenhouseScreen::new);
            MenuScreens.register(AlfheimHeart.PORTAL_MENU.get(), PortalScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AlfheimHeart.GREENHOUSE_BE.get(), GreenhouseRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.PORTAL_BE.get(), PortalRenderer::new);
    }

    /** The greenhouse's petals and crystal move, so they are models of their own, not part of the block's. */
    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(GreenhouseRenderer.PETAL_MODEL);
        event.register(GreenhouseRenderer.CORE_MODEL);
    }

    private ClientSetup() {
    }
}
