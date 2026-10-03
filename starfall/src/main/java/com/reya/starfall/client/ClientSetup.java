package com.reya.starfall.client;

import com.reya.starfall.Starfall;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Starfall.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    /** The remote's moving parts, drawn by {@link RemoteRenderer}. */
    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation part : RemoteRenderer.PARTS) event.register(part);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(RemoteRenderer::tint, Starfall.STELLAR_REMOTE.get());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(Keys.RAILGUN);
        event.register(Keys.GUNGNIR);
        event.register(Keys.SEVEN_STARS);
        event.register(Keys.MENU);
        event.register(Keys.FILM);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("strike_glow", (gui, graphics, partial, width, height) -> WorldGlow.compose());
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "remote", (gui, graphics, partial, width, height) -> RemoteHud.render(graphics, partial, width, height));
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "evac", (gui, graphics, partial, width, height) -> Evac.render(graphics, partial, width, height));
        event.registerAboveAll("film", (gui, graphics, partial, width, height) -> Film.render(graphics, partial, width, height));
        event.registerAboveAll("flash", (gui, graphics, partial, width, height) -> {
            int flash = ClientStrikes.flash(partial);
            if (((flash >>> 24) & 0xFF) > 0) graphics.fill(0, 0, width, height, flash);
        });
    }

    private ClientSetup() {
    }
}
