package com.reya.starfall.client;

import com.reya.starfall.Starfall;
import com.reya.starfall.StellarRemoteItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Starfall.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(Starfall.STELLAR_REMOTE.get(),
                new ResourceLocation(Starfall.MODID, "skill"),
                // the button under the cover lights in the selected weapon's colour
                (stack, level, entity, seed) -> StellarRemoteItem.skill(stack).ordinal() * 0.5F));
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
        event.registerAboveAll("film", (gui, graphics, partial, width, height) -> Film.render(graphics, partial, width, height));
        event.registerAboveAll("flash", (gui, graphics, partial, width, height) -> {
            int flash = ClientStrikes.flash(partial);
            if (((flash >>> 24) & 0xFF) > 0) graphics.fill(0, 0, width, height, flash);
        });
    }

    private ClientSetup() {
    }
}
