package com.reya.bossdamage.client;

import com.reya.bossdamage.BossDamage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {

    @Mod.EventBusSubscriber(modid = BossDamage.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "boss_damage", BossPanelOverlay::render);
        }

        private ModBus() {
        }
    }

    @Mod.EventBusSubscriber(modid = BossDamage.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        /** Our panel replaces the vanilla boss bars while it is showing. */
        @SubscribeEvent
        public static void hideVanillaBossBars(RenderGuiOverlayEvent.Pre event) {
            if (event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())
                    && !ClientBossData.current().isEmpty()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void renderDamageNumbers(RenderLevelStageEvent event) {
            DamageNumbers.render(event);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientBossData.clear();
            DamageNumbers.clear();
        }

        private ForgeBus() {
        }
    }

    private ClientEvents() {
    }
}
