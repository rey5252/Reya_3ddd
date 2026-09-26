package com.reya.bossdamage.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reya.bossdamage.BossDamage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    public static final KeyMapping CHANGE_STYLE = new KeyMapping("key.bossdamage.change_style",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.bossdamage");

    @Mod.EventBusSubscriber(modid = BossDamage.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(CHANGE_STYLE);
        }

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

        /** K opens the style menu. */
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (CHANGE_STYLE.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new StyleScreen(null));
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

    /** "Config" button in the Mods list opens the style menu too. */
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new StyleScreen(parent)));
    }

    private ClientEvents() {
    }
}
