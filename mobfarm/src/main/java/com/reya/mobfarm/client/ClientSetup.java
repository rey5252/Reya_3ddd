package com.reya.mobfarm.client;

import com.reya.mobfarm.MobFarm;
import com.reya.mobfarm.ModRegistry;
import com.reya.mobfarm.lasso.LassoItem;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MobFarm.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModRegistry.MOB_FARM_MENU.get(), MobFarmScreen::new);
            // Lasso with a mob switches to the model with a charm hanging from the knot.
            ItemProperties.register(ModRegistry.LASSO.get(), new ResourceLocation(MobFarm.MOD_ID, "captured"),
                    (stack, level, entity, seed) -> LassoItem.hasMob(stack) ? 1.0F : 0.0F);
        });
    }

    /** The charm takes the caught mob's spawn egg colour (green for creepers, pink for pigs...). */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            SpawnEggItem egg = ForgeSpawnEggItem.fromEntityType(LassoItem.getType(stack));
            return egg != null ? egg.getColor(0) : 0xFFD27A;
        }, ModRegistry.LASSO.get());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.MOB_FARM.get(), MobFarmRenderer::new);
    }

    private ClientSetup() {
    }
}
