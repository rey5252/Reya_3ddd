package com.reya.alfheimheart.client;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.client.GreenhouseRenderer;
import com.reya.alfheimheart.greenhouse.client.GreenhouseScreen;
import com.reya.alfheimheart.machine.altar.client.RuneAltarRenderer;
import com.reya.alfheimheart.machine.altar.client.RuneAltarScreen;
import com.reya.alfheimheart.machine.apothecary.client.PetalApothecaryRenderer;
import com.reya.alfheimheart.machine.apothecary.client.PetalApothecaryScreen;
import com.reya.alfheimheart.machine.daisy.client.PureDaisyRenderer;
import com.reya.alfheimheart.machine.daisy.client.PureDaisyScreen;
import com.reya.alfheimheart.machine.farm.client.PetalFarmRenderer;
import com.reya.alfheimheart.machine.farm.client.PetalFarmScreen;
import com.reya.alfheimheart.machine.field.client.CropFieldRenderer;
import com.reya.alfheimheart.machine.field.client.CropFieldScreen;
import com.reya.alfheimheart.machine.orechid.client.OrechidMineRenderer;
import com.reya.alfheimheart.machine.orechid.client.OrechidMineScreen;
import com.reya.alfheimheart.machine.infuser.client.ManaInfuserRenderer;
import com.reya.alfheimheart.machine.infuser.client.ManaInfuserScreen;
import com.reya.alfheimheart.machine.plate.client.TerraPlateRenderer;
import com.reya.alfheimheart.machine.plate.client.TerraPlateScreen;
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
            MenuScreens.register(AlfheimHeart.RUNE_ALTAR_MENU.get(), RuneAltarScreen::new);
            MenuScreens.register(AlfheimHeart.TERRA_PLATE_MENU.get(), TerraPlateScreen::new);
            MenuScreens.register(AlfheimHeart.MANA_INFUSER_MENU.get(), ManaInfuserScreen::new);
            MenuScreens.register(AlfheimHeart.PURE_DAISY_MENU.get(), PureDaisyScreen::new);
            MenuScreens.register(AlfheimHeart.PETAL_APOTHECARY_MENU.get(), PetalApothecaryScreen::new);
            MenuScreens.register(AlfheimHeart.PETAL_FARM_MENU.get(), PetalFarmScreen::new);
            MenuScreens.register(AlfheimHeart.ORECHID_MINE_MENU.get(), OrechidMineScreen::new);
            MenuScreens.register(AlfheimHeart.CROP_FIELD_MENU.get(), CropFieldScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AlfheimHeart.GREENHOUSE_BE.get(), GreenhouseRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.PORTAL_BE.get(), PortalRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.RUNE_ALTAR_BE.get(), RuneAltarRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.TERRA_PLATE_BE.get(), TerraPlateRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.MANA_INFUSER_BE.get(), ManaInfuserRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.PURE_DAISY_BE.get(), PureDaisyRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.PETAL_APOTHECARY_BE.get(), PetalApothecaryRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.PETAL_FARM_BE.get(), PetalFarmRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.ORECHID_MINE_BE.get(), OrechidMineRenderer::new);
        event.registerBlockEntityRenderer(AlfheimHeart.CROP_FIELD_BE.get(), CropFieldRenderer::new);
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
