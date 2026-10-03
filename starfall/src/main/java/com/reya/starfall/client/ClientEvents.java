package com.reya.starfall.client;

import com.reya.starfall.Skill;
import com.reya.starfall.Starfall;
import com.reya.starfall.network.Net;
import com.reya.starfall.network.SelectSkillPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Starfall.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ClientStrikes.tick();
        SkillMenu.tick();
        if (SkillMenu.binding()) {
            // the key being bound must not also fire its old meaning
            for (var key : Keys.SKILLS) while (key.consumeClick()) ;
            while (Keys.MENU.consumeClick()) ;
            while (Keys.FILM.consumeClick()) ;
            return;
        }
        for (int i = 0; i < Keys.SKILLS.length; i++) {
            while (Keys.SKILLS[i].consumeClick()) select(mc, Skill.byIndex(i));
        }
        while (Keys.MENU.consumeClick()) {
            if (mc.screen == null) SkillMenu.toggle();
        }
        while (Keys.FILM.consumeClick()) {
            if (Film.active()) {
                Film.stop();
                mc.player.displayClientMessage(Component.translatable("message.starfall.film_skipped"), true);
            } else {
                boolean on = !ClientConfig.films();
                ClientConfig.setFilms(on);
                mc.player.displayClientMessage(Component.translatable(on ? "message.starfall.films_on" : "message.starfall.films_off"), true);
            }
        }
    }

    private static void select(Minecraft mc, Skill skill) {
        if (mc.player == null) return;
        if (SkillMenu.remote(mc.player).isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("message.starfall.hold_remote"), true);
            return;
        }
        Net.CHANNEL.sendToServer(new SelectSkillPacket(skill.ordinal()));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            ClientStrikes.render(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            SkillMenu.render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float amp = ClientStrikes.shake((float) event.getPartialTick());
        if (amp <= 0.0F) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + amp * (Mth.sin(t * 1.9F) * 0.6F + Mth.sin(t * 4.3F) * 0.4F));
        event.setPitch(event.getPitch() + amp * (Mth.cos(t * 2.3F) * 0.6F + Mth.sin(t * 5.1F) * 0.4F));
        event.setRoll(event.getRoll() + amp * 0.5F * Mth.sin(t * 3.7F));
    }

    /** While the menu is open, clicks pick skills instead of hitting or using things. */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!SkillMenu.isOpen()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isAttack() || event.isUseItem()) SkillMenu.click(event.isAttack());
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS && SkillMenu.binding()) SkillMenu.key(event.getKey(), event.getScanCode());
    }

    /** You stand still while the film plays and while the menu floats around you. */
    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!Film.active() && !SkillMenu.isOpen()) return;
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStrikes.clear();
        SkillMenu.close();
    }

    private ClientEvents() {
    }
}
