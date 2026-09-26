package com.reya.chaosspawner.client;

import com.reya.chaosspawner.ChaosSpawner;
import com.reya.chaosspawner.SoulCrystalItem;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ChaosSpawner.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ChaosSpawner.SPAWNER_MENU.get(), ChaosSpawnerScreen::new);
            // a filled crystal switches to the model with a glowing soul inside
            ItemProperties.register(ChaosSpawner.SOUL_CRYSTAL.get(), new ResourceLocation(ChaosSpawner.MODID, "filled"),
                    (stack, level, entity, seed) -> SoulCrystalItem.isSoul(stack) ? 1.0F : 0.0F);
        });
    }

    /** The soul inside takes the mob's spawn egg colours. */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            if (tint == 0) return -1;
            EntityType<?> type = SoulCrystalItem.soulType(stack);
            SpawnEggItem egg = type == null ? null : ForgeSpawnEggItem.fromEntityType(type);
            if (egg == null) return 0x7FE8F2;
            return egg.getColor(tint == 1 ? 0 : 1);
        }, ChaosSpawner.SOUL_CRYSTAL.get());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ChaosSpawner.SPAWNER_BE.get(), ChaosSpawnerRenderer::new);
    }

    private ClientSetup() {
    }
}
