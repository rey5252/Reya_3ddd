package com.reya.singularityfusion.client;

import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The client's side: the core's screen, and the renderers of the core (and its singularity) and the pylons. */
@Mod.EventBusSubscriber(modid = SingularityFusion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(SingularityFusion.FUSION_CORE_MENU.get(), FusionCoreScreen::new));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SingularityFusion.FUSION_CORE_BE.get(), FusionCoreRenderer::new);
        event.registerBlockEntityRenderer(SingularityFusion.GRAVITON_PYLON_BE.get(), GravitonPylonRenderer::new);
    }

    private ClientSetup() {
    }
}
